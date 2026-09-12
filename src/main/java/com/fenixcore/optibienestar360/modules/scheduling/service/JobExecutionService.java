package com.fenixcore.optibienestar360.modules.scheduling.service;

import com.fenixcore.optibienestar360.modules.scheduling.entity.ScheduledJob;
import com.fenixcore.optibienestar360.modules.scheduling.entity.ScheduledJobRun;
import com.fenixcore.optibienestar360.modules.scheduling.entity.ScheduledJobRun.Outcome;
import com.fenixcore.optibienestar360.modules.scheduling.entity.ScheduledJobRun.TriggerSource;
import com.fenixcore.optibienestar360.modules.scheduling.repository.ScheduledJobRepository;
import com.fenixcore.optibienestar360.modules.scheduling.repository.ScheduledJobRunRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Orchestrates a single scheduled-job execution — same code path serves the
 * cron-driven invocation from {@code DynamicScheduledJobsRegistry} and the
 * manual {@code POST /run-now} endpoint.
 *
 * <p>Concurrency model:</p>
 * <ul>
 *   <li>Each invocation runs through {@code schedulerExecutor} (pool of 5).</li>
 *   <li>Bookkeeping (startRun + finalizeRun) commits in independent
 *       transactions via {@link TransactionTemplate} so the RUNNING row is
 *       visible to concurrent calls (and future multi-replica peers) the
 *       instant it's inserted.</li>
 *   <li>Manual trigger uses {@link CompletableFuture#get(long, TimeUnit)}
 *       with {@code max_sync_seconds} — on timeout we return 202 but the
 *       future keeps running and finalizes the row when done.</li>
 * </ul>
 *
 * <p>Runner registry: built once at {@link PostConstruct} from the auto-injected
 * {@link List} of beans, keyed by their {@link ScheduledJobRunner#code()}.
 * Duplicate codes fail-fast at startup with a clear error.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JobExecutionService {

    private final ScheduledJobRepository jobRepository;
    private final ScheduledJobRunRepository runRepository;
    private final List<ScheduledJobRunner> runners;

    @Qualifier("schedulerExecutor")
    private final ThreadPoolTaskExecutor schedulerExecutor;

    private final TransactionTemplate txTemplate;

    private Map<String, ScheduledJobRunner> runnersByCode;

    @PostConstruct
    void buildRunnerIndex() {
        runnersByCode = new HashMap<>();
        for (ScheduledJobRunner runner : runners) {
            ScheduledJobRunner previous = runnersByCode.put(runner.code(), runner);
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate scheduled job runner code: " + runner.code()
                        + " (beans " + previous.getClass().getName()
                        + " and " + runner.getClass().getName() + ")");
            }
        }
        log.info("Scheduled job runners indexed: {}", runnersByCode.keySet());
    }

    /**
     * Fully-qualified class name of the {@code ScheduledJobRunner} bean
     * registered for {@code code}, if any. Backs the read-only "executor"
     * panel in the admin edit modal so a job code with no matching runner
     * (a job that will never actually run) is visible instead of silent.
     */
    public Optional<String> runnerClassName(String code) {
        ScheduledJobRunner runner = runnersByCode.get(code);
        return Optional.ofNullable(runner).map(r -> r.getClass().getName());
    }

    // ─── Manual trigger (hybrid sync/async) ────────────────────────────────

    public ManualRunResult runNow(UUID jobUuid, UUID actorUserUuid) {
        ScheduledJob job = jobRepository.findByUuid(jobUuid)
                .orElseThrow(() -> new NoSuchElementException("scheduled_job.not_found"));

        if (!job.isAllowConcurrent() && runRepository.hasRunningFor(job.getId())) {
            throw new IllegalArgumentException("scheduled_job.run.already_in_flight");
        }

        ScheduledJobRunner runner = runnersByCode.get(job.getCode());
        if (runner == null) {
            throw new IllegalArgumentException("scheduled_job.runner.not_registered");
        }

        ScheduledJobRun run = txTemplate.execute(status ->
                startRun(job, TriggerSource.MANUAL, actorUserUuid));
        Long runId = run.getId();
        UUID runUuid = run.getUuid();

        CompletableFuture<AttemptedResult> future = CompletableFuture
                .supplyAsync(() -> runWithRetries(runner, job), schedulerExecutor)
                .whenComplete((attempted, throwable) -> {
                    AttemptedResult finalAttempt = throwable != null
                            ? new AttemptedResult(JobRunResult.failure("Runner threw: " + throwable.getMessage()), 1)
                            : attempted;
                    try {
                        txTemplate.executeWithoutResult(status -> finalizeRun(runId, finalAttempt));
                    } catch (RuntimeException ex) {
                        log.error("Failed to finalize run {} for job {}", runUuid, job.getCode(), ex);
                    }
                });

        int waitSeconds = Math.max(0, job.getMaxSyncSeconds());
        if (waitSeconds == 0) {
            return ManualRunResult.async(runUuid);
        }

        try {
            JobRunResult result = future.get(waitSeconds, TimeUnit.SECONDS).result();
            return ManualRunResult.synced(runUuid, result);
        } catch (TimeoutException timeout) {
            // Future keeps running; whenComplete will finalize the row.
            return ManualRunResult.async(runUuid);
        } catch (Exception ex) {
            log.error("Manual run for job {} failed inside sync wait", job.getCode(), ex);
            return ManualRunResult.synced(runUuid, JobRunResult.failure(ex.getMessage()));
        }
    }

    // ─── Scheduled trigger (called by registry, fire-and-forget) ──────────

    /**
     * Entry point for the registry's {@code Trigger}-driven invocations. We
     * re-fetch the job by uuid to pick up any cron / enabled edits that
     * happened between the trigger computation and the fire. Returns
     * immediately if the job is no longer eligible.
     */
    public void runScheduled(UUID jobUuid) {
        runTriggered(jobUuid, TriggerSource.SCHEDULED);
    }

    /**
     * Startup catch-up for a job whose {@code scheduled_jobs.last_run_at} is
     * still {@code null} — it has never executed, e.g. freshly seeded or the
     * app hasn't stayed up long enough to hit its own cron. Runs it once,
     * immediately, so its data isn't stale until the next scheduled firing.
     *
     * <p>Called from {@code DynamicScheduledJobsRegistry#onApplicationReady}
     * <em>after</em> the job's normal cron trigger is armed — that trigger
     * keeps owning every future firing; this only fills the gap for the one
     * that already should have happened. Dispatched onto
     * {@code schedulerExecutor} so it never blocks application startup, and
     * re-checks {@code lastRunAt} once inside the async task in case another
     * trigger (e.g. a very tight cron) already ran it in the meantime.</p>
     */
    public void triggerStartupCatchUp(UUID jobUuid) {
        schedulerExecutor.execute(() -> {
            ScheduledJob job = jobRepository.findByUuid(jobUuid).orElse(null);
            if (job == null || job.getLastRunAt() != null) return;
            runTriggered(jobUuid, TriggerSource.STARTUP);
        });
    }

    private void runTriggered(UUID jobUuid, TriggerSource source) {
        ScheduledJob job = jobRepository.findByUuid(jobUuid).orElse(null);
        if (job == null || !job.isActive() || !job.isEnabled()) return;

        if (!job.isAllowConcurrent() && runRepository.hasRunningFor(job.getId())) {
            log.info("Skipping {} fire for {} — previous run still in flight", source, job.getCode());
            recordSkip(job, source);
            return;
        }

        ScheduledJobRunner runner = runnersByCode.get(job.getCode());
        if (runner == null) {
            log.warn("No runner registered for code {} — {} fire skipped", job.getCode(), source);
            return;
        }

        ScheduledJobRun run = txTemplate.execute(status ->
                startRun(job, source, null));
        Long runId = run.getId();

        AttemptedResult attempted;
        try {
            attempted = runWithRetries(runner, job);
        } catch (Throwable t) {
            attempted = new AttemptedResult(JobRunResult.failure("Runner threw: " + t.getMessage()), 1);
        }
        AttemptedResult finalAttempt = attempted;

        try {
            txTemplate.executeWithoutResult(status -> finalizeRun(runId, finalAttempt));
        } catch (RuntimeException ex) {
            log.error("Failed to finalize {} run {} for job {}", source, run.getUuid(), job.getCode(), ex);
        }
    }

    // ─── Internals ─────────────────────────────────────────────────────────

    private JobRunResult safeRun(ScheduledJobRunner runner) {
        try {
            JobRunResult result = runner.run();
            return result != null ? result : JobRunResult.failure("Runner returned null");
        } catch (Throwable t) {
            log.error("Runner {} threw exception", runner.code(), t);
            return JobRunResult.failure("Runner threw: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    /**
     * Runs the job with fixed-backoff retries per {@code job}'s
     * {@code maxRetryAttempts}/{@code retryDelaySeconds}. Attempt 1 is
     * always tried; on failure it waits {@code retryDelaySeconds} and tries
     * again, up to {@code 1 + maxRetryAttempts} total attempts. Runs on the
     * {@code schedulerExecutor} thread pool (both call sites), never on the
     * HTTP request thread, so the blocking sleep is safe.
     */
    private AttemptedResult runWithRetries(ScheduledJobRunner runner, ScheduledJob job) {
        int maxAttempts = 1 + Math.max(0, job.getMaxRetryAttempts());
        int delaySeconds = Math.max(0, job.getRetryDelaySeconds());

        JobRunResult result = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            result = safeRun(runner);
            if (result.success() || attempt == maxAttempts) {
                return new AttemptedResult(result, attempt);
            }
            log.warn("Job {} failed on attempt {}/{}, retrying in {}s",
                    job.getCode(), attempt, maxAttempts, delaySeconds);
            if (delaySeconds > 0) {
                try {
                    Thread.sleep(delaySeconds * 1000L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return new AttemptedResult(result, attempt);
                }
            }
        }
        return new AttemptedResult(result, maxAttempts);
    }

    private record AttemptedResult(JobRunResult result, int attempts) {}

    private ScheduledJobRun startRun(ScheduledJob job, TriggerSource source, UUID actorUuid) {
        ScheduledJobRun run = new ScheduledJobRun();
        run.setScheduledJob(job);
        run.setStartedAt(Instant.now());
        run.setOutcome(Outcome.RUNNING.name());
        run.setTriggeredBy(source.name());
        run.setTriggeredByUserUuid(actorUuid);
        run = runRepository.save(run);

        // Reload job in the current persistence context to mark the soft mutex
        jobRepository.findById(job.getId()).ifPresent(managed -> managed.setLockHeld(true));

        return run;
    }

    private void finalizeRun(Long runId, AttemptedResult attempted) {
        JobRunResult result = attempted.result();
        ScheduledJobRun run = runRepository.findById(runId).orElseThrow();
        run.setFinishedAt(Instant.now());
        run.setDurationMs(run.getFinishedAt().toEpochMilli() - run.getStartedAt().toEpochMilli());
        run.setOutcome((result.success() ? Outcome.SUCCESS : Outcome.FAILED).name());
        run.setSummary(result.summary());
        run.setErrorMessage(result.errorMessage());
        run.setAttemptCount(attempted.attempts());

        ScheduledJob job = run.getScheduledJob();
        jobRepository.findById(job.getId()).ifPresent(managed -> {
            managed.setLockHeld(false);
            managed.setLastRunAt(run.getFinishedAt());
            managed.setLastRunStatus(run.getOutcome());
        });
    }

    private void recordSkip(ScheduledJob job, TriggerSource source) {
        txTemplate.executeWithoutResult(status -> {
            ScheduledJobRun skip = new ScheduledJobRun();
            skip.setScheduledJob(job);
            skip.setStartedAt(Instant.now());
            skip.setFinishedAt(Instant.now());
            skip.setDurationMs(0L);
            skip.setOutcome(Outcome.SKIPPED_CONCURRENT.name());
            skip.setTriggeredBy(source.name());
            runRepository.save(skip);
        });
    }
}
