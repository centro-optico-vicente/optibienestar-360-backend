package com.fenixcore.optibienestar360.modules.scheduling.service.runners;

import com.fenixcore.optibienestar360.core.util.AppTimeZone;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionRetroactiveTopUpRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionRetroactiveTopUpResponse;
import com.fenixcore.optibienestar360.modules.promoter.service.CommissionRetroactiveTopUpService;
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
 * Daily automation of {@link CommissionRetroactiveTopUpService#executeCut}
 * — the retroactive-settlement axis' scheduler (hub plan
 * "commission-frequency-currency-unification", phase 3). Unlike its two
 * siblings ({@link CommissionTierSettlementCutJobRunner}/{@link
 * HierarchyOverrideSettlementCutJobRunner}), this job runner does NOT
 * pre-filter "is today the cut-close day" itself: {@code executeCut} is
 * already a batch that internally resolves, per {@code LedgerType} and per
 * beneficiary/rule, whether {@code asOf} falls inside a closable retroactive
 * cut and only upserts a row when the computed {@code retroAmount > 0} — a
 * no-op day for every rule is therefore a cheap, safe, zero-write call, not
 * a correctness risk (the underlying upsert only ever touches still-{@code
 * PENDING} rows).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CommissionRetroactiveSettlementCutJobRunner implements ScheduledJobRunner {

    public static final String CODE = "COMMISSION_RETROACTIVE_SETTLEMENT_CUT";

    private final ScheduledJobRepository jobRepository;
    private final CommissionRetroactiveTopUpService commissionRetroactiveTopUpService;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public JobRunResult run() {
        LocalDate today = LocalDate.now(resolveZone());
        CommissionRetroactiveTopUpRequest request = new CommissionRetroactiveTopUpRequest(null, null, today, false);
        CommissionRetroactiveTopUpResponse response = commissionRetroactiveTopUpService.executeCut(request);

        Map<String, Object> summary = new HashMap<>();
        summary.put("asOf", today.toString());
        summary.put("topUps", response.totalTopUps());
        summary.put("totalRetro", response.totalRetroAmount());
        summary.put("currency", response.currency());

        log.info("COMMISSION_RETROACTIVE_SETTLEMENT_CUT completed: asOf={} topUps={} totalRetro={}",
                today, response.totalTopUps(), response.totalRetroAmount());
        return JobRunResult.success(summary);
    }

    private ZoneId resolveZone() {
        return jobRepository.findByCode(CODE)
                .map(job -> {
                    try {
                        return ZoneId.of(job.getTimezone());
                    } catch (RuntimeException ex) {
                        log.warn("Invalid timezone '{}' on {} job row — falling back to America/Caracas",
                                job.getTimezone(), CODE);
                        return AppTimeZone.ZONE;
                    }
                })
                .orElse(AppTimeZone.ZONE);
    }
}
