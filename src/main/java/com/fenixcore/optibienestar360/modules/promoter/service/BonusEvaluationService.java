package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
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
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
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
                comp.amount(), rule.getRewardCurrency());
    }

    // ─── Window computation ───────────────────────────────────────────────────

    /**
     * Calendar bounds the rule evaluates over, containing {@code asOf}. We use
     * fixed calendar periods (no sliding windows) so awards line up with
     * accounting. Returns null when a CAMPAIGN has not started yet.
     */
    private static BonusWindow windowFor(CommissionBonusRule rule, LocalDate asOf) {
        return switch (rule.getWindowStrategy()) {
            case LIFETIME -> new BonusWindow(LIFETIME_START, asOf);
            case DAILY -> new BonusWindow(asOf, asOf);
            case WEEKLY -> new BonusWindow(
                    asOf.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)),
                    asOf.with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY)));
            case BIWEEKLY -> asOf.getDayOfMonth() <= 15
                    ? new BonusWindow(asOf.withDayOfMonth(1), asOf.withDayOfMonth(15))
                    : new BonusWindow(asOf.withDayOfMonth(16), asOf.with(TemporalAdjusters.lastDayOfMonth()));
            case MONTHLY -> new BonusWindow(
                    asOf.withDayOfMonth(1), asOf.with(TemporalAdjusters.lastDayOfMonth()));
            case QUARTERLY -> {
                LocalDate start = asOf.with(IsoFields.DAY_OF_QUARTER, 1);
                yield new BonusWindow(start, start.plusMonths(3).minusDays(1));
            }
            case SEMIANNUAL -> asOf.getMonthValue() <= 6
                    ? new BonusWindow(asOf.withDayOfYear(1), LocalDate.of(asOf.getYear(), 6, 30))
                    : new BonusWindow(LocalDate.of(asOf.getYear(), 7, 1),
                            asOf.with(TemporalAdjusters.lastDayOfYear()));
            case ANNUAL -> new BonusWindow(
                    asOf.withDayOfYear(1), asOf.with(TemporalAdjusters.lastDayOfYear()));
            case CAMPAIGN -> asOf.isBefore(rule.getCampaignStart())
                    ? null
                    : new BonusWindow(rule.getCampaignStart(), rule.getCampaignEnd());
        };
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
