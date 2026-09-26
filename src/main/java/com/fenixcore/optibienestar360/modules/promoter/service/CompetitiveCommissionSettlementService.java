package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.core.util.PeriodCutCalculator;
import com.fenixcore.optibienestar360.core.util.PeriodStrategies;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionAward;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionAwardSettlement;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionAwardSettlement.CutKind;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.CompetitiveMetric;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.PeriodAxisStrategy;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRulePosition.RewardType;
import com.fenixcore.optibienestar360.modules.promoter.metric.CompetitiveMetricProvider;
import com.fenixcore.optibienestar360.modules.promoter.metric.CompetitiveRankingEngine.Candidate;
import com.fenixcore.optibienestar360.modules.promoter.metric.MetricScope;
import com.fenixcore.optibienestar360.modules.promoter.repository.CompetitiveCommissionAwardRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.CompetitiveCommissionAwardSettlementRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.CompetitiveCommissionRuleRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * D14 — turns a confirmed {@link CompetitiveCommissionAward} into {@link
 * CompetitiveCommissionAwardSettlement} cuts as the rule's own partial/retroactive/final
 * settlement axes come due (hub plan competitive-commission-rules, Fase 2b).
 *
 * <p><b>Why an award's amount doesn't need "recomputing" at every cut:</b> a RANKING award is
 * only ever created/confirmed once its whole accrual period has already closed (§7), so its
 * {@code amount} is already the final figure — settling it is a single {@code FINAL} cut equal
 * to {@code amount}, never anything partial or retroactive (Ronda 2 feedback: paying a position
 * before the period closes would reward someone who can still be overtaken). A FIRST_TO_REACH
 * award's {@code FLAT} amount is fixed the moment they cross the threshold — nothing to
 * recompute there either. The one case with real growth after confirmation is FIRST_TO_REACH
 * with a {@code PERCENTAGE} reward: the promoter keeps transacting through the rest of the
 * period, so each cut before the period's close re-asks the metric provider for the basis
 * accumulated so far ({@code periodStart..cutEnd}) and nets out what's already been paid — the
 * same cumulative-netting shape as {@code commission_retroactive_topup_cuts} (V150).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompetitiveCommissionSettlementService {

    private final CompetitiveCommissionRuleRepository ruleRepository;
    private final CompetitiveCommissionAwardRepository awardRepository;
    private final CompetitiveCommissionAwardSettlementRepository settlementRepository;
    private final List<CompetitiveMetricProvider> providers;

    private Map<CompetitiveMetric, CompetitiveMetricProvider> providerByMetric;

    @PostConstruct
    void indexProviders() {
        providerByMetric = providers.stream().collect(Collectors.toMap(CompetitiveMetricProvider::metric, p -> p));
    }

    public record CutOutcome(int settlementsCreated, int settlementsSkippedZero) {
        static final CutOutcome NONE = new CutOutcome(0, 0);
    }

    /**
     * Processes every cut of {@code rule} whose boundary falls on {@code asOf}, for the accrual
     * window containing {@code asOf}. Idempotent by {@code uq_ccas_cut} — running the same day
     * twice nets to the same result, and a {@code PAID} cut is never recomputed or reopened.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CutOutcome executeCutForRule(UUID ruleUuid, LocalDate asOf, boolean dryRun) {
        CompetitiveCommissionRule rule = ruleRepository.findByUuid(ruleUuid)
                .orElseThrow(() -> new NoSuchElementException("competitive_commission_rule.not_found"));
        PeriodStrategies.Window window = CompetitiveRuleWindowResolver.resolveWindow(rule, asOf);

        List<CutDue> dueCuts = resolveDueCuts(rule, window, asOf);
        if (dueCuts.isEmpty()) {
            return CutOutcome.NONE;
        }

        List<CompetitiveCommissionAward> awards = awardRepository
                .findByRule_IdAndPeriodStartAndActiveTrueAndStatusNot(rule.getId(), window.start(), "VOIDED").stream()
                .filter(a -> "PENDING".equals(a.getStatus()) || "PAID".equals(a.getStatus()))
                .toList();
        if (awards.isEmpty()) {
            return CutOutcome.NONE;
        }

        int created = 0, skippedZero = 0;
        for (CutDue cut : dueCuts) {
            Map<Long, Candidate> basisByPromoter = cut.cutEnd().equals(window.end())
                    ? Map.of() // final cut — every award already carries its final basis, no need to re-ask
                    : snapshotBasis(rule, window.start(), cut.cutEnd());

            for (CompetitiveCommissionAward award : awards) {
                CutResult result = settleAward(rule, award, cut, basisByPromoter, window, dryRun);
                if (result == CutResult.CREATED) {
                    created++;
                } else if (result == CutResult.SKIPPED_ZERO) {
                    skippedZero++;
                }
            }
        }
        return new CutOutcome(created, skippedZero);
    }

    private enum CutResult { CREATED, SKIPPED_ZERO, SKIPPED_ALREADY_PAID }

    private CutResult settleAward(CompetitiveCommissionRule rule, CompetitiveCommissionAward award, CutDue cut,
                                   Map<Long, Candidate> basisByPromoter, PeriodStrategies.Window window, boolean dryRun) {
        var existing = settlementRepository.findByRule_IdAndPromoter_IdAndPeriodStartAndCutKindAndCutSequence(
                rule.getId(), award.getPromoter().getId(), window.start(), cut.kind(), cut.sequence());
        if (existing.isPresent() && "PAID".equals(existing.get().getStatus())) {
            return CutResult.SKIPPED_ALREADY_PAID;
        }

        BigDecimal entitlementCumulative;
        BigDecimal basisCumulative = null;
        boolean isFinalMoment = cut.cutEnd().equals(window.end());
        if (isFinalMoment || award.getRewardType() == RewardType.FLAT) {
            entitlementCumulative = award.getAmount();
            basisCumulative = award.getBasisAmount();
        } else {
            Candidate candidate = basisByPromoter.get(award.getPromoter().getId());
            basisCumulative = candidate != null ? candidate.value() : BigDecimal.ZERO;
            entitlementCumulative = basisCumulative.multiply(award.getRewardPct())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            var position = award.getPosition();
            if (position.getRewardMinAmount() != null && entitlementCumulative.compareTo(position.getRewardMinAmount()) < 0) {
                entitlementCumulative = position.getRewardMinAmount();
            }
            if (position.getRewardMaxAmount() != null && entitlementCumulative.compareTo(position.getRewardMaxAmount()) > 0) {
                entitlementCumulative = position.getRewardMaxAmount();
            }
        }

        BigDecimal alreadyPaid = settlementRepository.sumPaidForRulePromoterPeriod(
                rule.getId(), award.getPromoter().getId(), window.start());
        BigDecimal amount = entitlementCumulative.subtract(alreadyPaid);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return CutResult.SKIPPED_ZERO;
        }
        if (dryRun) {
            return CutResult.CREATED;
        }

        CompetitiveCommissionAwardSettlement settlement = existing.orElseGet(CompetitiveCommissionAwardSettlement::new);
        settlement.setRule(rule);
        settlement.setAward(award);
        settlement.setPromoter(award.getPromoter());
        settlement.setPeriodStart(window.start());
        settlement.setPeriodEnd(window.end());
        settlement.setCutKind(cut.kind());
        settlement.setCutSequence(cut.sequence());
        settlement.setCutStart(cut.cutStart());
        settlement.setCutEnd(cut.cutEnd());
        settlement.setAwardPositionAtCut(award.getAwardPosition());
        settlement.setBasisAmountCumulative(basisCumulative);
        settlement.setEntitlementCumulative(entitlementCumulative);
        settlement.setAlreadyPaidAmount(alreadyPaid);
        settlement.setAmount(amount);
        settlement.setCurrency(award.getCurrency());
        settlement.setStatus("PENDING");
        settlementRepository.save(settlement);
        return CutResult.CREATED;
    }

    private Map<Long, Candidate> snapshotBasis(CompetitiveCommissionRule rule, LocalDate periodStart, LocalDate cutEnd) {
        CompetitiveMetricProvider provider = providerByMetric.get(rule.getMetric());
        if (provider == null) {
            return Map.of();
        }
        MetricScope scope = CompetitiveRuleWindowResolver.buildScope(rule);
        PeriodStrategies.Window narrowWindow = new PeriodStrategies.Window(periodStart, cutEnd);
        return provider.snapshot(narrowWindow, rule.getAchievementDateBasis(), scope).stream()
                .collect(Collectors.toMap(Candidate::promoterId, c -> c, (a, b) -> a));
    }

    /** One cut boundary landing exactly on {@code asOf} — {@code cutStart}/{@code cutEnd} are already clipped to the window. */
    private record CutDue(CutKind kind, int sequence, LocalDate cutStart, LocalDate cutEnd) {
    }

    private List<CutDue> resolveDueCuts(CompetitiveCommissionRule rule, PeriodStrategies.Window window, LocalDate asOf) {
        List<CutDue> due = new ArrayList<>();
        if (asOf.equals(window.end())) {
            due.add(new CutDue(CutKind.FINAL, 1, window.start(), window.end()));
        }
        due.addAll(dueCutsForAxis(CutKind.PARTIAL, rule.getPartialSettlementPeriodStrategy(),
                rule.getPartialSettlementPeriodAnchor(), window, asOf));
        if (rule.getRetroactiveSettlementPeriodStrategy() != rule.getPartialSettlementPeriodStrategy()) {
            due.addAll(dueCutsForAxis(CutKind.RETROACTIVE, rule.getRetroactiveSettlementPeriodStrategy(),
                    rule.getRetroactiveSettlementPeriodAnchor(), window, asOf));
        }
        return due;
    }

    private List<CutDue> dueCutsForAxis(CutKind kind, PeriodAxisStrategy strategy, Short anchor,
                                         PeriodStrategies.Window window, LocalDate asOf) {
        if (strategy == PeriodAxisStrategy.END_DATE) {
            return List.of(); // paid only at the FINAL cut, already covered above
        }
        List<PeriodCutCalculator.Cut> cuts = PeriodCutCalculator.cuts(strategy.name(), window.start(), window.end(), anchor);
        List<CutDue> due = new ArrayList<>();
        for (int i = 0; i < cuts.size(); i++) {
            PeriodCutCalculator.Cut cut = cuts.get(i);
            if (cut.end().equals(asOf) && !cut.end().equals(window.end())) {
                due.add(new CutDue(kind, i + 1, cut.start(), cut.end()));
            }
        }
        return due;
    }
}
