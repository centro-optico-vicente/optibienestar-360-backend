package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.core.util.PeriodStrategies;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule;
import com.fenixcore.optibienestar360.modules.promoter.metric.MetricScope;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Shared helpers between {@link CompetitiveCommissionEvaluationService} and
 * {@link CompetitiveCommissionSettlementService} — resolving a rule's accrual window and its
 * promoter scope is identical in both, and duplicating it would risk the two services silently
 * drifting apart on what "the period" or "who's eligible" means.
 */
final class CompetitiveRuleWindowResolver {

    private CompetitiveRuleWindowResolver() {
    }

    /** The accrual window containing {@code asOf} — the rule's own {@code starts_at..ends_at} when the axis is {@code END_DATE}. */
    static PeriodStrategies.Window resolveWindow(CompetitiveCommissionRule rule, java.time.LocalDate asOf) {
        if (rule.getAccrualPeriodStrategy() == CompetitiveCommissionRule.PeriodAxisStrategy.END_DATE) {
            return new PeriodStrategies.Window(rule.getStartsAt().toLocalDate(), rule.getEndsAt().toLocalDate());
        }
        return PeriodStrategies.window(rule.getAccrualPeriodStrategy().name(), asOf, rule.getAccrualPeriodAnchor());
    }

    static MetricScope buildScope(CompetitiveCommissionRule rule) {
        Set<Long> typeIds = rule.getPromoterTypes().stream().map(t -> t.getId()).collect(Collectors.toCollection(HashSet::new));
        Set<Long> rankIds = rule.getRanks().stream().map(r -> r.getId()).collect(Collectors.toCollection(HashSet::new));
        return new MetricScope(typeIds, rankIds, rule.isIncludeSystemPromoters());
    }
}
