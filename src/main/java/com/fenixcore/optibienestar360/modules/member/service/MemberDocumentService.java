package com.fenixcore.optibienestar360.modules.member.service;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.Auditable;
import com.fenixcore.optibienestar360.common.service.StorageService;
import com.fenixcore.optibienestar360.common.storage.FileValidationService;
import com.fenixcore.optibienestar360.common.storage.FileVisibility;
import com.fenixcore.optibienestar360.common.storage.PresignedUrlPolicy;
import com.fenixcore.optibienestar360.common.storage.StorageKeyBuilder;
import com.fenixcore.optibienestar360.common.storage.dto.ChecklistDto;
import com.fenixcore.optibienestar360.common.storage.dto.FileUrlDto;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.member.dto.MemberDocumentDto;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.member.entity.MemberDocument;
import com.fenixcore.optibienestar360.modules.member.entity.MemberDocument.DocumentType;
import com.fenixcore.optibienestar360.modules.member.repository.MemberDocumentRepository;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.core.util.DefaultSortResolver;
import com.fenixcore.optibienestar360.core.util.SortFieldValidator;
import com.fenixcore.optibienestar360.core.util.SortOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

/**
 * Wires the previously-unwired {@link MemberDocument} entity (V17) to the
 * shared storage building blocks — spec {@code .ai/specs/08-storage-r2.md}
 * §4/§4.1. Kept as its own bespoke table (not migrated to the generic
 * {@code attached_files} mechanism, since it already shipped), but reuses
 * {@link StorageKeyBuilder}, {@link PresignedUrlPolicy} and
 * {@link FileValidationService} instead of re-declaring them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberDocumentService {

    private static final String OWNER_TABLE = "members";
    private static final FileVisibility VISIBILITY = FileVisibility.CONFIDENTIAL;

    /** Recaudos requeridos para considerar completo el expediente de un afiliado (spec §4.1). */
    public static final Set<DocumentType> REQUIRED_TYPES =
            EnumSet.of(DocumentType.ID_FRONT, DocumentType.ID_BACK, DocumentType.MEMBER_PHOTO);

    /** {@code sizeBytes}/{@code uploadedAt} are DTO names for the entity's {@code fileSizeBytes}/{@code createdAt} (inherited from {@code BaseEntity}). */
    private static final Map<String, SortFieldValidator.SortableField> SORTABLE_FIELDS =
            SortFieldValidator.sortableFieldsOf(MemberDocument.class, Map.of(
                    "sizeBytes", "fileSizeBytes",
                    "uploadedAt", "createdAt"
            ));

    private final MemberRepository memberRepository;
    private final MemberDocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final ObjectProvider<StorageService> storageProvider;
    private final FileValidationService fileValidationService;
    private final PresignedUrlPolicy presignedUrlPolicy;
    private final DefaultSortResolver defaultSortResolver;

    @Transactional
    @Auditable(entity = "member_document", action = AuditAction.CREATE)
    public MemberDocumentDto upload(UUID memberUuid, DocumentType documentType, MultipartFile file, UUID uploaderUuid) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("member_document.upload.empty");
        }
        Member member = findMember(memberUuid);
        fileValidationService.validate(VISIBILITY, file);

        // Replace semantics — a re-upload of the same type supersedes the previous one.
        documentRepository.findFirstByMemberIdAndDocumentTypeAndActiveTrue(member.getId(), documentType)
                .ifPresent(this::deleteEntity);

        StorageService storage = requireStorage();
        String key = StorageKeyBuilder.build(VISIBILITY, OWNER_TABLE, member.getUuid(), file.getOriginalFilename());
        try {
            storage.upload(key, file.getInputStream(), file.getSize(), file.getContentType());
        } catch (IOException ex) {
            log.error("Failed to read upload stream for member {}", memberUuid, ex);
            throw new IllegalArgumentException("member_document.upload.failed");
        }

        MemberDocument document = new MemberDocument();
        document.setMember(member);
        document.setDocumentType(documentType);
        document.setFileUrl(key);
        document.setFileName(StorageKeyBuilder.safeName(file.getOriginalFilename()));
        document.setFileSizeBytes(file.getSize());
        document.setMimeType(file.getContentType());
        document.setUploadedBy(userOrNull(uploaderUuid));
        documentRepository.save(document);
        return toDto(document);
    }

    public List<MemberDocumentDto> list(UUID memberUuid, UUID currentUserUuid, boolean hasViewAll, Pageable pageable) {
        Member member = findMember(memberUuid);
        Pageable defaulted = defaultSortResolver.withDefaultSortIfUnsorted(
                "member_document", pageable, new SortOrder("createdAt", "DESC"));
        Pageable resolved = SortFieldValidator.resolve(defaulted, SORTABLE_FIELDS, "member_document");
        return documentRepository.findByMemberIdAndActiveTrue(member.getId(), resolved.getSort()).stream()
                .filter(d -> hasViewAll || visibleTo(d, currentUserUuid))
                .map(this::toDto)
                .toList();
    }

    /** Checklist against {@link #REQUIRED_TYPES} — used to gate member approval on a complete expediente. */
    public ChecklistDto listWithChecklist(UUID memberUuid) {
        Member member = findMember(memberUuid);
        List<MemberDocument> all = documentRepository.findByMemberIdAndActiveTrue(member.getId());

        List<ChecklistDto.Item> items = REQUIRED_TYPES.stream()
                .map(type -> all.stream().filter(d -> d.getDocumentType() == type).findFirst()
                        .map(d -> ChecklistDto.Item.uploaded(type.name(), d.getUuid(), d.getFileName(), d.getCreatedAt()))
                        .orElseGet(() -> ChecklistDto.Item.missing(type.name())))
                .toList();

        List<ChecklistDto.Item> extra = all.stream()
                .filter(d -> !REQUIRED_TYPES.contains(d.getDocumentType()))
                .map(d -> ChecklistDto.Item.uploaded(d.getDocumentType().name(), d.getUuid(), d.getFileName(), d.getCreatedAt()))
                .toList();

        boolean complete = items.stream().allMatch(i -> i.status() == ChecklistDto.Status.UPLOADED);
        List<ChecklistDto.Item> combined = new java.util.ArrayList<>(items);
        combined.addAll(extra);
        return new ChecklistDto(combined, complete);
    }

    public FileUrlDto presignedUrl(UUID documentUuid, Duration requestedTtl) {
        MemberDocument document = findManaged(documentUuid);
        StorageService storage = requireStorage();
        Duration ttl = presignedUrlPolicy.clamp(requestedTtl);
        String url = storage.generatePresignedUrl(document.getFileUrl(), ttl);
        return new FileUrlDto(url, Instant.now().plus(ttl), ttl.toSeconds(),
                document.getFileName(), document.getMimeType(), document.getFileSizeBytes());
    }

    @Transactional
    @Auditable(entity = "member_document", action = AuditAction.DELETE, uuidArgIndex = 0)
    public void delete(UUID documentUuid) {
        deleteEntity(findManaged(documentUuid));
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private void deleteEntity(MemberDocument document) {
        StorageService storage = requireStorage();
        storage.delete(document.getFileUrl());
        documentRepository.delete(document);
    }

    private static boolean visibleTo(MemberDocument document, UUID currentUserUuid) {
        User uploader = document.getUploadedBy();
        return uploader != null && currentUserUuid != null && currentUserUuid.equals(uploader.getUuid());
    }

    private Member findMember(UUID uuid) {
        return memberRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("member.not_found"));
    }

    private MemberDocument findManaged(UUID uuid) {
        return documentRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("member_document.not_found"));
    }

    private User userOrNull(UUID uuid) {
        return uuid != null ? userRepository.findByUuid(uuid).orElse(null) : null;
    }

    private StorageService requireStorage() {
        StorageService storage = storageProvider.getIfAvailable();
        if (storage == null) {
            throw new IllegalStateException("member_document.storage_unavailable");
        }
        return storage;
    }

    private MemberDocumentDto toDto(MemberDocument d) {
        return new MemberDocumentDto(d.getUuid(), d.getDocumentType(), d.getFileName(), d.getMimeType(),
                d.getFileSizeBytes(), d.getUploadedBy() != null ? d.getUploadedBy().getUuid() : null, d.getCreatedAt());
    }
}
