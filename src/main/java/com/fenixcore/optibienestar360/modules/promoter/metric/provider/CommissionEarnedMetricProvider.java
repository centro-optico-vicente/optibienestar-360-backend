package com.fenixcore.optibienestar360.modules.promoter.metric.provider;

import com.fenixcore.optibienestar360.core.util.AppTimeZone;
import com.fenixcore.optibienestar360.core.util.PeriodStrategies;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.AchievementDateBasis;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.CompetitiveMetric;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission;
import com.fenixcore.optibienestar360.modules.promoter.metric.CompetitiveMetricProvider;
import com.fenixcore.optibienestar360.modules.promoter.metric.CompetitiveRankingEngine.Candidate;
import com.fenixcore.optibienestar360.modules.promoter.metric.MetricEvent;
import com.fenixcore.optibienestar360.modules.promoter.metric.MetricScope;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code COMMISSION_EARNED}: rewards promoters on their own already-earned commissions
 * ({@code Commission.earnedAt}/{@code amount}) — unlike the payment families, there's no
 * achievement-date-basis choice here (a commission has exactly one earned moment).
 */
@Component
@RequiredArgsConstructor
public class CommissionEarnedMetricProvider implements CompetitiveMetricProvider {

    private final CommissionRepository commissionRepository;

    @Override
    public CompetitiveMetric metric() {
        return CompetitiveMetric.COMMISSION_EARNED;
    }

    @Override
    public boolean supportsEvents() {
        return true;
    }

    @Override
    public List<MetricEvent> events(PeriodStrategies.Window window, AchievementDateBasis basis, MetricScope scope) {
        Instant from = window.start().atStartOfDay(AppTimeZone.ZONE).toInstant();
        Instant to = window.end().plusDays(1).atStartOfDay(AppTimeZone.ZONE).toInstant();
        List<Commission> commissions = commissionRepository.findEarnedCandidatesInWindow(from, to,
                scope.includeSystemPromoters(),
                scope.promoterTypeIds().isEmpty() ? null : scope.promoterTypeIds(),
                scope.rankIds().isEmpty() ? null : scope.rankIds());
        List<MetricEvent> events = new ArrayList<>();
        for (Commission commission : commissions) {
            events.add(new MetricEvent(commission.getPromoter().getId(), commission.getAmount(), commission.getEarnedAt()));
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
            java.math.BigDecimal total = java.math.BigDecimal.ZERO;
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
}
