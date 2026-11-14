package com.fenixcore.optibienestar360.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
@EnableRetry
public class AsyncConfig {

    @Bean(name = "emailExecutor")
    public Executor emailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("email-");
        // Backpressure instead of failure. The default AbortPolicy throws
        // RejectedExecutionException once the 50-slot queue is full and the 5
        // threads are busy — and because the caller is a request thread (e.g.
        // approving a payment), that surfaced as a 500 on an operation that had
        // already succeeded. CallerRunsPolicy makes the caller send the mail
        // itself: the request gets slower under load, but it does not fail.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * Uploads generated reports to R2 after the response is sent
     * ({@code ReportAuditService}). Small on purpose: each queued task holds the
     * document bytes. When it is full the request thread uploads by itself
     * (slower response, nothing lost), as with {@link #emailExecutor()}.
     */
    @Bean(name = "reportArchiveExecutor")
    public Executor reportArchiveExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("report-archive-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
