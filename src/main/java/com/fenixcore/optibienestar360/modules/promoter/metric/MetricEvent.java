package com.fenixcore.optibienestar360.modules.promoter.metric;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One raw, per-transaction occurrence of a competitive metric — the increment a single sale,
 * payment or enrollment contributes. {@link CompetitiveRankingEngine#firstToReach} folds a
 * promoter's events (ordered by {@code achievedAt}) into a single {@link CompetitiveRankingEngine.Candidate}
 * at the moment their cumulative value first meets the rule's threshold. {@code achievedAt} is
 * already resolved to whichever {@code AchievementDateBasis} the rule configured — never "now",
 * never the job's own clock.
 */
public record MetricEvent(Long promoterId, BigDecimal value, Instant achievedAt) {
}
