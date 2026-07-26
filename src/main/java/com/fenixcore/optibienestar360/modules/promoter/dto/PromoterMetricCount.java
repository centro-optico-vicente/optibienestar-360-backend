package com.fenixcore.optibienestar360.modules.promoter.dto;

/**
 * Grouped subscriber count per promoter — the projection the bonus engine
 * consumes ({@code metric} count of one rule, one row per qualifying promoter).
 * A JPQL constructor-expression target, so the count reaches the engine in a
 * single grouped query instead of N per-promoter round-trips.
 */
public record PromoterMetricCount(Long promoterId, Long count) {
}
