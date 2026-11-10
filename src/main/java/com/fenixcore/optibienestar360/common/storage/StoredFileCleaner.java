package com.fenixcore.optibienestar360.common.storage;

import com.fenixcore.optibienestar360.common.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Removes stored objects whose database rows are being deleted, on top of {@link StorageService}.
 *
 * <p>Deletion is <b>after commit</b>: when a transaction is active the delete runs only once it has committed, so a
 * rollback never leaves rows pointing at deleted objects. It is <b>best effort</b>: a failure deleting one object is
 * logged as a warning with its key (the only trace, no table) and never fails the caller nor stops the other objects.
 * Without an active transaction the objects are deleted right away; without R2 enabled nothing happens.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StoredFileCleaner {

    private final ObjectProvider<StorageService> storageProvider;

    /** Schedules the deletion of {@code objects} for after the current transaction commits (or runs it now if none). */
    public void deleteAfterCommit(Collection<StoredObject> objects) {
        Set<StoredObject> unique = new LinkedHashSet<>();
        for (StoredObject object : objects) {
            if (object != null && object.key() != null && !object.key().isBlank()) {
                unique.add(object);
            }
        }
        if (unique.isEmpty()) {
            return;
        }
        List<StoredObject> toDelete = List.copyOf(unique);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteNow(toDelete);
                }
            });
        } else {
            deleteNow(toDelete);
        }
    }

    private void deleteNow(List<StoredObject> objects) {
        StorageService storage = storageProvider.getIfAvailable();
        if (storage == null) {
            return;
        }
        for (StoredObject object : objects) {
            try {
                String bucket = object.visibility() == FileVisibility.PUBLIC ? storage.getPublicBucket() : storage.getBucket();
                storage.delete(bucket, object.key());
            } catch (RuntimeException ex) {
                log.warn("Could not delete the stored object {} of a discarded draft; it stays orphaned in the bucket",
                        object.key(), ex);
            }
        }
    }
}
