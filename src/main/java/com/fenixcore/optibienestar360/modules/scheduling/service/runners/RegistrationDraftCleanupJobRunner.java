package com.fenixcore.optibienestar360.modules.scheduling.service.runners;

import com.fenixcore.optibienestar360.modules.onboarding.service.RegistrationDraftService;
import com.fenixcore.optibienestar360.modules.onboarding.service.RegistrationDraftService.CleanupResult;
import com.fenixcore.optibienestar360.modules.scheduling.service.JobRunResult;
import com.fenixcore.optibienestar360.modules.scheduling.service.ScheduledJobRunner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Daily job (V236): discards the registrations that stayed in DRAFT — never sent to review — for more than
 * {@code app.onboarding.draft-cleanup.retention-days} days (default 30), freeing their cédula and email. Seeded as
 * {@code REGISTRATION_DRAFT_CLEANUP} in {@code scheduled_jobs}; it can be paused there or with
 * {@code app.onboarding.draft-cleanup.enabled=false} (the run then reports {@code skipped}). No logic lives here:
 * {@link RegistrationDraftService#purgeAbandoned} does the work and never touches a submitted request.
 */
@Slf4j
@Component
public class RegistrationDraftCleanupJobRunner implements ScheduledJobRunner {

    public static final String CODE = "REGISTRATION_DRAFT_CLEANUP";
    static final int DEFAULT_RETENTION_DAYS = 30;

    private final RegistrationDraftService draftService;
    private final Clock clock;
    private final boolean enabled;
    private final int retentionDays;

    public RegistrationDraftCleanupJobRunner(
            RegistrationDraftService draftService,
            Clock clock,
            @Value("${app.onboarding.draft-cleanup.enabled:true}") boolean enabled,
            @Value("${app.onboarding.draft-cleanup.retention-days:30}") int retentionDays) {
        this.draftService = draftService;
        this.clock = clock;
        this.enabled = enabled;
        this.retentionDays = retentionDays > 0 ? retentionDays : DEFAULT_RETENTION_DAYS;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public JobRunResult run() {
        Map<String, Object> summary = new HashMap<>();
        if (!enabled) {
            summary.put("skipped", true);
            log.info("Registration draft cleanup is disabled (app.onboarding.draft-cleanup.enabled=false)");
            return JobRunResult.success(summary);
        }
        Instant cutoff = Instant.now(clock).minus(Duration.ofDays(retentionDays));
        CleanupResult result = draftService.purgeAbandoned(cutoff);
        summary.put("retentionDays", retentionDays);
        summary.put("memberDrafts", result.memberDrafts());
        summary.put("allyDrafts", result.allyDrafts());
        summary.put("failures", result.failures());
        log.info("Registration draft cleanup: {} affiliation draft(s) and {} ally draft(s) discarded, {} failure(s) (older than {} days)",
                result.memberDrafts(), result.allyDrafts(), result.failures(), retentionDays);
        return result.failures() > 0
                ? JobRunResult.failure(result.failures() + " draft(s) could not be discarded", summary)
                : JobRunResult.success(summary);
    }
}
