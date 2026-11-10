package com.fenixcore.optibienestar360.modules.scheduling.service.runners;

import com.fenixcore.optibienestar360.modules.onboarding.service.OnboardingPolicyService;
import com.fenixcore.optibienestar360.modules.onboarding.service.RegistrationDraftService;
import com.fenixcore.optibienestar360.modules.onboarding.service.RegistrationDraftService.CleanupResult;
import com.fenixcore.optibienestar360.modules.scheduling.service.JobRunResult;
import com.fenixcore.optibienestar360.modules.scheduling.service.ScheduledJobRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Daily job (V236): discards the registrations that stayed in DRAFT — never sent to review — and untouched for more
 * than the retention of their kind, freeing their cédula and email. The retention comes from the onboarding policy
 * on every run (V237: {@code member_draft_retention_days}, default 15, and {@code ally_draft_retention_days},
 * default 30 for long negotiations; both at least 1). A draft flagged "retain from cleanup" (V237) is
 * skipped. Seeded as {@code REGISTRATION_DRAFT_CLEANUP} in {@code scheduled_jobs}; it can be paused there.
 * No logic lives here: {@link RegistrationDraftService#purgeAbandoned} does the work and never touches a submitted
 * request.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegistrationDraftCleanupJobRunner implements ScheduledJobRunner {

    public static final String CODE = "REGISTRATION_DRAFT_CLEANUP";

    private final RegistrationDraftService draftService;
    private final OnboardingPolicyService policyService;
    private final Clock clock;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public JobRunResult run() {
        int memberDays = Math.max(1, policyService.memberDraftRetentionDays());
        int allyDays = Math.max(1, policyService.allyDraftRetentionDays());
        Map<String, Object> summary = new HashMap<>();
        summary.put("memberRetentionDays", memberDays);
        summary.put("allyRetentionDays", allyDays);
        Instant now = Instant.now(clock);
        CleanupResult result = draftService.purgeAbandoned(
                now.minus(Duration.ofDays(memberDays)), now.minus(Duration.ofDays(allyDays)));
        summary.put("memberDrafts", result.memberDrafts());
        summary.put("allyDrafts", result.allyDrafts());
        summary.put("failures", result.failures());
        log.info("Registration draft cleanup: {} affiliation draft(s) (older than {} days) and {} ally draft(s) (older than {} days) discarded, {} failure(s)",
                result.memberDrafts(), memberDays, result.allyDrafts(), allyDays, result.failures());
        return result.failures() > 0
                ? JobRunResult.failure(result.failures() + " draft(s) could not be discarded", summary)
                : JobRunResult.success(summary);
    }
}
