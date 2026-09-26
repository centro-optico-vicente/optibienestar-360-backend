package com.fenixcore.optibienestar360.modules.promoter.metric;

import com.fenixcore.optibienestar360.core.util.PeriodStrategies;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.AchievementDateBasis;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.CompetitiveMetric;

import java.util.List;

/**
 * One data source for a {@link CompetitiveMetric} — sales, collections, enrollments, etc. Each
 * concrete metric (both the {@code _COUNT} and {@code _AMOUNT} flavor of a family) gets its own
 * provider bean; the evaluation service (Phase 2b) looks providers up by {@link #metric()}.
 */
public interface CompetitiveMetricProvider {

    CompetitiveMetric metric();

    /** {@code true} unless the metric is a point-in-time snapshot with no "moment it happened" (e.g. ACTIVE_SUBSCRIBERS). */
    boolean supportsEvents();

    /** Raw, per-transaction events inside {@code window} — required for FIRST_TO_REACH rules. */
    List<MetricEvent> events(PeriodStrategies.Window window, AchievementDateBasis basis, MetricScope scope);

    /** One aggregated row per promoter with any activity in {@code window} — used by RANKING rules. */
    List<CompetitiveRankingEngine.Candidate> snapshot(PeriodStrategies.Window window, AchievementDateBasis basis, MetricScope scope);
}
