package com.fenixcore.optibienestar360.modules.product.service;

import com.fenixcore.optibienestar360.common.storage.AttachedFile;
import com.fenixcore.optibienestar360.common.storage.AttachedFileRepository;
import com.fenixcore.optibienestar360.common.storage.AttachedFileService;
import com.fenixcore.optibienestar360.common.storage.FileVisibility;
import com.fenixcore.optibienestar360.common.storage.dto.AttachedFileDto;
import com.fenixcore.optibienestar360.modules.product.entity.Product;
import com.fenixcore.optibienestar360.modules.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/**
 * Catalog image lifecycle for {@link Product} — spec
 * {@code .ai/specs/08-storage-r2.md} §5. Since ADR 0026 the image belongs to
 * the product master (it used to hang off each ally service). Thin domain
 * wiring on top of the generic {@link AttachedFileService}: upload lands as a
 * private {@code INTERNAL} attachment (never reachable at a guessable public
 * URL); {@code publish}/{@code unpublish} are separate, explicit actions.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductImageService {

    public static final String OWNER_TABLE = "products";
    private static final String CATEGORY = "CATALOG_IMAGE";

    private final ProductRepository productRepository;
    private final AttachedFileRepository attachedFileRepository;
    private final AttachedFileService attachedFileService;

    @Transactional
    public AttachedFileDto upload(UUID productUuid, MultipartFile file, UUID actorUuid) {
        Product product = find(productUuid);

        // Replace semantics: at most one current image per product.
        currentImage(product.getUuid()).ifPresent(existing -> attachedFileService.delete(existing.getUuid()));

        return attachedFileService.upload(OWNER_TABLE, product.getUuid(), FileVisibility.INTERNAL, CATEGORY, file, actorUuid);
    }

    @Transactional
    public void publish(UUID productUuid, UUID actorUuid) {
        Product product = find(productUuid);
        AttachedFile image = currentImage(product.getUuid())
                .orElseThrow(() -> new NoSuchElementException("product.image.not_found"));

        var result = attachedFileService.publish(image.getUuid(), true, null, actorUuid);
        product.setImageKey(result.publicKey());
    }

    @Transactional
    public void unpublish(UUID productUuid) {
        Product product = find(productUuid);
        // Clear the denormalized pointer first — the public catalog must stop
        // showing the image immediately, even before the copy-back completes.
        product.setImageKey(null);

        currentImage(product.getUuid()).ifPresent(image -> attachedFileService.unpublish(image.getUuid()));
    }

    @Transactional
    public void delete(UUID productUuid) {
        Product product = find(productUuid);
        product.setImageKey(null);
        currentImage(product.getUuid()).ifPresent(image -> attachedFileService.delete(image.getUuid()));
    }

    /**
     * Called when an ally's offer is removed: an exclusive product has no
     * other offer left to show its image, so a published image is retracted.
     */
    @Transactional
    public void unpublishIfPresent(UUID productUuid) {
        currentImage(productUuid).filter(f -> f.getPublishedAt() != null || f.getVisibility() == FileVisibility.PUBLIC)
                .ifPresent(image -> {
                    productRepository.findByUuid(productUuid).ifPresent(p -> p.setImageKey(null));
                    attachedFileService.unpublish(image.getUuid());
                });
    }

    private Optional<AttachedFile> currentImage(UUID productUuid) {
        List<AttachedFile> matches = attachedFileRepository
                .findByOwnerTableAndOwnerUuidAndCategoryAndActiveTrue(OWNER_TABLE, productUuid, CATEGORY);
        return matches.stream().max(Comparator.comparing(AttachedFile::getUploadedAt, Instant::compareTo));
    }

    private Product find(UUID productUuid) {
        return productRepository.findByUuid(productUuid)
                .orElseThrow(() -> new NoSuchElementException("product.not_found"));
    }
}
