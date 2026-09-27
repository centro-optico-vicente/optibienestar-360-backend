package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.core.util.AppTimeZone;
import com.fenixcore.optibienestar360.core.util.PeriodCutCalculator;
import com.fenixcore.optibienestar360.core.util.PeriodStrategies;
import com.fenixcore.optibienestar360.modules.currency.exception.NoExchangeRateAvailableException;
import com.fenixcore.optibienestar360.modules.currency.service.CurrencyConversionService;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentRepository;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule.AccrualMode;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule.BonusMetric;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule.RewardType;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule.WindowStrategy;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterBonusAward;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterBonusAward.AwardStatus;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterBonusAward.CutKind;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionBonusRuleRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterBonusAwardRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Fase B (hub plan competitive-commission-rules §12) — turns a bonus rule's
 * {@code partial_}/{@code final_}/{@code retroactive_settlement_period_strategy}
 * axes (V146, saved since Fase A but never read by anything) into real
 * settlement cuts, mirroring {@link CompetitiveCommissionSettlementService}'s
 * shape but writing directly onto {@link PromoterBonusAward} (no separate
 * settlements table — one cut IS one award row here).
 *
 * <p><b>Unified cumulative-netting formula</b> — every cut, for every reward
 * type, computes {@code entitlementCumulative(cutEnd)} and grants {@code
 * max(0, entitlement − alreadyGranted)}, where {@code alreadyGranted} sums
 * every other non-VOIDED row already persisted for the same {@code (rule,
 * promoter, windowStart)}. This collapses onto exactly today's PER_BLOCK/
 * THRESHOLD delta behavior for {@code FLAT} rewards (a completed block or a
 * crossed threshold never changes value, so once granted the formula nets to
 * zero on every later cut) while adding real support for {@code PERCENTAGE}
 * rewards, whose basis keeps growing after the metric threshold is first
 * crossed — exactly the gap H12/§12 called out. Per §12: {@code RETROACTIVE}
 * cuts only ever apply to {@code PERCENTAGE} rewards ("un bloque o umbral
 * pagado no cambia de valor" for FLAT).</p>
 *
 * <p><b>LIFETIME is the one strategy with no fixed close</b> — {@link
 * #windowFor} resolves its window as {@code [epoch, asOf]}, so every run's
 * "final" cut lands on a fresh {@code asOf}. Rather than colliding on the
 * natural key, {@link #settleCut} reuses the latest cut for that axis while
 * it's still {@code PENDING} (its {@code cutEnd}/{@code windowEnd} simply
 * advance) and only starts a new sequence once the previous one has been
 * {@code PAID} — so a lifetime total keeps accruing under a fresh row instead
 * of silently stopping once the first payout closes it out.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BonusSettlementCutService {

    private static final LocalDate LIFETIME_START = LocalDate.of(1970, 1, 1);

    private final CommissionBonusRuleRepository ruleRepository;
    private final PromoterBonusAwardRepository awardRepository;
    private final MemberRepository memberRepository;
    private final PromoterRepository promoterRepository;
    private final CommissionRepository commissionRepository;
    private final PaymentRepository paymentRepository;
    private final CurrencyConversionService currencyConversionService;

    public record CutOutcome(int granted, int skippedZero) {
        static final CutOutcome NONE = new CutOutcome(0, 0);
    }

    /**
     * Processes every cut of {@code rule} whose boundary falls on {@code
     * asOf}, for the accrual window containing it. Idempotent by {@code
     * uq_promoter_bonus_awards_cut} — running the same day twice nets to the
     * same result, and a {@code PAID} row is never recomputed or reopened.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CutOutcome executeCutForRule(UUID ruleUuid, LocalDate asOf, boolean dryRun) {
        CommissionBonusRule rule = ruleRepository.findByUuid(ruleUuid)
                .orElseThrow(() -> new NoSuchElementException("commission_bonus_rule.not_found"));
        Window window = windowFor(rule, asOf);
        if (window == null) {
            return CutOutcome.NONE; // e.g. a CAMPAIGN that hasn't started yet
        }
        List<CutDue> dueCuts = resolveDueCuts(rule, window, asOf);
        if (dueCuts.isEmpty()) {
            return CutOutcome.NONE;
        }

        int granted = 0, skippedZero = 0;
        for (CutDue cut : dueCuts) {
            for (MetricValue mv : metricValuesAsOf(rule, window.start(), cut.cutEnd())) {
                Promoter promoter = promoterRepository.findById(mv.promoterId()).orElse(null);
                if (promoter == null || !BonusEvaluationService.appliesToPromoterType(rule, promoter)) {
                    continue; // soft-deleted, or scoped to a different promoter type (V46)
                }
                SettleOutcome result = settleCut(rule, promoter, window, cut, mv.value(), dryRun);
                if (result.status() == CutResult.GRANTED) {
                    granted++;
                } else if (result.status() == CutResult.SKIPPED_ZERO) {
                    skippedZero++;
                }
            }
        }
        return new CutOutcome(granted, skippedZero);
    }

    /**
     * The manual "Evaluate now" admin trigger ({@code POST
     * /v1/admin/bonus-rules/evaluate}, {@link BonusEvaluationService#evaluate})
     * — forces a cumulative snapshot as of {@code asOf} regardless of whether a
     * real settlement-axis boundary lands there, sharing the exact same
     * entitlement/netting math and upsert-while-open target resolution as
     * {@link #executeCutForRule} (same treatment {@code LIFETIME} always gets,
     * generalized to every accrual strategy — this is an ad-hoc trigger, not a
     * scheduled cut, so there's no real boundary to key a fixed sequence off of).
     */
    @Transactional
    public ForcedOutcome forceEvaluateRule(CommissionBonusRule rule, LocalDate asOf, boolean dryRun) {
        Window window = windowFor(rule, asOf);
        if (window == null) {
            return ForcedOutcome.EMPTY;
        }
        LocalDate cutEnd = asOf.isAfter(window.end()) ? window.end() : asOf;
        CutDue forced = new CutDue(CutKind.FINAL, null, window.start(), cutEnd);

        int promotersAwarded = 0, totalBlocks = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (MetricValue mv : metricValuesAsOf(rule, window.start(), cutEnd)) {
            Promoter promoter = promoterRepository.findById(mv.promoterId()).orElse(null);
            if (promoter == null || !BonusEvaluationService.appliesToPromoterType(rule, promoter)) {
                continue;
            }
            SettleOutcome result = settleCut(rule, promoter, window, forced, mv.value(), dryRun);
            if (result.status() == CutResult.GRANTED) {
                promotersAwarded++;
                totalBlocks += result.blocks();
                totalAmount = totalAmount.add(result.amount());
            }
        }
        return new ForcedOutcome(promotersAwarded, totalBlocks, totalAmount);
    }

    public record ForcedOutcome(int promotersAwarded, int totalBlocks, BigDecimal amount) {
        static final ForcedOutcome EMPTY = new ForcedOutcome(0, 0, BigDecimal.ZERO);
    }

    private enum CutResult { GRANTED, SKIPPED_ZERO, SKIPPED_ALREADY_PAID }

    private record SettleOutcome(CutResult status, BigDecimal amount, int blocks) {
        static SettleOutcome of(CutResult status) {
            return new SettleOutcome(status, BigDecimal.ZERO, 0);
        }
    }

    private SettleOutcome settleCut(CommissionBonusRule rule, Promoter promoter, Window window,
                                     CutDue cut, BigDecimal metricValueAsOf, boolean dryRun) {
        short sequence;
        PromoterBonusAward existing;
        if (cut.sequence() != null) {
            sequence = cut.sequence();
            existing = awardRepository.findByRule_IdAndPromoter_IdAndWindowStartAndCutKindAndCutSequence(
                    rule.getId(), promoter.getId(), window.start(), cut.kind(), sequence).orElse(null);
        } else {
            // LIFETIME (and the forced ad-hoc trigger) — reuse the latest row
            // for this axis while it's still open.
            var latest = awardRepository.findTopByRule_IdAndPromoter_IdAndWindowStartAndCutKindOrderByCutSequenceDesc(
                    rule.getId(), promoter.getId(), window.start(), cut.kind());
            boolean latestOpen = latest.isPresent() && !AwardStatus.PAID.name().equals(latest.get().getStatus());
            int nextSequence = latest.map(a -> a.getCutSequence() + 1).orElse(1);
            sequence = latestOpen ? latest.get().getCutSequence() : (short) nextSequence;
            existing = latestOpen ? latest.get() : null;
        }
        if (existing != null && AwardStatus.PAID.name().equals(existing.getStatus())) {
            return SettleOutcome.of(CutResult.SKIPPED_ALREADY_PAID);
        }

        Entitlement entitlement = entitlementCumulative(rule, metricValueAsOf, promoter, window.start(), cut.cutEnd(), cut.kind());
        if (entitlement.amount().signum() <= 0) {
            return SettleOutcome.of(CutResult.SKIPPED_ZERO);
        }
        BigDecimal alreadyGranted = awardRepository.sumGrantedExcludingCut(
                rule.getId(), promoter.getId(), window.start(), cut.kind(), sequence);
        BigDecimal amount = entitlement.amount().subtract(alreadyGranted);
        if (amount.signum() <= 0) {
            return SettleOutcome.of(CutResult.SKIPPED_ZERO);
        }
        int blocks = estimateBlocks(rule, amount);
        if (dryRun) {
            return new SettleOutcome(CutResult.GRANTED, amount, blocks);
        }

        PromoterBonusAward award = existing != null ? existing : new PromoterBonusAward();
        award.setRule(rule);
        award.setPromoter(promoter);
        award.setWindowStart(window.start());
        award.setWindowEnd(window.end());
        award.setCutKind(cut.kind());
        award.setCutSequence(sequence);
        award.setCutStart(cut.cutStart());
        award.setCutEnd(cut.cutEnd());
        award.setBlocksAwarded(blocks);
        award.setMetricCount(metricValueAsOf.intValue());
        award.setRewardType(rule.getRewardType());
        award.setFlatAmount(rule.getRewardType() == RewardType.FLAT ? rule.getFlatAmount() : null);
        award.setRewardPct(rule.getRewardType() == RewardType.PERCENTAGE ? rule.getRewardPct() : null);
        award.setBasisAmount(entitlement.basis());
        award.setAmount(amount);
        award.setRewardCurrency(rule.getRewardCurrency());
        award.setRuleNameSnapshot(rule.getName());
        award.setEvaluatedAt(Instant.now());
        if (existing == null || award.getStatus() == null) {
            award.setStatus(AwardStatus.PENDING.name());
        }
        awardRepository.save(award);

        log.debug("Bonus cut: rule={} promoter={} kind={} seq={} cutEnd={} amount={} {}",
                rule.getName(), promoter.getReferralCode(), cut.kind(), sequence, cut.cutEnd(),
                amount, rule.getRewardCurrency().getCode());
        return new SettleOutcome(CutResult.GRANTED, amount, blocks);
    }

    /** Cosmetic only (reporting) — how many PER_BLOCK/FLAT blocks this delta represents. */
    private static int estimateBlocks(CommissionBonusRule rule, BigDecimal amount) {
        if (rule.getAccrual() == AccrualMode.PER_BLOCK && rule.getRewardType() == RewardType.FLAT
                && rule.getFlatAmount() != null && rule.getFlatAmount().signum() > 0) {
            return amount.divide(rule.getFlatAmount(), 0, RoundingMode.HALF_UP).intValue();
        }
        return 1;
    }

    // ─── Entitlement ──────────────────────────────────────────────────────────

    private record Entitlement(BigDecimal amount, BigDecimal basis) {
        static final Entitlement ZERO = new Entitlement(BigDecimal.ZERO, null);
    }

    /**
     * The cumulative amount this rule owes {@code promoter} as of {@code
     * cutEnd} — never scaled by "new blocks since last time"; {@link
     * #settleCut} nets the delta itself. {@code RETROACTIVE} cuts only ever
     * apply to {@code PERCENTAGE} rewards (§12) — a {@code FLAT} reward
     * resolves to zero there, same as if it were skipped entirely.
     */
    private Entitlement entitlementCumulative(CommissionBonusRule rule, BigDecimal metricValueAsOf,
                                               Promoter promoter, LocalDate windowStart, LocalDate cutEnd, CutKind cutKind) {
        boolean isCountMetric = rule.getMetric() != BonusMetric.AMOUNT_COLLECTED;
        BigDecimal threshold = isCountMetric
                ? BigDecimal.valueOf(rule.getThresholdCount())
                : rule.getThresholdAmount();
        if (metricValueAsOf.compareTo(threshold) < 0) {
            return Entitlement.ZERO;
        }
        if (rule.getRewardType() == RewardType.FLAT) {
            if (cutKind == CutKind.RETROACTIVE) {
                return Entitlement.ZERO;
            }
            if (rule.getAccrual() == AccrualMode.PER_BLOCK && isCountMetric) {
                BigDecimal totalBlocks = metricValueAsOf.divideToIntegralValue(threshold);
                return new Entitlement(rule.getFlatAmount().multiply(totalBlocks), null);
            }
            return new Entitlement(rule.getFlatAmount(), null);
        }
        // PERCENTAGE — a cut of the promoter's window commission earnings (count
        // metrics) or of the collected amount itself (AMOUNT_COLLECTED, the same
        // basis its own threshold is compared against).
        BigDecimal basis = isCountMetric
                ? commissionRepository.sumForPromoterInPeriod(promoter.getId(), windowStart, cutEnd)
                : metricValueAsOf;
        BigDecimal amount = basis.multiply(rule.getRewardPct()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return new Entitlement(amount, basis);
    }

    // ─── Metric values as of an arbitrary cut end ──────────────────────────────

    private record MetricValue(Long promoterId, BigDecimal value) {}

    private List<MetricValue> metricValuesAsOf(CommissionBonusRule rule, LocalDate windowStart, LocalDate cutEnd) {
        boolean includeSystem = rule.isIncludeSystemPromoters();
        if (rule.getMetric() == BonusMetric.NEW_SUBSCRIBERS) {
            return memberRepository.countNewSubscribersByPromoter(windowStart, cutEnd, includeSystem).stream()
                    .map(pc -> new MetricValue(pc.promoterId(), BigDecimal.valueOf(pc.count())))
                    .toList();
        }
        if (rule.getMetric() == BonusMetric.ACTIVE_SUBSCRIBERS) {
            // Snapshot metric ("current active") — the window only bounds cutEnd
            // implicitly through when this cut runs, same as BonusEvaluationService.
            return memberRepository.countActiveSubscribersByPromoter(includeSystem).stream()
                    .map(pc -> new MetricValue(pc.promoterId(), BigDecimal.valueOf(pc.count())))
                    .toList();
        }
        return amountCollectedAsOf(rule, windowStart, cutEnd);
    }

    /** {@code AMOUNT_COLLECTED} as of {@code cutEnd} — same per-payment conversion as {@code BonusEvaluationService}. */
    private List<MetricValue> amountCollectedAsOf(CommissionBonusRule rule, LocalDate windowStart, LocalDate cutEnd) {
        Instant from = windowStart.atStartOfDay(AppTimeZone.ZONE).toInstant();
        Instant to = cutEnd.plusDays(1).atStartOfDay(AppTimeZone.ZONE).toInstant();

        List<Payment> allPayments = new ArrayList<>();
        for (Promoter promoter : promoterRepository.findAll()) {
            if (!BonusEvaluationService.appliesToPromoterType(rule, promoter)) {
                continue;
            }
            allPayments.addAll(paymentRepository.findApprovedInForPromoterInWindow(promoter.getId(), from, to));
        }
        Map<Long, List<Payment>> byPromoterId = allPayments.stream()
                .collect(Collectors.groupingBy(p -> p.getPromoter().getId()));

        List<MetricValue> result = new ArrayList<>();
        for (Map.Entry<Long, List<Payment>> entry : byPromoterId.entrySet()) {
            BigDecimal total = BigDecimal.ZERO;
            for (Payment p : entry.getValue()) {
                try {
                    var conversion = currencyConversionService.convert(
                            p.getAmount(), p.getCurrency(), rule.getThresholdCurrency(), p.getPaymentDate());
                    total = total.add(conversion.convertedAmount());
                } catch (NoExchangeRateAvailableException ex) {
                    log.warn("Bonus rule {} — no exchange rate {}→{} for payment {}; excluded from AMOUNT_COLLECTED sum",
                            rule.getUuid(), p.getCurrency().getCode(), rule.getThresholdCurrency().getCode(), p.getUuid());
                }
            }
            result.add(new MetricValue(entry.getKey(), total));
        }
        return result;
    }

    // ─── Window + due-cuts resolution ──────────────────────────────────────────

    /** Inclusive calendar bounds of the accrual window containing {@code asOf}. */
    private record Window(LocalDate start, LocalDate end) {}

    /** One cut boundary landing exactly on {@code asOf}. {@code sequence == null} means "resolve dynamically" (LIFETIME only). */
    private record CutDue(CutKind kind, Short sequence, LocalDate cutStart, LocalDate cutEnd) {}

    private static Window windowFor(CommissionBonusRule rule, LocalDate asOf) {
        WindowStrategy strategy = rule.getAccrualPeriodStrategy();
        if (strategy == WindowStrategy.LIFETIME) {
            return new Window(LIFETIME_START, asOf);
        }
        if (strategy == WindowStrategy.CAMPAIGN) {
            LocalDate campaignStart = rule.getCampaignStart().toLocalDate();
            LocalDate campaignEnd = rule.getCampaignEnd().toLocalDate();
            return asOf.isBefore(campaignStart) ? null : new Window(campaignStart, campaignEnd);
        }
        PeriodStrategies.Window w = PeriodStrategies.window(strategy.name(), asOf, rule.getAccrualPeriodAnchor());
        return new Window(w.start(), w.end());
    }

    private List<CutDue> resolveDueCuts(CommissionBonusRule rule, Window window, LocalDate asOf) {
        List<CutDue> due = new ArrayList<>();
        if (rule.getAccrualPeriodStrategy() == WindowStrategy.LIFETIME) {
            // Never closes — every run is a fresh cumulative snapshot; the target
            // row (and its sequence) is resolved dynamically in settleCut.
            due.add(new CutDue(CutKind.FINAL, null, window.start(), asOf));
            return due;
        }
        if (asOf.equals(window.end())) {
            due.add(new CutDue(CutKind.FINAL, (short) 1, window.start(), window.end()));
        }
        due.addAll(dueCutsForAxis(CutKind.PARTIAL, rule.getPartialSettlementPeriodStrategy(),
                rule.getPartialSettlementPeriodAnchor(), window, asOf));
        if (rule.getRetroactiveSettlementPeriodStrategy() != rule.getPartialSettlementPeriodStrategy()) {
            due.addAll(dueCutsForAxis(CutKind.RETROACTIVE, rule.getRetroactiveSettlementPeriodStrategy(),
                    rule.getRetroactiveSettlementPeriodAnchor(), window, asOf));
        }
        return due;
    }

    private List<CutDue> dueCutsForAxis(CutKind kind, WindowStrategy strategy, Short anchor, Window window, LocalDate asOf) {
        List<PeriodCutCalculator.Cut> cuts = PeriodCutCalculator.cuts(strategy.name(), window.start(), window.end(), anchor);
        List<CutDue> due = new ArrayList<>();
        for (int i = 0; i < cuts.size(); i++) {
            PeriodCutCalculator.Cut cut = cuts.get(i);
            if (cut.end().equals(asOf) && !cut.end().equals(window.end())) {
                due.add(new CutDue(kind, (short) (i + 1), cut.start(), cut.end()));
            }
        }
        return due;
    }
}
