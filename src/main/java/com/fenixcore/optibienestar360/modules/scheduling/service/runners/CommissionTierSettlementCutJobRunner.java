package com.fenixcore.optibienestar360.modules.scheduling.service.runners;

import com.fenixcore.optibienestar360.core.util.AppTimeZone;
import com.fenixcore.optibienestar360.core.util.PeriodCutCalculator;
import com.fenixcore.optibienestar360.core.util.PeriodStrategies;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.AppliesTo;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionTier;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionTierRepository;
import com.fenixcore.optibienestar360.modules.promoter.service.CommissionPeriodicSettlementService;
import com.fenixcore.optibienestar360.modules.promoter.service.CommissionPeriodicSettlementService.SettlementOutcome;
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
 * Daily automation of {@link CommissionPeriodicSettlementService#settleCut}
 * — phase 3 of the hub plan ("commission-frequency-currency-unification"),
 * the piece {@code CommissionPeriodicSettlementService}'s own Javadoc calls
 * out as still missing: <i>"a scheduler ... can call once per configured
 * cut"</i>.
 *
 * <p>Walks every active {@link CommissionTier} and, for each one, resolves
 * "today's" partial-settlement cut inside its final-settlement window. Only
 * acts when {@code today} is exactly the cut's last day ({@code
 * cut.end().equals(today)}) — {@code settleCut} mutates {@code APPROVED}
 * rows to {@code PAID}, so calling it before the cut has actually closed
 * would pay comissions early. Candidate promoters for that cut are
 * discovered via {@link CommissionRepository#findApprovedForPeriod}
 * (whole-window start through the cut's end) grouped by promoter id, then
 * {@code settleCut} is invoked once per distinct promoter.</p>
 *
 * <p>Two (or more) active rules that happen to share the exact same
 * frequency-axis configuration will both resolve the same cut for the same
 * promoter on the same day and both call {@code settleCut} — intentionally
 * not deduplicated across rules: {@code settleCut} recomputes and
 * overwrites with the same result when nothing changed, so a duplicate call
 * is a no-op, not a double payment.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CommissionTierSettlementCutJobRunner implements ScheduledJobRunner {

    public static final String CODE = "COMMISSION_TIER_SETTLEMENT_CUT";

    private final ScheduledJobRepository jobRepository;
    private final CommissionTierRepository commissionTierRepository;
    private final CommissionRepository commissionRepository;
    private final CommissionPeriodicSettlementService settlementService;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    @Transactional
    public JobRunResult run() {
        LocalDate today = LocalDate.now(resolveZone());
        String payoutReference = "auto-cut:" + today;

        List<CommissionTier> activeRules = commissionTierRepository.findByActiveTrue();

        int rulesProcessed = 0;
        int rulesAtCut = 0;
        int promotersSettled = 0;
        int commissionsPaid = 0;
        BigDecimal totalPaid = BigDecimal.ZERO;

        for (CommissionTier rule : activeRules) {
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

            List<Commission> candidates = commissionRepository.findApprovedForPeriod(settlementWindow.start(), cut.end())
                    .stream()
                    .filter(c -> c.getAppliesTo() == AppliesTo.INSCRIPTION)
                    .toList();

            Map<Long, Promoter> distinctPromoters = new LinkedHashMap<>();
            for (Commission c : candidates) {
                distinctPromoters.putIfAbsent(c.getPromoter().getId(), c.getPromoter());
            }

            for (Promoter promoter : distinctPromoters.values()) {
                SettlementOutcome outcome = settlementService.settleCut(promoter, rule, today, payoutReference, false);
                if (outcome.commissionsPaid() > 0) {
                    promotersSettled++;
                    commissionsPaid += outcome.commissionsPaid();
                    totalPaid = totalPaid.add(outcome.totalPaid());
                }
            }
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("asOf", today.toString());
        summary.put("rulesProcessed", rulesProcessed);
        summary.put("rulesAtCut", rulesAtCut);
        summary.put("promotersSettled", promotersSettled);
        summary.put("commissionsPaid", commissionsPaid);
        summary.put("totalPaid", totalPaid);

        log.info("COMMISSION_TIER_SETTLEMENT_CUT completed: asOf={} rulesProcessed={} rulesAtCut={} "
                        + "promotersSettled={} commissionsPaid={} totalPaid={}",
                today, rulesProcessed, rulesAtCut, promotersSettled, commissionsPaid, totalPaid);
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
