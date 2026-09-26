package com.fenixcore.optibienestar360.modules.scheduling.service.runners;

import com.fenixcore.optibienestar360.core.util.AppTimeZone;
import com.fenixcore.optibienestar360.modules.membership.service.MembershipChargeService;
import com.fenixcore.optibienestar360.modules.membership.service.MembershipChargeService.BatchResult;
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
 * Daily job (V153): "generación de cargos". Creates the next PENDING
 * {@code MembershipCharge} for every live ACTIVE/SUSPENDED membership once
 * it's within the configured {@code daysBeforeDue} window of its scheduled
 * collection date. Seeded as {@code MEMBERSHIP_CHARGE_GENERATION} in
 * {@code scheduled_jobs} (V155). Delegates entirely to
 * {@link MembershipChargeService#generateUpcomingCharges(LocalDate)} — no
 * generation logic lives here, same "single orchestration point" shape as
 * {@link FetchExchangeRatesJobRunner}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MembershipChargeGenerationJobRunner implements ScheduledJobRunner {

    public static final String CODE = "MEMBERSHIP_CHARGE_GENERATION";

    private final ScheduledJobRepository jobRepository;
    private final MembershipChargeService membershipChargeService;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public JobRunResult run() {
        LocalDate today = LocalDate.now(resolveZone());
        BatchResult result = membershipChargeService.generateUpcomingCharges(today);

        Map<String, Object> summary = new HashMap<>();
        summary.put("scanned", result.scanned());
        summary.put("created", result.created());
        log.info("MEMBERSHIP_CHARGE_GENERATION: scanned={} created={}", result.scanned(), result.created());
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
