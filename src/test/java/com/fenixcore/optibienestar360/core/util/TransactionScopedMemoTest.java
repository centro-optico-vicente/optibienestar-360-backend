package com.fenixcore.optibienestar360.core.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for {@link TransactionScopedMemo}. */
class TransactionScopedMemoTest {

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void withoutATransactionTheLoaderRunsEveryTime() {
        AtomicInteger calls = new AtomicInteger();

        TransactionScopedMemo.get("k", calls::incrementAndGet);
        TransactionScopedMemo.get("k", calls::incrementAndGet);

        assertThat(calls).hasValue(2);
    }

    @Test
    void insideATransactionTheLoaderRunsOncePerKey() {
        TransactionSynchronizationManager.initSynchronization();
        AtomicInteger calls = new AtomicInteger();

        Integer first = TransactionScopedMemo.get("k", calls::incrementAndGet);
        Integer second = TransactionScopedMemo.get("k", calls::incrementAndGet);
        TransactionScopedMemo.get("other", calls::incrementAndGet);

        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(1);
        assertThat(calls).hasValue(2);
    }

    @Test
    void nullResultsAreMemoizedToo() {
        TransactionSynchronizationManager.initSynchronization();
        AtomicInteger calls = new AtomicInteger();

        TransactionScopedMemo.get("k", () -> { calls.incrementAndGet(); return null; });
        Object again = TransactionScopedMemo.get("k", () -> { calls.incrementAndGet(); return null; });

        assertThat(again).isNull();
        assertThat(calls).hasValue(1);
    }

    @Test
    void theMemoIsDroppedWhenTheTransactionCompletes() {
        TransactionSynchronizationManager.initSynchronization();
        AtomicInteger calls = new AtomicInteger();
        TransactionScopedMemo.get("k", calls::incrementAndGet);

        List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
        syncs.forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        TransactionSynchronizationManager.clearSynchronization();

        TransactionSynchronizationManager.initSynchronization();
        TransactionScopedMemo.get("k", calls::incrementAndGet);
        assertThat(calls).hasValue(2);
    }

    @Test
    void aSuspendedTransactionHidesItsMemoFromTheNestedOne() {
        TransactionSynchronizationManager.initSynchronization();
        AtomicInteger calls = new AtomicInteger();
        TransactionScopedMemo.get("k", calls::incrementAndGet);

        // What AbstractPlatformTransactionManager does for REQUIRES_NEW.
        List<TransactionSynchronization> outer = TransactionSynchronizationManager.getSynchronizations();
        outer.forEach(TransactionSynchronization::suspend);
        TransactionSynchronizationManager.clearSynchronization();

        TransactionSynchronizationManager.initSynchronization();
        Integer nested = TransactionScopedMemo.get("k", calls::incrementAndGet);
        assertThat(nested).isEqualTo(2);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        TransactionSynchronizationManager.clearSynchronization();

        TransactionSynchronizationManager.initSynchronization();
        outer.forEach(s -> { s.resume(); TransactionSynchronizationManager.registerSynchronization(s); });
        Integer resumed = TransactionScopedMemo.get("k", calls::incrementAndGet);
        assertThat(resumed).isEqualTo(1);
        assertThat(calls).hasValue(2);
    }
}
