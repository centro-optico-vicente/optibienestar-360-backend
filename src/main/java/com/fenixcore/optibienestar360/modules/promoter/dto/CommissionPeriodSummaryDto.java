package com.fenixcore.optibienestar360.modules.promoter.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row of a promoter's monthly commission history — the body of
 * {@code GET /v1/admin/promoters/{uuid}/commissions/summary}. Sourced
 * directly from the {@code commission_period_summary} DB view (V42).
 */
public record CommissionPeriodSummaryDto(
        String periodStrategy,
        LocalDate periodStart,
        LocalDate periodEnd,
        long commissionCount,
        BigDecimal totalAmount,
        String currency
) {}
