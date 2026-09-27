package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.modules.promoter.dto.BonusEvaluationResponse;
import com.fenixcore.optibienestar360.modules.promoter.dto.BonusEvaluationResponse.RuleOutcome;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionBonusRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The manual "Evaluate now" admin trigger (v2 PDF #5, {@code POST
 * /v1/admin/bonus-rules/evaluate}) — a thin wrapper kept for its stable
 * {@link BonusEvaluationResponse} shape (unchanged since before Fase B). The
 * actual entitlement/netting math and the scheduled {@code
 * BONUS_SETTLEMENT_CUT} job both live in {@link BonusSettlementCutService}
 * (hub plan competitive-commission-rules §12) — this forces a cumulative
 * snapshot as of {@code asOf} for every active rule via {@link
 * BonusSettlementCutService#forceEvaluateRule}, exactly like that service's
 * own {@code LIFETIME} handling (an ad-hoc trigger has no real settlement-axis
 * boundary to key a fixed cut sequence off of, so it always reuses the latest
 * still-open row for the rule/promoter/window).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BonusEvaluationService {

    private static final String DEFAULT_CURRENCY = "USD";

    private final CommissionBonusRuleRepository ruleRepository;
    private final BonusSettlementCutService settlementCutService;

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
            var outcome = settlementCutService.forceEvaluateRule(rule, reference, dryRun);
            perRule.add(new RuleOutcome(rule.getUuid(), rule.getName(),
                    rule.getMetric().name(), rule.getAccrual().name(),
                    outcome.promotersAwarded(), outcome.totalBlocks(), outcome.amount()));
            awardsCreated += outcome.promotersAwarded();
            totalAmount = totalAmount.add(outcome.amount());
        }

        log.info("BONUS_EVALUATION asOf={} dryRun={} rules={} awards={} total={}",
                reference, dryRun, rules.size(), awardsCreated, totalAmount);

        return new BonusEvaluationResponse(reference, dryRun, rules.size(), awardsCreated,
                totalAmount, DEFAULT_CURRENCY, Instant.now(), perRule);
    }

    /**
     * A rule scoped to one or more promoter types (V46/V137, empty set = applies
     * to everyone) only grants to promoters of one of those types — this is an
     * eligibility filter, not a "pick one rule" precedence: unlike commission
     * tiers, bonus rules are independent and combinable, so a type-scoped rule
     * and a generic rule can both grant to the same promoter in the same window.
     *
     * <p>Package-visible — also used by {@link BonusSettlementCutService}.</p>
     */
    static boolean appliesToPromoterType(CommissionBonusRule rule, Promoter promoter) {
        if (rule.getPromoterTypes().isEmpty()) {
            return true;
        }
        return promoter.getPromoterType() != null
                && rule.getPromoterTypes().stream()
                        .anyMatch(pt -> pt.getId().equals(promoter.getPromoterType().getId()));
    }
}
