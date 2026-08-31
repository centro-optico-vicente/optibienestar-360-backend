package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fenixcore.optibienestar360.core.display.Display;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row of a promoter's monthly commission history — the body of
 * {@code GET /v1/admin/promoters/{uuid}/commissions/summary}. Sourced
 * directly from the {@code commission_period_summary} DB view (V42).
 *
 * <p>Scalars carry a localized {@code _Display} sibling (ADR 0014).</p>
 */
public record CommissionPeriodSummaryDto(
        @Display(Display.Kind.ENUM) String periodStrategy,
        @Display(Display.Kind.DATE) LocalDate periodStart,
        @Display(Display.Kind.DATE) LocalDate periodEnd,
        long commissionCount,
        @Display(Display.Kind.MONEY) BigDecimal totalAmount,
        String currency
) {}
