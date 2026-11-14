package com.fenixcore.optibienestar360.modules.scheduling.service.runners;

import com.fenixcore.optibienestar360.core.util.ScheduledJobParams;
import com.fenixcore.optibienestar360.modules.scheduling.repository.ScheduledJobRepository;
import com.fenixcore.optibienestar360.modules.scheduling.service.JobRunResult;
import com.fenixcore.optibienestar360.modules.scheduling.service.OperationalLogRetentionService;
import com.fenixcore.optibienestar360.modules.scheduling.service.ScheduledJobRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Daily job (V244, seeded <b>disabled</b>): purges old rows of the operational
 * log tables through {@link OperationalLogRetentionService}. The retention per
 * table, in days, comes from the job's {@code parameters} on every run, so it can
 * be tuned from the scheduled jobs admin without a deploy:
 * {@code notificationsDays} (90), {@code jobRunsDays} (90),
 * {@code downloadLinksDays} (30). Any value below
 * 7 days is raised to 7, so a typo can never wipe recent data.
 */
@Component
@RequiredArgsConstructor
public class OperationalLogRetentionJobRunner implements ScheduledJobRunner {

    public static final String CODE = "OPERATIONAL_LOG_RETENTION";

    static final int MIN_DAYS = 7;

    private final ScheduledJobRepository jobRepository;
    private final OperationalLogRetentionService retentionService;
    private final Clock clock;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public JobRunResult run() {
        Map<String, Object> params = jobRepository.findByCode(CODE).map(job -> job.getParameters()).orElse(Map.of());
        int notificationsDays = days(params, "notificationsDays", 90);
        int jobRunsDays = days(params, "jobRunsDays", 90);
        int downloadLinksDays = days(params, "downloadLinksDays", 30);
        Instant now = Instant.now(clock);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("notifications", retentionService.purgeNotifications(now.minus(Duration.ofDays(notificationsDays))));
        summary.put("jobRuns", retentionService.purgeJobRuns(now.minus(Duration.ofDays(jobRunsDays))));
        summary.put("downloadLinks", retentionService.purgeDownloadLinks(now.minus(Duration.ofDays(downloadLinksDays))));
        summary.put("notificationsDays", notificationsDays);
        summary.put("jobRunsDays", jobRunsDays);
        summary.put("downloadLinksDays", downloadLinksDays);
        return JobRunResult.success(summary);
    }

    private static int days(Map<String, Object> params, String key, int fallback) {
        return Math.max(MIN_DAYS, ScheduledJobParams.intParam(params, key, fallback));
    }
}
