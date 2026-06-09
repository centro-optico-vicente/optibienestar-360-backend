package com.fenixcore.optisaludplus.modules.scheduling.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Unconditional configuration for the runner-execution thread pool.
 *
 * <p>Kept separate from {@link SchedulingConfig} (which is conditional on
 * {@code app.scheduler.enabled=true}) because the manual {@code /run-now}
 * path needs this executor even on replicas where cron triggering is
 * disabled — and the dispatch flow inside {@code JobExecutionService} is
 * unconditional. Per the plan: "replica still serves HTTP including the
 * manual /run-now endpoint, because JobExecutionService does not depend on
 * the scheduler bean".</p>
 *
 * <p>This pool also serves scheduled invocations on enabled replicas — both
 * trigger fires and manual /run-now invocations end up here.</p>
 */
@Configuration
public class SchedulerExecutorConfig {

    @Bean(name = "schedulerExecutor")
    public ThreadPoolTaskExecutor schedulerExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("sched-run-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
