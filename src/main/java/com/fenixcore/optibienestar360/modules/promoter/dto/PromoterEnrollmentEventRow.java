package com.fenixcore.optibienestar360.modules.promoter.dto;

import java.time.LocalDate;

/**
 * One member enrollment, raw — the input row for the {@code NEW_SUBSCRIBERS} competitive metric
 * provider, which folds these into events (FIRST_TO_REACH) or a per-promoter snapshot (RANKING).
 */
public record PromoterEnrollmentEventRow(Long promoterId, LocalDate enrolledAt) {
}
