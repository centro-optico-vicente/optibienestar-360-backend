package com.fenixcore.optibienestar360.modules.product.service;

import com.fenixcore.optibienestar360.common.service.StorageService;
import com.fenixcore.optibienestar360.common.storage.AttachedFile;
import com.fenixcore.optibienestar360.common.storage.AttachedFileRepository;
import com.fenixcore.optibienestar360.common.storage.FileVisibility;
import com.fenixcore.optibienestar360.common.storage.StorageKeyBuilder;
import com.fenixcore.optibienestar360.modules.media.MediaTables;
import com.fenixcore.optibienestar360.modules.media.entity.MediaItem;
import com.fenixcore.optibienestar360.modules.media.repository.MediaItemRepository;
import com.fenixcore.optibienestar360.modules.media.service.MediaItemsService;
import com.fenixcore.optibienestar360.modules.media.service.MediaOwner;
import com.fenixcore.optibienestar360.modules.media.service.MediaOwnerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * One-shot move of catalog images from the legacy {@code ally_services/}
 * prefix to {@code products/} (hub ADR 0026). V198 already re-pointed each
 * {@code attached_files} row to its product ({@code owner_table='products'})
 * but left {@code file_key} alone, because SQL cannot move R2 objects. Since
 * ADR 0027 the image is a {@code media_items} row, so its {@code public_key}
 * and the owner's {@code logo_url} are corrected here too.
 *
 * <p>Off by default; enabled per environment with
 * {@code storage.r2.legacy-product-keys.migrate=true} and switched off again
 * afterwards. For each pending row it copies the object under the new key,
 * then updates {@code file_key} (and the product's {@code image_key} when it
 * pointed at the same object) in its own transaction — so a failure leaves
 * the row on the old, still-existing object. Idempotent: a second run finds
 * nothing left to move. Old objects are kept unless
 * {@code storage.r2.legacy-product-keys.delete-legacy=true}; when kept, their
 * keys are logged for manual cleanup.</p>
 */
@Slf4j
@Component
// Both flags: StorageService itself only exists with storage.r2.enabled=true.
@ConditionalOnProperty(name = {"storage.r2.enabled", "storage.r2.legacy-product-keys.migrate"}, havingValue = "true")
@RequiredArgsConstructor
public class ProductImageKeyMigrationRunner implements ApplicationRunner {

    private static final String LEGACY_SEGMENT = "/ally_services/";

    private final StorageService storage;
    private final AttachedFileRepository attachedFileRepository;
    private final MediaItemRepository mediaItemRepository;
    private final MediaOwnerRegistry mediaOwners;
    private final MediaItemsService mediaItemsService;
    private final TransactionTemplate transactionTemplate;

    @Value("${storage.r2.legacy-product-keys.delete-legacy:false}")
    private boolean deleteLegacy;

    @Override
    public void run(ApplicationArguments args) {
        List<AttachedFile> pending = attachedFileRepository
                .findByOwnerTableAndFileKeyContaining(MediaTables.PRODUCTS, LEGACY_SEGMENT);
        log.info("product image key migration: {} file(s) under the legacy prefix", pending.size());

        int moved = 0;
        for (AttachedFile file : pending) {
            try {
                move(file);
                moved++;
            } catch (RuntimeException e) {
                log.error("product image key migration: could not move {} (file {}) — left untouched",
                        file.getFileKey(), file.getUuid(), e);
            }
        }
        log.info("product image key migration: {} of {} file(s) moved", moved, pending.size());
    }

    private void move(AttachedFile file) {
        String bucket = file.getVisibility() == FileVisibility.PUBLIC ? storage.getPublicBucket() : storage.getBucket();
        String oldKey = file.getFileKey();
        String newKey = StorageKeyBuilder.build(file.getVisibility(), MediaTables.PRODUCTS,
                file.getOwnerUuid(), file.getFileName());

        storage.copy(bucket, oldKey, bucket, newKey);

        transactionTemplate.executeWithoutResult(status -> {
            AttachedFile managed = attachedFileRepository.findById(file.getId()).orElseThrow();
            managed.setFileKey(newKey);
            // The gallery image that points at this object follows it, and the
            // owner's logo_url is rebuilt from the (possibly new) primary key.
            MediaOwner owner = mediaOwners.byTableOrNull(MediaTables.PRODUCTS);
            for (MediaItem item : mediaItemRepository.findByAttachedFile_Id(managed.getId())) {
                if (oldKey.equals(item.getPublicKey())) {
                    item.setPublicKey(newKey);
                }
            }
            if (owner != null) {
                mediaItemsService.syncLogoUrl(owner, managed.getOwnerUuid());
            }
        });

        if (deleteLegacy) {
            storage.delete(bucket, oldKey);
        } else {
            log.info("product image key migration: legacy object kept {}/{}", bucket, oldKey);
        }
    }
}
