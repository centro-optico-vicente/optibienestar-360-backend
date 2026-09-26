package com.fenixcore.optibienestar360.modules.scheduling.service.runners;

import com.fenixcore.optibienestar360.core.util.AppTimeZone;
import com.fenixcore.optibienestar360.core.util.PeriodCutCalculator;
import com.fenixcore.optibienestar360.core.util.PeriodStrategies;
import com.fenixcore.optibienestar360.modules.promoter.entity.HierarchyOverrideTier;
import com.fenixcore.optibienestar360.modules.promoter.entity.HierarchyOverrideTier.OverrideCategory;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterHierarchyOverride;
import com.fenixcore.optibienestar360.modules.promoter.repository.HierarchyOverrideTierRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterHierarchyOverrideRepository;
import com.fenixcore.optibienestar360.modules.promoter.service.HierarchyOverridePeriodicSettlementService;
import com.fenixcore.optibienestar360.modules.promoter.service.HierarchyOverridePeriodicSettlementService.SettlementOutcome;
import com.fenixcore.optibienestar360.modules.scheduling.repository.ScheduledJobRepository;
import com.fenixcore.optibienestar360.modules.scheduling.service.JobRunResult;
import com.fenixcore.optibienestar360.modules.scheduling.service.ScheduledJobRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Daily automation of {@link HierarchyOverridePeriodicSettlementService#settleCut}
 * — the hierarchy-override analogue of {@link CommissionTierSettlementCutJobRunner},
 * same phase-3 automation piece (hub plan
 * "commission-frequency-currency-unification").
 *
 * <p>Walks every active {@link HierarchyOverrideTier} and, for each one,
 * resolves "today's" partial-settlement cut inside its final-settlement
 * window. Only acts when {@code today} is exactly the cut's last day —
 * {@code settleCut} mutates {@code PENDING} overrides (whose root commission
 * is already {@code APPROVED}) to {@code PAID}. Candidate beneficiaries are
 * discovered from {@link PromoterHierarchyOverrideRepository#findPendingForPeriod}
 * (whole-window start through the cut's end), scoped to the rule's own
 * {@link OverrideCategory}, grouped by promoter id.</p>
 *
 * <p>Same intentional non-deduplication across rules sharing a frequency
 * axis as the commission-tier sibling: {@code settleCut} recomputes and
 * overwrites the same result, so a duplicate call is a no-op.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HierarchyOverrideSettlementCutJobRunner implements ScheduledJobRunner {

    public static final String CODE = "HIERARCHY_OVERRIDE_SETTLEMENT_CUT";

    private final ScheduledJobRepository jobRepository;
    private final HierarchyOverrideTierRepository hierarchyOverrideTierRepository;
    private final PromoterHierarchyOverrideRepository overrideRepository;
    private final HierarchyOverridePeriodicSettlementService settlementService;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    @Transactional
    public JobRunResult run() {
        LocalDate today = LocalDate.now(resolveZone());
        String payoutReference = "auto-cut:" + today;

        List<HierarchyOverrideTier> activeRules = hierarchyOverrideTierRepository.findByActiveTrue();

        int rulesProcessed = 0;
        int rulesAtCut = 0;
        int beneficiariesSettled = 0;
        int overridesPaid = 0;
        BigDecimal totalPaid = BigDecimal.ZERO;

        for (HierarchyOverrideTier rule : activeRules) {
            rulesProcessed++;

            PeriodStrategies.Window settlementWindow = PeriodStrategies.window(
                    rule.getFinalSettlementPeriodStrategy().name(), today, rule.getFinalSettlementPeriodAnchor());
            PeriodCutCalculator.Cut cut = PeriodCutCalculator.cutContaining(
                    rule.getPartialSettlementPeriodStrategy().name(), settlementWindow.start(), settlementWindow.end(),
                    today, rule.getPartialSettlementPeriodAnchor());

            if (!cut.end().equals(today)) {
                continue; // not this rule's cut-close day
            }
            rulesAtCut++;

            OverrideCategory category = rule.getCategory();
            List<PromoterHierarchyOverride> candidates = overrideRepository
                    .findPendingForPeriod(settlementWindow.start(), cut.end())
                    .stream()
                    .filter(o -> o.getCategory() == category)
                    .toList();

            Map<Long, Promoter> distinctBeneficiaries = new LinkedHashMap<>();
            for (PromoterHierarchyOverride o : candidates) {
                distinctBeneficiaries.putIfAbsent(o.getPromoter().getId(), o.getPromoter());
            }

            for (Promoter beneficiary : distinctBeneficiaries.values()) {
                SettlementOutcome outcome = settlementService.settleCut(beneficiary, rule, today, payoutReference, false);
                if (outcome.overridesPaid() > 0) {
                    beneficiariesSettled++;
                    overridesPaid += outcome.overridesPaid();
                    totalPaid = totalPaid.add(outcome.totalPaid());
                }
            }
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("asOf", today.toString());
        summary.put("rulesProcessed", rulesProcessed);
        summary.put("rulesAtCut", rulesAtCut);
        summary.put("beneficiariesSettled", beneficiariesSettled);
        summary.put("overridesPaid", overridesPaid);
        summary.put("totalPaid", totalPaid);

        log.info("HIERARCHY_OVERRIDE_SETTLEMENT_CUT completed: asOf={} rulesProcessed={} rulesAtCut={} "
                        + "beneficiariesSettled={} overridesPaid={} totalPaid={}",
                today, rulesProcessed, rulesAtCut, beneficiariesSettled, overridesPaid, totalPaid);
        return JobRunResult.success(summary);
    }

    private ZoneId resolveZone() {
        return jobRepository.findByCode(CODE)
                .map(job -> {
                    try {
                        return ZoneId.of(job.getTimezone());
                    } catch (RuntimeException ex) {
                        log.warn("Invalid timezone '{}' on {} job row — falling back to {}",
                                job.getTimezone(), CODE, AppTimeZone.ZONE);
                        return AppTimeZone.ZONE;
                    }
                })
                .orElse(AppTimeZone.ZONE);
    }
}
