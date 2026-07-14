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

        CompletableFuture<JobRunResult> future = CompletableFuture
                .supplyAsync(() -> safeRun(runner), schedulerExecutor)
                .whenComplete((result, throwable) -> {
                    JobRunResult finalResult = throwable != null
                            ? JobRunResult.failure("Runner threw: " + throwable.getMessage())
                            : result;
                    try {
                        txTemplate.executeWithoutResult(status -> finalizeRun(runId, finalResult));
                    } catch (RuntimeException ex) {
                        log.error("Failed to finalize run {} for job {}", runUuid, job.getCode(), ex);
                    }
                });

        int waitSeconds = Math.max(0, job.getMaxSyncSeconds());
        if (waitSeconds == 0) {
            return ManualRunResult.async(runUuid);
        }

        try {
            JobRunResult result = future.get(waitSeconds, TimeUnit.SECONDS);
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
        ScheduledJob job = jobRepository.findByUuid(jobUuid).orElse(null);
        if (job == null || !job.isActive() || !job.isEnabled()) return;

        if (!job.isAllowConcurrent() && runRepository.hasRunningFor(job.getId())) {
            log.info("Skipping scheduled fire for {} — previous run still in flight", job.getCode());
            recordSkip(job);
            return;
        }

        ScheduledJobRunner runner = runnersByCode.get(job.getCode());
        if (runner == null) {
            log.warn("No runner registered for code {} — scheduled fire skipped", job.getCode());
            return;
        }

        ScheduledJobRun run = txTemplate.execute(status ->
                startRun(job, TriggerSource.SCHEDULED, null));
        Long runId = run.getId();

        JobRunResult result;
        try {
            result = safeRun(runner);
        } catch (Throwable t) {
            result = JobRunResult.failure("Runner threw: " + t.getMessage());
        }
        JobRunResult finalResult = result;

        try {
            txTemplate.executeWithoutResult(status -> finalizeRun(runId, finalResult));
        } catch (RuntimeException ex) {
            log.error("Failed to finalize scheduled run {} for job {}", run.getUuid(), job.getCode(), ex);
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

    private void finalizeRun(Long runId, JobRunResult result) {
        ScheduledJobRun run = runRepository.findById(runId).orElseThrow();
        run.setFinishedAt(Instant.now());
        run.setDurationMs(run.getFinishedAt().toEpochMilli() - run.getStartedAt().toEpochMilli());
        run.setOutcome((result.success() ? Outcome.SUCCESS : Outcome.FAILED).name());
        run.setSummary(result.summary());
        run.setErrorMessage(result.errorMessage());

        ScheduledJob job = run.getScheduledJob();
        jobRepository.findById(job.getId()).ifPresent(managed -> {
            managed.setLockHeld(false);
            managed.setLastRunAt(run.getFinishedAt());
            managed.setLastRunStatus(run.getOutcome());
        });
    }

    private void recordSkip(ScheduledJob job) {
        txTemplate.executeWithoutResult(status -> {
            ScheduledJobRun skip = new ScheduledJobRun();
            skip.setScheduledJob(job);
            skip.setStartedAt(Instant.now());
            skip.setFinishedAt(Instant.now());
            skip.setDurationMs(0L);
            skip.setOutcome(Outcome.SKIPPED_CONCURRENT.name());
            skip.setTriggeredBy(TriggerSource.SCHEDULED.name());
            runRepository.save(skip);
        });
    }
}
