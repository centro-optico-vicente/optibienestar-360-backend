package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.core.util.AppTimeZone;
import com.fenixcore.optibienestar360.core.util.PeriodStrategies;
import com.fenixcore.optibienestar360.modules.currency.exception.NoExchangeRateAvailableException;
import com.fenixcore.optibienestar360.modules.currency.service.CurrencyConversionService;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentRepository;
import com.fenixcore.optibienestar360.modules.promoter.dto.BonusEvaluationResponse;
import com.fenixcore.optibienestar360.modules.promoter.dto.BonusEvaluationResponse.RuleOutcome;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterMetricCount;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule.AccrualMode;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule.BonusMetric;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule.RewardType;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule.WindowStrategy;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterBonusAward;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterBonusAward.AwardStatus;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionBonusRuleRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterBonusAwardRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The automated bonus/awards engine (v2 PDF #5). Singular service name — it owns
 * the one operation "given a reference date, grant every bonus the active rules
 * earn". Rule CRUD lives in {@code BonusRulesService}; the read queues live in
 * {@code BonusAwardsService}.
 *
 * <p>For each active {@link CommissionBonusRule} it computes the rule's window,
 * counts the metric per promoter in one grouped query, and grants awards:</p>
 * <ul>
 *   <li><b>PER_BLOCK</b> — one FLAT amount per full block of {@code thresholdCount}
 *       (e.g. 523 new @ 500 → 1 block; at 1000 → a 2nd). Re-runs grant only the
 *       positive delta of blocks not yet awarded (per window, or cumulatively for
 *       LIFETIME), so the engine is idempotent.</li>
 *   <li><b>THRESHOLD</b> — a single award once the count reaches
 *       {@code thresholdCount}; a FLAT amount, or a percentage of the promoter's
 *       window commission earnings. Once per window (or once ever for LIFETIME).</li>
 * </ul>
 *
 * <p>Rules are combinable and several promoters may each win several awards in a
 * period — the loop is independent per rule and per promoter.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BonusEvaluationService {

    /** Cumulative window lower bound — well before any enrollment. */
    private static final LocalDate LIFETIME_START = LocalDate.of(1970, 1, 1);

    private static final String DEFAULT_CURRENCY = "USD";

    private final CommissionBonusRuleRepository ruleRepository;
    private final PromoterBonusAwardRepository awardRepository;
    private final MemberRepository memberRepository;
    private final PromoterRepository promoterRepository;
    private final CommissionRepository commissionRepository;
    private final PaymentRepository paymentRepository;
    private final CurrencyConversionService currencyConversionService;

    /**
     * Evaluates every active rule against {@code asOf} and grants the awards
     * earned. {@code dryRun=true} computes + reports without persisting.
     */
    @Transactional
    public BonusEvaluationResponse evaluate(LocalDate asOf, boolean dryRun) {
        LocalDate reference = asOf != null ? asOf : LocalDate.now();
        List<CommissionBonusRule> rules = ruleRepository.findByActiveTrue();

        List<RuleOutcome> perRule = new ArrayList<>();
        int awardsCreated = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (CommissionBonusRule rule : rules) {
            BonusWindow window = windowFor(rule, reference);
            if (window == null) {
                // e.g. a CAMPAIGN that has not started yet as of the reference date.
                perRule.add(emptyOutcome(rule));
                continue;
            }

            if (rule.getMetric() == BonusMetric.AMOUNT_COLLECTED) {
                RuleOutcome outcome = evaluateAmountCollectedRule(rule, window, dryRun);
                perRule.add(outcome);
                awardsCreated += outcome.promotersAwarded();
                totalAmount = totalAmount.add(outcome.amount());
                continue;
            }

            List<PromoterMetricCount> counts = metricCounts(rule, window);
            Map<Long, Promoter> promoters = loadPromoters(counts);

            int promotersAwarded = 0;
            int blocks = 0;
            BigDecimal ruleAmount = BigDecimal.ZERO;

            for (PromoterMetricCount pc : counts) {
                Promoter promoter = promoters.get(pc.promoterId());
                if (promoter == null) {
                    continue;   // soft-deleted between the group query and the load
                }
                if (!appliesToPromoterType(rule, promoter)) {
                    continue;   // rule is scoped to a different promoter type (V46)
                }
                AwardComputation comp = computeAward(rule, window, promoter, pc.count().intValue());
                if (comp == null) {
                    continue;
                }
                if (!dryRun) {
                    persist(rule, window, promoter, comp);
                }
                promotersAwarded++;
                blocks += comp.blocks();
                ruleAmount = ruleAmount.add(comp.amount());
                awardsCreated++;
                totalAmount = totalAmount.add(comp.amount());
            }

            perRule.add(new RuleOutcome(rule.getUuid(), rule.getName(),
                    rule.getMetric().name(), rule.getAccrual().name(),
                    promotersAwarded, blocks, ruleAmount));
        }

        log.info("BONUS_EVALUATION asOf={} dryRun={} rules={} awards={} total={}",
                reference, dryRun, rules.size(), awardsCreated, totalAmount);

        return new BonusEvaluationResponse(reference, dryRun, rules.size(), awardsCreated,
                totalAmount, DEFAULT_CURRENCY, Instant.now(), perRule);
    }

    // ─── Metric counting ──────────────────────────────────────────────────────

    private List<PromoterMetricCount> metricCounts(CommissionBonusRule rule, BonusWindow window) {
        boolean includeSystem = rule.isIncludeSystemPromoters();
        if (rule.getMetric() == BonusMetric.NEW_SUBSCRIBERS) {
            return memberRepository.countNewSubscribersByPromoter(window.start(), window.end(), includeSystem);
        }
        // ACTIVE_SUBSCRIBERS is a snapshot ("current active"); the window only
        // governs award dedup, not which members are counted.
        return memberRepository.countActiveSubscribersByPromoter(includeSystem);
    }

    /**
     * A rule scoped to one or more promoter types (V46/V137, empty set = applies
     * to everyone) only grants to promoters of one of those types — this is an
     * eligibility filter, not a "pick one rule" precedence: unlike commission
     * tiers, bonus rules are independent and combinable, so a type-scoped rule
     * and a generic rule can both grant to the same promoter in the same window.
     */
    private static boolean appliesToPromoterType(CommissionBonusRule rule, Promoter promoter) {
        if (rule.getPromoterTypes().isEmpty()) {
            return true;
        }
        return promoter.getPromoterType() != null
                && rule.getPromoterTypes().stream()
                        .anyMatch(pt -> pt.getId().equals(promoter.getPromoterType().getId()));
    }

    /**
     * {@code AMOUNT_COLLECTED} evaluation (I-BE, hub plan Part I) — diverges from
     * the count-based metrics above: instead of grouping a member-count query, it
     * sums each promoter's own APPROVED {@code direction=IN} {@link Payment}s in
     * the window, converting every payment to {@link CommissionBonusRule#getThresholdCurrency()}
     * via {@link CurrencyConversionService#convert}. A payment with no exchange
     * rate available for its pair is excluded from the sum and logged — never
     * blocks the whole evaluation (same degrade-gracefully policy {@code
     * PaymentsService.snapshotExchangeRate} already uses). Threshold semantics are
     * "minimum" ({@code total >= thresholdAmount}), same {@code >=} the count
     * metrics use — awarded once per window (or once ever for LIFETIME), same
     * dedup {@link #computeAward}'s THRESHOLD branch uses via {@link
     * PromoterBonusAwardRepository#existsActiveInWindow}/{@code
     * existsActiveByRuleAndPromoter}.
     */
    private RuleOutcome evaluateAmountCollectedRule(CommissionBonusRule rule, BonusWindow window, boolean dryRun) {
        boolean lifetime = rule.getWindowStrategy() == WindowStrategy.LIFETIME;
        Instant from = window.start().atStartOfDay(AppTimeZone.ZONE).toInstant();
        Instant to = window.end().plusDays(1).atStartOfDay(AppTimeZone.ZONE).toInstant();

        // Distinct promoters with at least one attributed IN payment in the window.
        java.util.List<Payment> allPayments = new java.util.ArrayList<>();
        for (Promoter promoter : promoterRepository.findAll()) {
            if (!appliesToPromoterType(rule, promoter)) {
                continue;
            }
            allPayments.addAll(paymentRepository.findApprovedInForPromoterInWindow(promoter.getId(), from, to));
        }
        Map<Long, java.util.List<Payment>> byPromoterId = allPayments.stream()
                .collect(Collectors.groupingBy(p -> p.getPromoter().getId()));

        int promotersAwarded = 0;
        BigDecimal ruleAmount = BigDecimal.ZERO;

        for (Map.Entry<Long, java.util.List<Payment>> entry : byPromoterId.entrySet()) {
            BigDecimal total = BigDecimal.ZERO;
            Promoter promoter = null;
            for (Payment p : entry.getValue()) {
                promoter = p.getPromoter();
                try {
                    var conversion = currencyConversionService.convert(
                            p.getAmount(), p.getCurrency(), rule.getThresholdCurrency(), p.getPaymentDate());
                    total = total.add(conversion.convertedAmount());
                } catch (NoExchangeRateAvailableException ex) {
                    log.warn("Bonus rule {} — no exchange rate {}→{} for payment {}; excluded from AMOUNT_COLLECTED sum",
                            rule.getUuid(), p.getCurrency().getCode(), rule.getThresholdCurrency().getCode(), p.getUuid());
                }
            }
            if (promoter == null || total.compareTo(rule.getThresholdAmount()) < 0) {
                continue;
            }
            boolean already = lifetime
                    ? awardRepository.existsActiveByRuleAndPromoter(rule.getId(), promoter.getId())
                    : awardRepository.existsActiveInWindow(rule.getId(), promoter.getId(), window.start(), window.end());
            if (already) {
                continue;
            }

            BigDecimal amount = rule.getRewardType() == RewardType.FLAT
                    ? rule.getFlatAmount()
                    : total.multiply(rule.getRewardPct()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            if (amount == null || amount.signum() <= 0) {
                continue;
            }

            if (!dryRun) {
                PromoterBonusAward award = new PromoterBonusAward();
                award.setRule(rule);
                award.setPromoter(promoter);
                award.setWindowStart(window.start());
                award.setWindowEnd(window.end());
                award.setBlocksAwarded(1);
                award.setMetricCount(total.intValue());
                award.setRewardType(rule.getRewardType());
                award.setFlatAmount(rule.getRewardType() == RewardType.FLAT ? rule.getFlatAmount() : null);
                award.setRewardPct(rule.getRewardType() == RewardType.PERCENTAGE ? rule.getRewardPct() : null);
                award.setBasisAmount(total);
                award.setAmount(amount);
                award.setRewardCurrency(rule.getRewardCurrency());
                award.setRuleNameSnapshot(rule.getName());
                award.setEvaluatedAt(Instant.now());
                award.setStatus(AwardStatus.PENDING.name());
                awardRepository.save(award);
            }

            promotersAwarded++;
            ruleAmount = ruleAmount.add(amount);
        }

        return new RuleOutcome(rule.getUuid(), rule.getName(),
                rule.getMetric().name(), rule.getAccrual().name(), promotersAwarded, promotersAwarded, ruleAmount);
    }

    private Map<Long, Promoter> loadPromoters(List<PromoterMetricCount> counts) {
        Set<Long> ids = counts.stream().map(PromoterMetricCount::promoterId).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return promoterRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Promoter::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    // ─── Award computation ────────────────────────────────────────────────────

    /**
     * Decide what (if anything) to grant this promoter for this rule. Returns
     * null when the promoter has not crossed a new block / the threshold, or when
     * a percentage reward resolves to zero (no window earnings to apply it to).
     */
    private AwardComputation computeAward(CommissionBonusRule rule, BonusWindow window,
                                          Promoter promoter, int count) {
        boolean lifetime = rule.getWindowStrategy() == WindowStrategy.LIFETIME;
        int threshold = rule.getThresholdCount();

        int blocks;
        if (rule.getAccrual() == AccrualMode.PER_BLOCK) {
            int totalBlocks = count / threshold;
            if (totalBlocks <= 0) {
                return null;
            }
            long already = lifetime
                    ? awardRepository.sumBlocksAwardedLifetime(rule.getId(), promoter.getId())
                    : awardRepository.sumBlocksAwardedInWindow(rule.getId(), promoter.getId(),
                            window.start(), window.end());
            blocks = totalBlocks - (int) already;
            if (blocks <= 0) {
                return null;
            }
        } else { // THRESHOLD
            if (count < threshold) {
                return null;
            }
            boolean already = lifetime
                    ? awardRepository.existsActiveByRuleAndPromoter(rule.getId(), promoter.getId())
                    : awardRepository.existsActiveInWindow(rule.getId(), promoter.getId(),
                            window.start(), window.end());
            if (already) {
                return null;
            }
            blocks = 1;
        }

        BigDecimal basis = null;
        BigDecimal amount;
        if (rule.getRewardType() == RewardType.FLAT) {
            // FLAT scales with blocks for PER_BLOCK, single flat for THRESHOLD.
            amount = rule.getFlatAmount().multiply(BigDecimal.valueOf(blocks));
        } else { // PERCENTAGE — a cut of the promoter's window commission earnings.
            basis = commissionRepository.sumForPromoterInPeriod(
                    promoter.getId(), window.start(), window.end());
            amount = basis.multiply(rule.getRewardPct()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        }

        if (amount == null || amount.signum() <= 0) {
            return null;   // the DB CHECK requires amount > 0; nothing to grant
        }
        return new AwardComputation(blocks, count, amount, basis);
    }

    private void persist(CommissionBonusRule rule, BonusWindow window,
                         Promoter promoter, AwardComputation comp) {
        PromoterBonusAward award = new PromoterBonusAward();
        award.setRule(rule);
        award.setPromoter(promoter);
        award.setWindowStart(window.start());
        award.setWindowEnd(window.end());
        award.setBlocksAwarded(comp.blocks());
        award.setMetricCount(comp.count());
        award.setRewardType(rule.getRewardType());
        award.setFlatAmount(rule.getRewardType() == RewardType.FLAT ? rule.getFlatAmount() : null);
        award.setRewardPct(rule.getRewardType() == RewardType.PERCENTAGE ? rule.getRewardPct() : null);
        award.setBasisAmount(comp.basis());
        award.setAmount(comp.amount());
        award.setRewardCurrency(rule.getRewardCurrency());
        award.setRuleNameSnapshot(rule.getName());
        award.setEvaluatedAt(Instant.now());
        award.setStatus(AwardStatus.PENDING.name());
        awardRepository.save(award);

        log.debug("Bonus award: rule={} promoter={} blocks={} amount={} {}",
                rule.getName(), promoter.getReferralCode(), comp.blocks(),
                comp.amount(), rule.getRewardCurrency().getCode());
    }

    // ─── Window computation ───────────────────────────────────────────────────

    /**
     * Calendar bounds the rule evaluates over, containing {@code asOf}. The seven
     * fixed calendar strategies delegate to the shared {@link PeriodStrategies}
     * (no sliding windows — awards line up with accounting); LIFETIME and CAMPAIGN
     * are bonus-specific. Returns null when a CAMPAIGN has not started yet.
     */
    private static BonusWindow windowFor(CommissionBonusRule rule, LocalDate asOf) {
        WindowStrategy strategy = rule.getWindowStrategy();
        if (strategy == WindowStrategy.LIFETIME) {
            return new BonusWindow(LIFETIME_START, asOf);
        }
        if (strategy == WindowStrategy.CAMPAIGN) {
            LocalDate campaignStart = rule.getCampaignStart().toLocalDate();
            LocalDate campaignEnd = rule.getCampaignEnd().toLocalDate();
            return asOf.isBefore(campaignStart)
                    ? null
                    : new BonusWindow(campaignStart, campaignEnd);
        }
        PeriodStrategies.Window w = PeriodStrategies.window(strategy.name(), asOf);
        return new BonusWindow(w.start(), w.end());
    }

    private static RuleOutcome emptyOutcome(CommissionBonusRule rule) {
        return new RuleOutcome(rule.getUuid(), rule.getName(),
                rule.getMetric().name(), rule.getAccrual().name(), 0, 0, BigDecimal.ZERO);
    }

    /** Inclusive calendar bounds of an evaluation window. */
    private record BonusWindow(LocalDate start, LocalDate end) {}

    /** What a single (rule, promoter) evaluation decided to grant. */
    private record AwardComputation(int blocks, int count, BigDecimal amount, BigDecimal basis) {}
}
