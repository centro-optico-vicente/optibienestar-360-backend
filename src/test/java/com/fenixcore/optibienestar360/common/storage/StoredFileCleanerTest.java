package com.fenixcore.optibienestar360.common.storage;

import com.fenixcore.optibienestar360.common.service.StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Stored objects are deleted only after the commit, per their own bucket, and a failure never propagates. */
@ExtendWith(MockitoExtension.class)
class StoredFileCleanerTest {

    @Mock private ObjectProvider<StorageService> storageProvider;
    @Mock private StorageService storage;

    private StoredFileCleaner cleaner;

    @BeforeEach
    void setUp() {
        cleaner = new StoredFileCleaner(storageProvider);
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void storageAvailable() {
        when(storageProvider.getIfAvailable()).thenReturn(storage);
        when(storage.getBucket()).thenReturn("private-bucket");
        when(storage.getPublicBucket()).thenReturn("public-bucket");
    }

    @Test
    void insideATransaction_deletesOnlyAfterTheCommit() {
        storageAvailable();
        TransactionSynchronizationManager.initSynchronization();

        cleaner.deleteAfterCommit(List.of(
                new StoredObject(FileVisibility.CONFIDENTIAL, "k1"),
                new StoredObject(FileVisibility.PUBLIC, "k2")));

        verifyNoInteractions(storage);
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }
        verify(storage).delete("private-bucket", "k1");
        verify(storage).delete("public-bucket", "k2");
    }

    @Test
    void aRolledBackTransaction_deletesNothing() {
        TransactionSynchronizationManager.initSynchronization();

        cleaner.deleteAfterCommit(List.of(new StoredObject(FileVisibility.CONFIDENTIAL, "k1")));

        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }
        verifyNoInteractions(storage);
    }

    @Test
    void aFailureDeletingOneObject_doesNotPropagateNorStopTheOthers() {
        storageAvailable();
        doThrow(new RuntimeException("R2 down")).when(storage).delete("private-bucket", "k1");
        TransactionSynchronizationManager.initSynchronization();

        cleaner.deleteAfterCommit(List.of(
                new StoredObject(FileVisibility.CONFIDENTIAL, "k1"),
                new StoredObject(FileVisibility.PUBLIC, "k2")));

        assertThatCode(() -> {
            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }
        }).doesNotThrowAnyException();
        verify(storage).delete("public-bucket", "k2");
    }

    @Test
    void withoutATransaction_deletesRightAway_ignoringBlankKeysAndDuplicates() {
        storageAvailable();

        cleaner.deleteAfterCommit(List.of(
                new StoredObject(FileVisibility.CONFIDENTIAL, "k1"),
                new StoredObject(FileVisibility.CONFIDENTIAL, "k1"),
                new StoredObject(FileVisibility.CONFIDENTIAL, " "),
                new StoredObject(FileVisibility.CONFIDENTIAL, null)));

        verify(storage).delete("private-bucket", "k1");
        verify(storage, never()).delete("private-bucket", " ");
    }

    @Test
    void withoutStorageEnabled_doesNothing() {
        when(storageProvider.getIfAvailable()).thenReturn(null);

        assertThatCode(() -> cleaner.deleteAfterCommit(List.of(new StoredObject(FileVisibility.PUBLIC, "k1"))))
                .doesNotThrowAnyException();
        verifyNoInteractions(storage);
    }
}
