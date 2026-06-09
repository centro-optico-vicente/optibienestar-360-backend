package com.fenixcore.optisaludplus.modules.scheduling.service;

/**
 * Contract every scheduled-job implementation publishes. Beans implementing
 * this interface are auto-discovered by {@code JobExecutionService} and
 * matched to {@code scheduled_jobs} rows via {@link #code()}.
 *
 * <p>Implementations should be idempotent when {@code allow_concurrent=true},
 * and should never throw — wrap any error inside the returned
 * {@link JobRunResult#failure(String)}. Throwing an unchecked exception
 * works (the framework catches it) but loses the chance to attach a
 * partial summary.</p>
 *
 * <p>Lifecycle: a runner is invoked from the {@code schedulerExecutor}
 * thread pool, not from the HTTP request thread. {@code @Transactional}
 * declared inside the runner is respected; the framework does NOT open a
 * transaction for the runner itself (the surrounding bookkeeping commits
 * are independent).</p>
 */
public interface ScheduledJobRunner {

    /**
     * Stable identifier matching {@code scheduled_jobs.code}. Must be unique
     * across all {@code ScheduledJobRunner} beans in the application
     * context — duplicate codes are detected at startup and fail
     * deterministically.
     */
    String code();

    /**
     * Executes the job. Synchronous from the runner's perspective; the
     * framework wraps the call in a {@code CompletableFuture} when needed
     * (manual trigger with bounded sync wait).
     */
    JobRunResult run();
}
