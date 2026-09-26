package com.fenixcore.optibienestar360.modules.promoter.metric.provider;

import com.fenixcore.optibienestar360.core.util.AppTimeZone;
import com.fenixcore.optibienestar360.core.util.PeriodStrategies;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterEnrollmentEventRow;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.AchievementDateBasis;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.CompetitiveMetric;
import com.fenixcore.optibienestar360.modules.promoter.metric.CompetitiveMetricProvider;
import com.fenixcore.optibienestar360.modules.promoter.metric.CompetitiveRankingEngine.Candidate;
import com.fenixcore.optibienestar360.modules.promoter.metric.MetricEvent;
import com.fenixcore.optibienestar360.modules.promoter.metric.MetricScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code NEW_SUBSCRIBERS}: one event per member enrollment (value 1). {@code
 * achievement_date_basis} doesn't apply — enrollment has a single date ({@code enrolledAt}),
 * always used regardless of the rule's configured basis.
 */
@Component
@RequiredArgsConstructor
public class NewSubscribersMetricProvider implements CompetitiveMetricProvider {

    private final MemberRepository memberRepository;

    @Override
    public CompetitiveMetric metric() {
        return CompetitiveMetric.NEW_SUBSCRIBERS;
    }

    @Override
    public boolean supportsEvents() {
        return true;
    }

    @Override
    public List<MetricEvent> events(PeriodStrategies.Window window, AchievementDateBasis basis, MetricScope scope) {
        List<MetricEvent> events = new ArrayList<>();
        for (PromoterEnrollmentEventRow row : fetchRows(window, scope)) {
            Instant achievedAt = row.enrolledAt().atStartOfDay(AppTimeZone.ZONE).toInstant();
            events.add(new MetricEvent(row.promoterId(), BigDecimal.ONE, achievedAt));
        }
        return events;
    }

    @Override
    public List<Candidate> snapshot(PeriodStrategies.Window window, AchievementDateBasis basis, MetricScope scope) {
        Map<Long, List<PromoterEnrollmentEventRow>> byPromoter = new LinkedHashMap<>();
        for (PromoterEnrollmentEventRow row : fetchRows(window, scope)) {
            byPromoter.computeIfAbsent(row.promoterId(), k -> new ArrayList<>()).add(row);
        }
        List<Candidate> result = new ArrayList<>();
        for (Map.Entry<Long, List<PromoterEnrollmentEventRow>> entry : byPromoter.entrySet()) {
            Instant lastAchievedAt = entry.getValue().stream()
                    .map(r -> r.enrolledAt().atStartOfDay(AppTimeZone.ZONE).toInstant())
                    .max(Instant::compareTo).orElse(null);
            result.add(new Candidate(entry.getKey(), BigDecimal.valueOf(entry.getValue().size()),
                    lastAchievedAt, entry.getValue().size()));
        }
        return result;
    }

    private List<PromoterEnrollmentEventRow> fetchRows(PeriodStrategies.Window window, MetricScope scope) {
        return memberRepository.findNewSubscriberEvents(window.start(), window.end(), scope.includeSystemPromoters(),
                scope.promoterTypeIds().isEmpty() ? null : scope.promoterTypeIds(),
                scope.rankIds().isEmpty() ? null : scope.rankIds());
    }
}
