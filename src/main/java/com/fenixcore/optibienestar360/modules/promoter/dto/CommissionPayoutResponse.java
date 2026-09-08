package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fenixcore.optibienestar360.core.display.Display;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Response body for {@code POST /v1/admin/commissions/payout}. Top-level
 * envelope carries the global counters; per-promoter breakdown lets the
 * admin see exactly how much went to whom and re-download the CSV that
 * was emailed.
 *
 * <p>Scalars carry a localized {@code _Display} sibling (ADR 0014).</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CommissionPayoutResponse(
        @Display(Display.Kind.DATE) LocalDate periodStart,
        @Display(Display.Kind.DATE) LocalDate periodEnd,
        String payoutReference,
        @Display(Display.Kind.BOOLEAN) boolean dryRun,

        int totalPromoters,
        /** Every settled line across all promoters — commissions + hierarchy overrides + retroactive top-ups (V105/PR4). */
        int totalCommissions,
        @Display(Display.Kind.MONEY) BigDecimal totalAmount,
        String currency,
        @Display(Display.Kind.DATETIME) Instant executedAt,

        List<PromoterPayoutSummary> perPromoter
) {

    public record PromoterPayoutSummary(
            UUID promoterUuid,
            String promoterCode,
            String promoterDisplayName,
            /** Count of every settled line this batch — direct commissions + hierarchy overrides + retroactive top-ups combined (V105/PR4), not just commissions despite the name. */
            int commissionCount,
            @Display(Display.Kind.MONEY) BigDecimal totalAmount,
            String currency,
            String csv,
            @Display(Display.Kind.BOOLEAN) boolean emailDispatched,
            String emailFailureReason
    ) {}
}
