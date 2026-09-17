package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.core.util.PeriodCutCalculator;
import com.fenixcore.optibienestar360.core.util.PeriodStrategies;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.AppliesTo;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.CommissionStatus;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionTier;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionTierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Per-cut settlement engine for direct {@link Commission}s (hub plan
 * ".ai/plans/2026-09-07-hierarchical-commissions-plan.md" §3 "Liquidación
 * parcial por frecuencia configurable + retroactivo al cierre") — the piece
 * that was still missing: {@code PeriodCutCalculator} existed, {@code
 * commission_tiers} now carries {@code payoutPeriodStrategy}/{@code
 * settlementPeriodStrategy} (V112), but nothing yet walked "today's cut" and
 * actually re-priced + paid the {@code APPROVED} rows that fall inside it.
 *
 * <p><b>What this does NOT replace</b>:</p>
 * <ul>
 *   <li>{@link CommissionReRatingService} — still owns bumping {@code
 *       PENDING} rows to the period's final band <i>before</i> commercial
 *       approval. This service only ever touches {@code APPROVED} rows.</li>
 *   <li>{@link CommissionRetroactiveTopUpService} — still owns the
 *       month-close top-up ledger for rows that are already {@code PAID}.
 *       Running this service cut-by-cut through a whole settlement window
 *       and then running the top-up at close is the intended combination:
 *       each cut pays the band that qualifies <i>as of that cut</i>, and the
 *       top-up closes the gap to the period's true final band once the
 *       whole window is known.</li>
 *   <li>{@link CommissionPayoutService} — still owns the broader period-close
 *       operation (CSV, email, cross-source batching with overrides and
 *       top-ups). This service is a narrower, single-promoter/single-cut
 *       primitive a scheduler (or {@code CommissionPayoutService} itself, in
 *       a future PR) can call once per configured cut.</li>
 * </ul>
 *
 * <p>A rule left at the V112 default ({@code MONTHLY}/{@code MONTHLY}) is a
 * no-op change: {@code PeriodCutCalculator.cutContaining("MONTHLY", ...)}
 * always returns the whole settlement month as a single cut, so this service
 * simply re-confirms the already-correct band for the full month and marks
 * every {@code APPROVED} row in it {@code PAID} — exactly what {@code
 * CommissionPayoutService} does today, just scoped to one promoter.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommissionPeriodicSettlementService {

    private final CommissionRepository commissionRepository;
    private final CommissionTierRepository tierRepository;
    private final MemberRepository memberRepository;
    private final CommissionAuditRecorder auditRecorder;

    /** Outcome of settling one promoter's cut. */
    public record SettlementOutcome(
            LocalDate settlementStart, LocalDate settlementEnd,
            LocalDate cutStart, LocalDate cutEnd,
            long accumulatedCount, String targetTierName,
            int commissionsPaid, BigDecimal totalPaid) {
    }

    /**
     * Settles the cut of {@code rule} (its {@link CommissionTier
     * #getPayoutPeriodStrategy()} inside its {@link CommissionTier
     * #getSettlementPeriodStrategy()} window) that contains {@code asOf} for
     * {@code promoter}: re-prices every {@code APPROVED} {@link
     * AppliesTo#INSCRIPTION} commission of that promoter falling inside the
     * cut to the band the promoter's month(-or-whatever)-to-cut-end count
     * qualifies for, then marks them {@code PAID}.
     *
     * @param rule          the commission-tier rule whose period-strategy
     *                      config drives the cut calculation (candidates for
     *                      the actual band selection are still every active
     *                      tier applicable to {@code promoter}, same as
     *                      {@code CommissionService.selectTier} — {@code
     *                      rule} only supplies the cut/settlement window).
     * @param asOf          reference date — "today" in production, an
     *                      arbitrary past date for manual/backfill runs.
     * @param payoutReference free-text reference stamped on every row paid
     *                      by this cut (same field {@code
     *                      CommissionPayoutService} stamps).
     * @param dryRun        when {@code true}, computes and returns the
     *                      outcome without mutating any row.
     */
    @Transactional
    public SettlementOutcome settleCut(Promoter promoter, CommissionTier rule, LocalDate asOf,
                                       String payoutReference, boolean dryRun) {
        PeriodStrategies.Window settlementWindow =
                PeriodStrategies.window(rule.getSettlementPeriodStrategy().name(), asOf);
        PeriodCutCalculator.Cut cut = PeriodCutCalculator.cutContaining(
                rule.getPayoutPeriodStrategy().name(), settlementWindow.start(), settlementWindow.end(), asOf);

        // Month(-or-whatever)-to-cut-end accumulation — same metric CommissionService.selectTier
        // and CommissionRetroactiveTopUpService already use, just clipped to the cut's end instead
        // of the whole settlement window's end (that's exactly the "partial cut" difference).
        long accumulatedCount = memberRepository.countNewSubscribersForPromoter(
                promoter.getId(), settlementWindow.start(), cut.end());

        CommissionTier target = highestQualifyingTier(accumulatedCount, promoter);
        if (target == null) {
            log.warn("Periodic settlement skipped for promoter {} (cut {}..{}): no applicable INSCRIPTION tier",
                    promoter.getReferralCode(), cut.start(), cut.end());
            return new SettlementOutcome(settlementWindow.start(), settlementWindow.end(),
                    cut.start(), cut.end(), accumulatedCount, null, 0, BigDecimal.ZERO);
        }

        List<Commission> cutCommissions = commissionRepository.findApprovedForPromoterAppliesToInPeriod(
                promoter.getId(), AppliesTo.INSCRIPTION, cut.start(), cut.end());

        BigDecimal totalPaid = BigDecimal.ZERO;
        Instant now = Instant.now();
        for (Commission c : cutCommissions) {
            BigDecimal newAmount = recompute(target.getCommissionPct(), target.getFlatAmount(), c.getCalculationBasis());
            totalPaid = totalPaid.add(newAmount);
            if (dryRun) {
                continue;
            }
            Map<String, Object> before = auditRecorder.snapshot(c);
            if (target.getCommissionPct() != null) {
                c.setCommissionPct(target.getCommissionPct());
                c.setFlatAmount(null);
            } else {
                c.setFlatAmount(target.getFlatAmount());
                c.setCommissionPct(null);
            }
            c.setAmount(newAmount);
            c.setCommissionTierId(target.getId());
            c.setTierNameSnapshot(target.getName());
            c.setStatus(CommissionStatus.PAID.name());
            c.setPaidAt(now);
            c.setPayoutReference(payoutReference);
            auditRecorder.recordUpdate(c.getUuid(), before, auditRecorder.snapshot(c));
        }

        log.info("COMMISSION_PERIODIC_SETTLEMENT promoter={} settlement={}..{} cut={}..{} dryRun={} "
                        + "accumulatedCount={} targetTier={} commissionsPaid={} totalPaid={}",
                promoter.getReferralCode(), settlementWindow.start(), settlementWindow.end(),
                cut.start(), cut.end(), dryRun, accumulatedCount, target.getName(), cutCommissions.size(), totalPaid);

        return new SettlementOutcome(settlementWindow.start(), settlementWindow.end(),
                cut.start(), cut.end(), accumulatedCount, target.getName(), cutCommissions.size(), totalPaid);
    }

    /**
     * Same "iterate highest-threshold-first, first qualifying wins" rule as
     * {@code CommissionService.selectTier}/{@code
     * CommissionRetroactiveTopUpService.highestQualifyingCommissionTier} —
     * intentionally NOT extracted into a shared {@code CommissionTierSelector}
     * for this PR (see final report: low risk/reward given the three call
     * sites differ slightly in how they source {@code count} and {@code
     * planType} scoping).
     */
    private CommissionTier highestQualifyingTier(long count, Promoter promoter) {
        Long promoterTypeId = promoter.getPromoterType() != null ? promoter.getPromoterType().getId() : null;
        List<CommissionTier> candidates = tierRepository.findActiveApplicable(
                null, CommissionTier.AppliesTo.INSCRIPTION, CommissionTier.AppliesTo.BOTH, promoterTypeId);
        for (CommissionTier tier : candidates) {
            if (tier.getThresholdCount() <= 0 || count >= tier.getThresholdCount()) {
                return tier;
            }
        }
        return null;
    }

    private static BigDecimal recompute(BigDecimal pct, BigDecimal flatAmount, BigDecimal basis) {
        return pct != null
                ? basis.multiply(pct).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
                : flatAmount;
    }
}
