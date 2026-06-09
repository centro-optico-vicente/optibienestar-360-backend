package com.fenixcore.optisaludplus.modules.scheduling.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Wires the JVM-internal trigger scheduler used by
 * {@code DynamicScheduledJobsRegistry} to compute next-firing times and
 * dispatch cron tasks.
 *
 * <p>Conditional on {@code app.scheduler.enabled=true} (default). When the
 * property is {@code false}, neither this configuration nor the registry
 * bean instantiate — the replica still serves HTTP traffic (including the
 * manual {@code /run-now} endpoint, because {@code JobExecutionService}
 * depends only on {@code schedulerExecutor} which lives in the
 * unconditional {@link SchedulerExecutorConfig}), but no cron triggers
 * fire on that replica.</p>
 *
 * <p><b>Pool sizing:</b> the {@code taskScheduler} (pool=4) runs the
 * {@code Trigger} lambdas that compute next firing times and dispatch
 * tasks. Lightweight; 4 is generous for the expected ≤10 concurrent jobs.
 * The actual runner business logic runs on {@code schedulerExecutor}
 * (separate config) so a long runner can't block trigger computation.</p>
 *
 * <p><b>Future multi-replica HA path (not implemented today):</b> wrap each
 * dispatched job in {@code pg_try_advisory_lock(hashtext('scheduled_job:' || code))}
 * so only one replica executes any given fire. The {@code lock_held}
 * column in {@code scheduled_jobs} is the bookkeeping for this. Today
 * single-replica → not needed.</p>
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
        name = "app.scheduler.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class SchedulingConfig {

    /**
     * The pool the {@code DynamicScheduledJobsRegistry} uses to fire
     * trigger-based scheduled tasks. Each task does almost no work — it
     * just dispatches the actual runner onto {@code schedulerExecutor}.
     */
    @Bean
    public TaskScheduler scheduledJobsTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("sched-trigger-");
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.initialize();
        return scheduler;
    }
}
