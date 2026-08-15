package com.fenixcore.optibienestar360.common.storage;

import com.fenixcore.optibienestar360.common.service.StorageService;
import com.fenixcore.optibienestar360.common.storage.dto.AttachedFileDto;
import com.fenixcore.optibienestar360.common.storage.dto.ChecklistDto;
import com.fenixcore.optibienestar360.common.storage.dto.FileUrlDto;
import com.fenixcore.optibienestar360.common.storage.dto.PublishResultDto;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Generic, reusable file-attachment mechanism — spec {@code .ai/specs/08-storage-r2.md}
 * §4. Any domain attaches files to one of its records by calling this
 * service with a fixed {@code ownerTable} (never taken from the request) —
 * no bespoke entity/service/controller needed per domain.
 *
 * <p>Authorization is the caller's job: this service has no notion of
 * permissions beyond the "own vs. shared" filter (spec §4.2) applied via the
 * {@code hasViewAll}/{@code currentUserUuid} parameters — each domain's
 * controller decides which {@code *_VIEW_ALL}/{@code *_VIEW_OWN} authority
 * it requires before calling in.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttachedFileService {

    private final AttachedFileRepository attachedFileRepository;
    private final UserRepository userRepository;
    private final ObjectProvider<StorageService> storageProvider;
    private final FileValidationService fileValidationService;
    private final PresignedUrlPolicy presignedUrlPolicy;
    private final FileDownloadLinkService downloadLinkService;

    /** Visibility an unpublished file returns to — see {@link #unpublish} javadoc. */
    private static final FileVisibility UNPUBLISHED_VISIBILITY = FileVisibility.INTERNAL;

    // ─── Upload ─────────────────────────────────────────────────────────────

    @Transactional
    public AttachedFileDto upload(String ownerTable, UUID ownerUuid, FileVisibility visibility,
                                   String category, MultipartFile file, UUID uploaderUuid) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file.upload.empty");
        }
        fileValidationService.validate(visibility, file);

        StorageService storage = requireStorage();
        String key = StorageKeyBuilder.build(visibility, ownerTable, ownerUuid, file.getOriginalFilename());
        try {
            storage.upload(bucketFor(visibility, storage), key, file.getInputStream(), file.getSize(), file.getContentType());
        } catch (IOException ex) {
            log.error("Failed to read upload stream for {}/{}", ownerTable, ownerUuid, ex);
            throw new IllegalArgumentException("file.upload.failed");
        }

        AttachedFile entity = new AttachedFile();
        entity.setOwnerTable(ownerTable);
        entity.setOwnerUuid(ownerUuid);
        entity.setVisibility(visibility);
        entity.setCategory(category);
        entity.setFileKey(key);
        entity.setFileName(StorageKeyBuilder.safeName(file.getOriginalFilename()));
        entity.setMimeType(file.getContentType());
        entity.setSizeBytes(file.getSize());
        entity.setUploadedAt(Instant.now());
        entity.setUploadedBy(userOrNull(uploaderUuid));
        attachedFileRepository.save(entity);
        return toDto(entity);
    }

    // ─── List / checklist (spec §4.1) ───────────────────────────────────────

    /** Plain metadata listing, filtered to what {@code currentUserUuid} may see (spec §4.2) unless {@code hasViewAll}. */
    public List<AttachedFileDto> list(String ownerTable, UUID ownerUuid, UUID currentUserUuid, boolean hasViewAll) {
        return attachedFileRepository.findByOwnerTableAndOwnerUuidAndActiveTrue(ownerTable, ownerUuid).stream()
                .filter(f -> hasViewAll || visibleTo(f, currentUserUuid))
                .map(this::toDto)
                .toList();
    }

    /**
     * Checklist against {@code requiredCategories} — e.g. "does this member
     * have ID_FRONT + ID_BACK + MEMBER_PHOTO uploaded?". Completeness reflects
     * every uploaded file regardless of the own/shared filter: whether a
     * required category is present is a review-workflow signal, not
     * something that should hide behind per-uploader visibility — callers
     * needing this are already reviewers holding {@code *_VIEW_ALL}.
     */
    public ChecklistDto listWithChecklist(String ownerTable, UUID ownerUuid, Set<String> requiredCategories) {
        List<AttachedFile> all = attachedFileRepository.findByOwnerTableAndOwnerUuidAndActiveTrue(ownerTable, ownerUuid);
        Map<String, AttachedFile> byCategory = all.stream()
                .collect(Collectors.toMap(AttachedFile::getCategory, f -> f, (a, b) -> a.getUploadedAt().isAfter(b.getUploadedAt()) ? a : b));

        List<ChecklistDto.Item> items = requiredCategories.stream()
                .map(category -> {
                    AttachedFile f = byCategory.get(category);
                    return f == null
                            ? ChecklistDto.Item.missing(category)
                            : ChecklistDto.Item.uploaded(category, f.getUuid(), f.getFileName(), f.getUploadedAt());
                })
                .toList();

        // Extra categories (e.g. OTHER) show up too, without affecting `complete`.
        List<ChecklistDto.Item> extra = byCategory.keySet().stream()
                .filter(c -> !requiredCategories.contains(c))
                .map(c -> {
                    AttachedFile f = byCategory.get(c);
                    return ChecklistDto.Item.uploaded(c, f.getUuid(), f.getFileName(), f.getUploadedAt());
                })
                .toList();

        boolean complete = items.stream().allMatch(i -> i.status() == ChecklistDto.Status.UPLOADED);
        List<ChecklistDto.Item> combined = new java.util.ArrayList<>(items);
        combined.addAll(extra);
        return new ChecklistDto(combined, complete);
    }

    private static boolean visibleTo(AttachedFile file, UUID currentUserUuid) {
        if (file.isShared()) return true;
        User uploader = file.getUploadedBy();
        return uploader != null && currentUserUuid != null && currentUserUuid.equals(uploader.getUuid());
    }

    // ─── On-demand presigned URL ────────────────────────────────────────────

    public FileUrlDto presignedUrl(UUID fileUuid, Duration requestedTtl) {
        AttachedFile file = findManaged(fileUuid);
        StorageService storage = requireStorage();
        Duration ttl = presignedUrlPolicy.clamp(requestedTtl);
        String url = storage.generatePresignedUrl(bucketFor(file.getVisibility(), storage), file.getFileKey(), ttl);
        return new FileUrlDto(url, Instant.now().plus(ttl), ttl.toSeconds(),
                file.getFileName(), file.getMimeType(), file.getSizeBytes());
    }

    // ─── Share (own -> visible to *_VIEW_OWN too, spec §4.2) ────────────────

    @Transactional
    public AttachedFileDto setShared(UUID fileUuid, boolean shared) {
        AttachedFile file = findManaged(fileUuid);
        file.setShared(shared);
        return toDto(file);
    }

    // ─── Delete ─────────────────────────────────────────────────────────────

    @Transactional
    public void delete(UUID fileUuid) {
        AttachedFile file = findManaged(fileUuid);
        StorageService storage = requireStorage();
        storage.delete(bucketFor(file.getVisibility(), storage), file.getFileKey());
        downloadLinkService.revokeAllFor(file.getOwnerTable(), file.getOwnerUuid(), file.getFileKey());
        attachedFileRepository.delete(file);
    }

    // ─── Publish / unpublish (spec §4.3) ────────────────────────────────────

    /**
     * Moves a file from its current (private) visibility to public access.
     * {@code permanent=true} copies the object to the public bucket
     * server-side (R2/S3 has no symlinks — a copy is the closest
     * equivalent) and flips {@code visibility=PUBLIC} in place.
     * {@code permanent=false} copies nothing — it mints a
     * {@code file_download_links} row valid until {@code temporaryExpiresAt}
     * instead, reusing the same short-TTL-presigned-URL-per-hit mechanism
     * (spec §6).
     */
    @Transactional
    public PublishResultDto publish(UUID fileUuid, boolean permanent, Instant temporaryExpiresAt, UUID actorUuid) {
        AttachedFile file = findManaged(fileUuid);

        if (!permanent) {
            var link = downloadLinkService.create(file.getVisibility(), file.getOwnerTable(), file.getOwnerUuid(),
                    file.getFileKey(), temporaryExpiresAt, null, actorUuid);
            return PublishResultDto.temporary(link);
        }

        if (file.getVisibility() == FileVisibility.PUBLIC) {
            throw new IllegalArgumentException("file.publish.already_public");
        }

        StorageService storage = requireStorage();
        String sourceBucket = bucketFor(file.getVisibility(), storage);
        String destKey = StorageKeyBuilder.build(FileVisibility.PUBLIC, file.getOwnerTable(), file.getOwnerUuid(), file.getFileName());
        storage.copy(sourceBucket, file.getFileKey(), storage.getPublicBucket(), destKey);

        // Old private object stays until unpublish/delete removes it — see StorageService.copy javadoc:
        // never leave the row referencing a half-completed operation.
        file.setVisibility(FileVisibility.PUBLIC);
        file.setFileKey(destKey);
        file.setPublishedAt(Instant.now());
        file.setPublishedBy(userOrNull(actorUuid));
        return PublishResultDto.permanent(destKey);
    }

    /**
     * Reverses {@link #publish}. For a permanently-published file: copies the
     * object back to the private bucket under {@link #UNPUBLISHED_VISIBILITY}
     * and only then deletes the public object — the file is never lost, it's
     * ready to be re-published without re-uploading. For a temporarily-shared
     * file (no object was ever moved): revokes its download links instead.
     */
    @Transactional
    public void unpublish(UUID fileUuid) {
        AttachedFile file = findManaged(fileUuid);

        if (file.getVisibility() != FileVisibility.PUBLIC) {
            downloadLinkService.revokeAllFor(file.getOwnerTable(), file.getOwnerUuid(), file.getFileKey());
            return;
        }

        StorageService storage = requireStorage();
        String oldPublicKey = file.getFileKey();
        String privateKey = StorageKeyBuilder.build(UNPUBLISHED_VISIBILITY, file.getOwnerTable(), file.getOwnerUuid(), file.getFileName());
        storage.copy(storage.getPublicBucket(), oldPublicKey, storage.getBucket(), privateKey);

        file.setVisibility(UNPUBLISHED_VISIBILITY);
        file.setFileKey(privateKey);
        file.setPublishedAt(null);
        file.setPublishedBy(null);

        storage.delete(storage.getPublicBucket(), oldPublicKey);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private AttachedFile findManaged(UUID uuid) {
        return attachedFileRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("file.not_found"));
    }

    private User userOrNull(UUID uuid) {
        return uuid != null ? userRepository.findByUuid(uuid).orElse(null) : null;
    }

    private static String bucketFor(FileVisibility visibility, StorageService storage) {
        return visibility == FileVisibility.PUBLIC ? storage.getPublicBucket() : storage.getBucket();
    }

    private StorageService requireStorage() {
        StorageService storage = storageProvider.getIfAvailable();
        if (storage == null) {
            throw new IllegalStateException("file.storage_unavailable");
        }
        return storage;
    }

    private AttachedFileDto toDto(AttachedFile f) {
        return new AttachedFileDto(
                f.getUuid(), f.getCategory(), f.getVisibility(), f.getFileName(), f.getMimeType(), f.getSizeBytes(),
                f.getUploadedBy() != null ? f.getUploadedBy().getUuid() : null,
                f.getUploadedAt(), f.isShared(), f.getPublishedAt());
    }
}
