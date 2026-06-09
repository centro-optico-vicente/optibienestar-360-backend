package com.fenixcore.optisaludplus.modules.scheduling.config;

import com.fenixcore.optisaludplus.modules.scheduling.entity.ScheduledJob;
import com.fenixcore.optisaludplus.modules.scheduling.repository.ScheduledJobRepository;
import com.fenixcore.optisaludplus.modules.scheduling.service.JobExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;

/**
 * In-memory JVM-local registry of currently-active scheduled jobs and the
 * runtime entry point for hot-reload after admin mutations.
 *
 * <p><b>What this is and is not:</b></p>
 * <ul>
 *   <li>{@link #scheduledFutures} is <em>not</em> a cache of data. It's the
 *       per-replica map of {@link ScheduledFuture} handles to tasks the
 *       JVM's {@link TaskScheduler} has currently armed. The actual job
 *       configuration (cron, timezone, enabled, …) lives in Postgres
 *       (table {@code scheduled_jobs}) and is the source of truth; the
 *       map only tracks "which jobs has this JVM scheduled".</li>
 *   <li>Each replica has its own independent map. There is no Redis or
 *       cross-replica state for this — the partial-shared coordinator
 *       lives in Postgres (today via the {@code SCHEDULER_ENABLED} env
 *       var; future via {@code pg_try_advisory_lock}).</li>
 * </ul>
 *
 * <p><b>Hot reload:</b> after an admin endpoint commits a mutation to
 * {@code scheduled_jobs}, it calls
 * {@link #register(ScheduledJob)} / {@link #unregister(String)} /
 * {@link #reschedule(ScheduledJob)} from a
 * {@code TransactionSynchronization#afterCommit()} hook. The next cron
 * computation reads the updated row directly from the DB inside the
 * {@link Trigger#nextExecution(TriggerContext)} lambda, so cron edits take
 * effect at the very next firing computation with no polling thread.</p>
 *
 * <p>Conditional on {@code app.scheduler.enabled=true} (default). Disabled
 * replicas don't instantiate this bean and therefore never register any
 * triggers — but they can still serve admin endpoints since
 * {@link JobExecutionService} is unconditional.</p>
 */
@Component
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class DynamicScheduledJobsRegistry {

    private final ScheduledJobRepository jobRepository;
    private final JobExecutionService executionService;

    @Qualifier("scheduledJobsTaskScheduler")
    private final TaskScheduler taskScheduler;

    /**
     * JVM-local handle map. Keyed by {@code scheduled_jobs.code}. Concurrent
     * because admin endpoints may register/unregister from request threads
     * while the registry is loading initial jobs on the application-ready
     * thread.
     */
    private final ConcurrentMap<String, ScheduledFuture<?>> scheduledFutures = new ConcurrentHashMap<>();

    /**
     * Loads every enabled job from the DB and registers it. Runs after the
     * full ApplicationContext is ready so all {@code ScheduledJobRunner}
     * beans are visible to {@link JobExecutionService}.
     */
    @EventListener(ApplicationReadyEvent.class)
    public synchronized void onApplicationReady() {
        log.info("Loading scheduled jobs from database");
        int count = 0;
        for (ScheduledJob job : jobRepository.findAllByActiveTrueAndEnabledTrue()) {
            try {
                register(job);
                count++;
            } catch (RuntimeException ex) {
                log.error("Failed to register scheduled job {}", job.getCode(), ex);
            }
        }
        log.info("Scheduled jobs registry initialized with {} job(s)", count);
    }

    /**
     * Arms a job. Idempotent: if a previous {@link ScheduledFuture} exists
     * for the same code, it is cancelled first. The {@link Trigger} lambda
     * re-reads the cron from the DB on each computation, so admins can edit
     * cron live and the next firing reflects the change.
     */
    public synchronized void register(ScheduledJob job) {
        if (job == null || !job.isActive() || !job.isEnabled()) return;

        cancelExisting(job.getCode());

        UUID jobUuid = job.getUuid();
        String code = job.getCode();

        Trigger trigger = new Trigger() {
            @Override
            public Instant nextExecution(TriggerContext triggerContext) {
                ScheduledJob current = jobRepository.findByCode(code).orElse(null);
                if (current == null || !current.isActive() || !current.isEnabled()) {
                    return null;
                }
                CronTrigger cron = new CronTrigger(
                        current.getCronExpression(),
                        ZoneId.of(current.getTimezone()));
                return cron.nextExecution(triggerContext);
            }
        };

        ScheduledFuture<?> future = taskScheduler.schedule(
                () -> executionService.runScheduled(jobUuid),
                trigger);

        if (future != null) {
            scheduledFutures.put(code, future);
            log.info("Registered scheduled job {} cron='{}' tz={}",
                    code, job.getCronExpression(), job.getTimezone());
        } else {
            log.warn("TaskScheduler returned null future for {} — not registered", code);
        }
    }

    /**
     * Cancels the active future for the given code (no-op if none).
     * {@code mayInterruptIfRunning=false} so an in-flight run completes
     * gracefully; the runner is not interrupted mid-execution.
     */
    public synchronized void unregister(String code) {
        if (cancelExisting(code)) {
            log.info("Unregistered scheduled job {}", code);
        }
    }

    /**
     * Idempotent re-register: cancels the current future (if any) and arms
     * a fresh one from the supplied job snapshot. Called from admin update
     * after-commit hooks.
     */
    public synchronized void reschedule(ScheduledJob job) {
        if (job == null) return;
        cancelExisting(job.getCode());
        register(job);
    }

    /** Returns whether a given job code is currently armed in this JVM. */
    public boolean isRegistered(String code) {
        return scheduledFutures.containsKey(code);
    }

    /** Test/diagnostics hook — count of armed jobs in this JVM. */
    public int registeredCount() {
        return scheduledFutures.size();
    }

    private boolean cancelExisting(String code) {
        ScheduledFuture<?> previous = scheduledFutures.remove(code);
        if (previous != null) {
            previous.cancel(false);
            return true;
        }
        return false;
    }
}
