package com.fenixcore.optibienestar360.core.util;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Memoizes a value for the lifetime of the current transaction.
 *
 * <p>For lookups that a single unit of work repeats with the same answer, such as
 * the organization singleton or the exchange-rate history of a currency pair
 * that a listing converts once per row. Unlike a shared cache it can never
 * serve stale data to another request or replica: the memo starts empty with
 * each transaction and is dropped when the transaction completes.</p>
 *
 * <p>A {@code REQUIRES_NEW} transaction gets its own memo. The outer memo is
 * suspended and resumed with the outer transaction, like a transaction-bound
 * resource. With no active transaction synchronization, the loader runs on
 * every call (no memoization).</p>
 *
 * <p>Only memoize values that the same transaction does not change afterwards.
 * Managed JPA entities are fine: the persistence context hands back the same
 * instance anyway.</p>
 */
public final class TransactionScopedMemo {

    private static final Object RESOURCE_KEY = TransactionScopedMemo.class;

    private TransactionScopedMemo() {}

    /**
     * Returns the value memoized under {@code key} in the current transaction,
     * computing it with {@code loader} the first time. {@code null} results
     * are memoized too.
     */
    @SuppressWarnings("unchecked")
    public static <T> T get(Object key, Supplier<T> loader) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return loader.get();
        }
        Map<Object, Object> memo = (Map<Object, Object>) TransactionSynchronizationManager.getResource(RESOURCE_KEY);
        if (memo == null) {
            memo = new HashMap<>();
            TransactionSynchronizationManager.bindResource(RESOURCE_KEY, memo);
            TransactionSynchronizationManager.registerSynchronization(new MemoSynchronization(memo));
        }
        // Not Map.computeIfAbsent: the loader may memoize other keys (re-entrant
        // HashMap modification), and null results must be remembered as well.
        if (memo.containsKey(key)) {
            return (T) memo.get(key);
        }
        T value = loader.get();
        memo.put(key, value);
        return value;
    }

    /** Unbinds the memo when its transaction completes; hides it while a nested REQUIRES_NEW runs. */
    private record MemoSynchronization(Map<Object, Object> memo) implements TransactionSynchronization {

        @Override
        public void suspend() {
            TransactionSynchronizationManager.unbindResourceIfPossible(RESOURCE_KEY);
        }

        @Override
        public void resume() {
            TransactionSynchronizationManager.bindResource(RESOURCE_KEY, memo);
        }

        @Override
        public void afterCompletion(int status) {
            TransactionSynchronizationManager.unbindResourceIfPossible(RESOURCE_KEY);
        }
    }
}
