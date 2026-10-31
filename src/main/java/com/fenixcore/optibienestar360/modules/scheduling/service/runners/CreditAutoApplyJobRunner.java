package com.fenixcore.optibienestar360.modules.scheduling.service.runners;

import com.fenixcore.optibienestar360.modules.payment.service.CreditApplicationService;
import com.fenixcore.optibienestar360.modules.payment.service.CreditApplicationService.BatchResult;
import com.fenixcore.optibienestar360.modules.scheduling.service.JobRunResult;
import com.fenixcore.optibienestar360.modules.scheduling.service.ScheduledJobRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Daily job (V205): applies each person's credit to the next uncovered months of their ACTIVE membership.
 * Seeded as {@code CREDIT_AUTO_APPLY} in {@code scheduled_jobs}, after the charge generation. Delegates entirely to
 * {@link CreditApplicationService#applyForAllPending()} — no application logic lives here, same shape as
 * {@link MembershipChargeGenerationJobRunner}.
 */
@Component
@RequiredArgsConstructor
public class CreditAutoApplyJobRunner implements ScheduledJobRunner {

    public static final String CODE = "CREDIT_AUTO_APPLY";

    private final CreditApplicationService creditApplicationService;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public JobRunResult run() {
        BatchResult result = creditApplicationService.applyForAllPending();
        Map<String, Object> summary = new HashMap<>();
        summary.put("people", result.people());
        summary.put("memberships", result.memberships());
        summary.put("monthsCovered", result.monthsCovered());
        return JobRunResult.success(summary);
    }
}
