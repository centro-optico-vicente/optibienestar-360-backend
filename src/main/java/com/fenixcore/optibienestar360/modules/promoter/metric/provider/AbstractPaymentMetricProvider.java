package com.fenixcore.optibienestar360.modules.promoter.metric.provider;

import com.fenixcore.optibienestar360.core.util.AppTimeZone;
import com.fenixcore.optibienestar360.core.util.PeriodStrategies;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.AchievementDateBasis;
import com.fenixcore.optibienestar360.modules.promoter.metric.CompetitiveMetricProvider;
import com.fenixcore.optibienestar360.modules.promoter.metric.CompetitiveRankingEngine.Candidate;
import com.fenixcore.optibienestar360.modules.promoter.metric.MetricEvent;
import com.fenixcore.optibienestar360.modules.promoter.metric.MetricScope;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared plumbing for the 3 {@code Payment}-backed metric families (sales, collection, advance):
 * each candidate query casts a wide net across all 3 achievement-date-basis columns (JPQL can't
 * select a dynamic column), so this base class re-filters precisely on the rule's actual basis
 * in Java and turns the survivors into {@link MetricEvent}s. Concrete subclasses only supply the
 * candidate query and whether the metric counts occurrences or sums amounts.
 */
abstract class AbstractPaymentMetricProvider implements CompetitiveMetricProvider {

    @Override
    public boolean supportsEvents() {
        return true;
    }

    protected abstract List<Payment> fetchCandidates(Instant from, Instant to, boolean includeSystem,
                                                       Collection<Long> typeIds, Collection<Long> rankIds);

    /** {@code true}: each matching payment counts as 1. {@code false}: its {@code amount} is the value. */
    protected abstract boolean isCountMetric();

    @Override
    public List<MetricEvent> events(PeriodStrategies.Window window, AchievementDateBasis basis, MetricScope scope) {
        Instant from = window.start().atStartOfDay(AppTimeZone.ZONE).toInstant();
        Instant to = window.end().plusDays(1).atStartOfDay(AppTimeZone.ZONE).toInstant();
        List<Payment> candidates = fetchCandidates(from, to, scope.includeSystemPromoters(),
                nullIfEmpty(scope.promoterTypeIds()), nullIfEmpty(scope.rankIds()));

        List<MetricEvent> events = new ArrayList<>();
        for (Payment payment : candidates) {
            Instant achievedAt = basisInstant(payment, basis);
            if (achievedAt == null || achievedAt.isBefore(from) || !achievedAt.isBefore(to)) {
                continue; // outside the window on the rule's ACTUAL basis column
            }
            BigDecimal value = isCountMetric() ? BigDecimal.ONE : payment.getAmount();
            events.add(new MetricEvent(payment.getPromoter().getId(), value, achievedAt));
        }
        return events;
    }

    @Override
    public List<Candidate> snapshot(PeriodStrategies.Window window, AchievementDateBasis basis, MetricScope scope) {
        Map<Long, List<MetricEvent>> byPromoter = new LinkedHashMap<>();
        for (MetricEvent event : events(window, basis, scope)) {
            byPromoter.computeIfAbsent(event.promoterId(), k -> new ArrayList<>()).add(event);
        }
        List<Candidate> result = new ArrayList<>();
        for (Map.Entry<Long, List<MetricEvent>> entry : byPromoter.entrySet()) {
            BigDecimal total = BigDecimal.ZERO;
            Instant lastAchievedAt = null;
            for (MetricEvent event : entry.getValue()) {
                total = total.add(event.value());
                if (lastAchievedAt == null || event.achievedAt().isAfter(lastAchievedAt)) {
                    lastAchievedAt = event.achievedAt();
                }
            }
            result.add(new Candidate(entry.getKey(), total, lastAchievedAt, entry.getValue().size()));
        }
        return result;
    }

    private static Instant basisInstant(Payment payment, AchievementDateBasis basis) {
        return switch (basis) {
            case PAYMENT_DATE -> payment.getPaymentDate();
            case REGISTERED_AT -> payment.getReceivedAt();
            case APPROVED_AT -> payment.getReviewedAt() != null ? payment.getReviewedAt() : payment.getReceivedAt();
        };
    }

    private static Collection<Long> nullIfEmpty(Collection<Long> ids) {
        return ids == null || ids.isEmpty() ? null : ids;
    }
}
