package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

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
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CommissionPayoutResponse(
        LocalDate periodStart,
        LocalDate periodEnd,
        String payoutReference,
        boolean dryRun,

        int totalPromoters,
        int totalCommissions,
        BigDecimal totalAmount,
        String currency,
        Instant executedAt,

        List<PromoterPayoutSummary> perPromoter
) {

    public record PromoterPayoutSummary(
            UUID promoterUuid,
            String promoterCode,
            String promoterDisplayName,
            int commissionCount,
            BigDecimal totalAmount,
            String currency,
            String csv,
            boolean emailDispatched,
            String emailFailureReason
    ) {}
}
