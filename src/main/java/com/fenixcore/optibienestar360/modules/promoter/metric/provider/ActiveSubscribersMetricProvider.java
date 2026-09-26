package com.fenixcore.optibienestar360.modules.promoter.metric.provider;

import com.fenixcore.optibienestar360.core.util.PeriodStrategies;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterMetricCount;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.AchievementDateBasis;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.CompetitiveMetric;
import com.fenixcore.optibienestar360.modules.promoter.metric.CompetitiveMetricProvider;
import com.fenixcore.optibienestar360.modules.promoter.metric.CompetitiveRankingEngine.Candidate;
import com.fenixcore.optibienestar360.modules.promoter.metric.MetricEvent;
import com.fenixcore.optibienestar360.modules.promoter.metric.MetricScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * {@code ACTIVE_SUBSCRIBERS}: a point-in-time headcount, not an event stream — there's no single
 * "moment" a promoter's portfolio became this size, so this metric only supports RANKING
 * ({@link #supportsEvents()} = {@code false}; {@code CompetitiveCommissionRulesService} rejects it
 * for FIRST_TO_REACH). The snapshot is taken as of {@code window.end()}; {@code achievedAt} is left
 * {@code null} (no event to point to) — the ranking engine's tiebreak falls through to transaction
 * count, then promoter id.
 */
@Component
@RequiredArgsConstructor
public class ActiveSubscribersMetricProvider implements CompetitiveMetricProvider {

    private final MemberRepository memberRepository;

    @Override
    public CompetitiveMetric metric() {
        return CompetitiveMetric.ACTIVE_SUBSCRIBERS;
    }

    @Override
    public boolean supportsEvents() {
        return false;
    }

    @Override
    public List<MetricEvent> events(PeriodStrategies.Window window, AchievementDateBasis basis, MetricScope scope) {
        throw new UnsupportedOperationException("competitive_metric_provider.snapshot_only");
    }

    @Override
    public List<Candidate> snapshot(PeriodStrategies.Window window, AchievementDateBasis basis, MetricScope scope) {
        List<PromoterMetricCount> rows = memberRepository.countActiveSubscribersByPromoterScoped(
                scope.includeSystemPromoters(),
                scope.promoterTypeIds().isEmpty() ? null : scope.promoterTypeIds(),
                scope.rankIds().isEmpty() ? null : scope.rankIds());
        return rows.stream()
                .map(row -> new Candidate(row.promoterId(), BigDecimal.valueOf(row.count()), null, row.count().intValue()))
                .toList();
    }
}
