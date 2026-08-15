package com.fenixcore.optibienestar360.modules.ally.service;

import com.fenixcore.optibienestar360.common.storage.AttachedFile;
import com.fenixcore.optibienestar360.common.storage.AttachedFileRepository;
import com.fenixcore.optibienestar360.common.storage.AttachedFileService;
import com.fenixcore.optibienestar360.common.storage.FileVisibility;
import com.fenixcore.optibienestar360.common.storage.dto.AttachedFileDto;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyService;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Catalog image lifecycle for {@link AllyService} — spec
 * {@code .ai/specs/08-storage-r2.md} §5. Thin domain wiring on top of the
 * generic {@link AttachedFileService}: upload lands as a private
 * {@code INTERNAL} attachment (so an unapproved/unpublished service's image
 * is never reachable at a guessable public URL); {@code publish}/
 * {@code unpublish} are separate, explicit actions from there.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AllyServiceImageService {

    private static final String OWNER_TABLE = "ally_services";
    private static final String CATEGORY = "CATALOG_IMAGE";

    private final AllyServiceRepository serviceRepository;
    private final AttachedFileRepository attachedFileRepository;
    private final AttachedFileService attachedFileService;

    @Transactional
    public AttachedFileDto upload(UUID allyUuid, UUID serviceUuid, MultipartFile file, UUID actorUuid) {
        AllyService service = findServiceUnderAlly(allyUuid, serviceUuid);

        // Replace semantics: at most one current image per service.
        currentImage(service.getUuid()).ifPresent(existing -> attachedFileService.delete(existing.getUuid()));

        return attachedFileService.upload(OWNER_TABLE, service.getUuid(), FileVisibility.INTERNAL, CATEGORY, file, actorUuid);
    }

    @Transactional
    public void publish(UUID allyUuid, UUID serviceUuid, UUID actorUuid) {
        AllyService service = findServiceUnderAlly(allyUuid, serviceUuid);
        AttachedFile image = currentImage(service.getUuid())
                .orElseThrow(() -> new NoSuchElementException("ally_service.image.not_found"));

        var result = attachedFileService.publish(image.getUuid(), true, null, actorUuid);
        service.setImageKey(result.publicKey());
    }

    @Transactional
    public void unpublish(UUID allyUuid, UUID serviceUuid) {
        AllyService service = findServiceUnderAlly(allyUuid, serviceUuid);
        // Clear the denormalized pointer first — the public directory must stop
        // showing the image immediately, even before the copy-back completes.
        service.setImageKey(null);

        currentImage(service.getUuid()).ifPresent(image -> attachedFileService.unpublish(image.getUuid()));
    }

    @Transactional
    public void delete(UUID allyUuid, UUID serviceUuid) {
        AllyService service = findServiceUnderAlly(allyUuid, serviceUuid);
        service.setImageKey(null);
        currentImage(service.getUuid()).ifPresent(image -> attachedFileService.delete(image.getUuid()));
    }

    /** Called from {@code AllyServiceReviewService.remove()} — a removed service can't keep a published image. */
    @Transactional
    public void unpublishIfPresent(UUID serviceUuid) {
        currentImage(serviceUuid).filter(f -> f.getPublishedAt() != null || f.getVisibility() == FileVisibility.PUBLIC)
                .ifPresent(image -> {
                    serviceRepository.findByUuid(serviceUuid).ifPresent(s -> s.setImageKey(null));
                    attachedFileService.unpublish(image.getUuid());
                });
    }

    private java.util.Optional<AttachedFile> currentImage(UUID serviceUuid) {
        List<AttachedFile> matches = attachedFileRepository
                .findByOwnerTableAndOwnerUuidAndCategoryAndActiveTrue(OWNER_TABLE, serviceUuid, CATEGORY);
        return matches.stream().max(java.util.Comparator.comparing(AttachedFile::getUploadedAt, Instant::compareTo));
    }

    private AllyService findServiceUnderAlly(UUID allyUuid, UUID serviceUuid) {
        AllyService service = serviceRepository.findByUuid(serviceUuid)
                .orElseThrow(() -> new NoSuchElementException("ally_service.not_found"));
        if (!service.getAlly().getUuid().equals(allyUuid)) {
            throw new NoSuchElementException("ally_service.not_found");
        }
        return service;
    }
}
