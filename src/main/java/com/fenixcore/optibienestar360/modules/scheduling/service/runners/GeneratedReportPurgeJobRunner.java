package com.fenixcore.optibienestar360.modules.scheduling.service.runners;

import com.fenixcore.optibienestar360.core.audit.GeneratedReportPurgeService;
import com.fenixcore.optibienestar360.core.audit.GeneratedReportPurgeService.PurgeResult;
import com.fenixcore.optibienestar360.modules.scheduling.service.JobRunResult;
import com.fenixcore.optibienestar360.modules.scheduling.service.ScheduledJobRunner;
import com.fenixcore.optibienestar360.modules.system.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Daily job (V252, seeded <b>disabled</b>: deleting from R2 cannot be undone):
 * deletes generated reports older than {@code system_configs.generated_report_retention_days}
 * from R2 and marks their rows PURGED through {@link GeneratedReportPurgeService}. The
 * retention is read from the system settings on every run, so a change applies
 * without a deploy. The report audit trail is never touched.
 */
@Component
@RequiredArgsConstructor
public class GeneratedReportPurgeJobRunner implements ScheduledJobRunner {

    public static final String CODE = "GENERATED_REPORT_PURGE";

    private final GeneratedReportPurgeService purgeService;
    private final SystemConfigService systemConfigService;
    private final Clock clock;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public JobRunResult run() {
        int retentionDays = Math.max(1, systemConfigService.getSystemConfig().getGeneratedReportRetentionDays());
        Instant cutoff = Instant.now(clock).minus(Duration.ofDays(retentionDays));
        PurgeResult result = purgeService.purgeUploadedBefore(cutoff);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("retentionDays", retentionDays);
        summary.put("purged", result.purged());
        summary.put("failed", result.failed());
        if (!result.storageAvailable()) {
            return JobRunResult.failure("R2 storage is disabled; nothing was purged", summary);
        }
        return result.failed() > 0
                ? JobRunResult.failure(result.failed() + " file(s) could not be deleted from R2; retried on the next run",
                        summary)
                : JobRunResult.success(summary);
    }
}
