package com.fenixcore.optibienestar360.modules.scheduling.service.runners;

import com.fenixcore.optibienestar360.core.util.AppTimeZone;
import com.fenixcore.optibienestar360.modules.membership.service.MembershipTermRenewalService;
import com.fenixcore.optibienestar360.modules.membership.service.MembershipTermRenewalService.BatchResult;
import com.fenixcore.optibienestar360.modules.scheduling.repository.ScheduledJobRepository;
import com.fenixcore.optibienestar360.modules.scheduling.service.JobRunResult;
import com.fenixcore.optibienestar360.modules.scheduling.service.ScheduledJobRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

/**
 * Daily job (V220, hub ADR 0034): renews the term of every live membership that requires re-inscription and
 * whose {@code expires_at} has been reached; with re-inscription pending the membership is SUSPENDED until an
 * approved inscription payment is applied. Seeded as {@code MEMBERSHIP_TERM_RENEWAL} (01:30, before the charge
 * generation at 02:00). Delegates to {@link MembershipTermRenewalService#renewDueTerms(LocalDate)}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MembershipTermRenewalJobRunner implements ScheduledJobRunner {

    public static final String CODE = "MEMBERSHIP_TERM_RENEWAL";

    private final ScheduledJobRepository jobRepository;
    private final MembershipTermRenewalService termRenewalService;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public JobRunResult run() {
        LocalDate today = LocalDate.now(resolveZone());
        BatchResult result = termRenewalService.renewDueTerms(today);

        Map<String, Object> summary = new HashMap<>();
        summary.put("scanned", result.scanned());
        summary.put("renewed", result.renewed());
        summary.put("suspended", result.suspended());
        log.info("MEMBERSHIP_TERM_RENEWAL: scanned={} renewed={} suspended={}", result.scanned(), result.renewed(), result.suspended());
        return JobRunResult.success(summary);
    }

    private ZoneId resolveZone() {
        return jobRepository.findByCode(CODE)
                .map(job -> {
                    try {
                        return ZoneId.of(job.getTimezone());
                    } catch (RuntimeException ex) {
                        log.warn("Invalid timezone '{}' on {} — falling back to {}",
                                job.getTimezone(), CODE, AppTimeZone.ZONE);
                        return AppTimeZone.ZONE;
                    }
                })
                .orElse(AppTimeZone.ZONE);
    }
}
