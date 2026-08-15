package com.fenixcore.optibienestar360.common.storage;

import com.fenixcore.optibienestar360.common.storage.dto.DownloadLinkDto;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.fenixcore.optibienestar360.common.service.StorageService;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Shareable download links with a business-level expiration — spec §6.
 * The token in {@code file_download_links} is what's emailed/shown; the
 * actual R2 presigned URL is re-minted (short TTL) on every hit of
 * {@code GET /v1/files/links/{token}} rather than baked into the link, since
 * R2 caps presigned URLs at 7 days and a link's business validity can run
 * longer.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FileDownloadLinkService {

    private static final String PUBLIC_PATH = "/v1/public/files/links/";

    private final FileDownloadLinkRepository repository;
    private final UserRepository userRepository;
    private final ObjectProvider<StorageService> storageProvider;
    private final PresignedUrlPolicy presignedUrlPolicy;

    @Transactional
    public DownloadLinkDto create(FileVisibility visibility, String resourceTable, UUID resourceUuid,
                                   String fileKey, Instant expiresAt, Integer maxDownloads, UUID createdByUuid) {
        FileDownloadLink link = new FileDownloadLink();
        link.setToken(UUID.randomUUID());
        link.setVisibility(visibility);
        link.setResourceTable(resourceTable);
        link.setResourceUuid(resourceUuid);
        link.setFileKey(fileKey);
        link.setExpiresAt(expiresAt);
        link.setMaxDownloads(maxDownloads);
        link.setCreatedAt(Instant.now());
        if (createdByUuid != null) {
            User creator = userRepository.findByUuid(createdByUuid).orElse(null);
            link.setCreatedBy(creator);
        }
        repository.save(link);

        String url = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(PUBLIC_PATH)
                .path(link.getToken().toString())
                .toUriString();
        return new DownloadLinkDto(url, expiresAt);
    }

    /** Revokes every still-active link pointing at this exact file — used by {@code unpublish}. */
    @Transactional
    public void revokeAllFor(String resourceTable, UUID resourceUuid, String fileKey) {
        repository.findByResourceTableAndResourceUuidAndFileKeyAndRevokedAtIsNull(resourceTable, resourceUuid, fileKey)
                .forEach(link -> link.setRevokedAt(Instant.now()));
    }

    /**
     * Resolves a token to a fresh presigned URL. Throws
     * {@code file.download_link.not_found} (404) or
     * {@code file.download_link.expired} (410) — callers map these to HTTP.
     */
    @Transactional
    public String resolvePresignedUrl(UUID token) {
        FileDownloadLink link = repository.findByToken(token)
                .orElseThrow(() -> new NoSuchElementException("file.download_link.not_found"));
        if (!link.isValid()) {
            throw new IllegalStateException("file.download_link.expired");
        }

        StorageService storage = storageProvider.getIfAvailable();
        if (storage == null) {
            throw new IllegalStateException("file.download_link.storage_unavailable");
        }

        link.setDownloadCount(link.getDownloadCount() + 1);
        String bucket = link.getVisibility() == FileVisibility.PUBLIC
                ? storage.getPublicBucket()
                : storage.getBucket();
        return storage.generatePresignedUrl(bucket, link.getFileKey(), presignedUrlPolicy.clamp(null));
    }
}
