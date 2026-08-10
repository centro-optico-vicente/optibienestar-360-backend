package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Response body for {@code POST /v1/admin/commissions/re-rate}. Top-level
 * envelope carries the global counters; per-promoter breakdown shows the
 * inscription count that decided the band and how much each promoter's
 * PENDING commissions moved by.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CommissionReRatingResponse(
        LocalDate periodStart,
        LocalDate periodEnd,
        boolean dryRun,

        int totalPromoters,
        int commissionsUpdated,
        BigDecimal totalDeltaAmount,
        String currency,
        Instant executedAt,

        List<PromoterReRatingOutcome> perPromoter
) {

    public record PromoterReRatingOutcome(
            UUID promoterUuid,
            String promoterCode,
            String promoterDisplayName,
            long inscriptionCount,
            UUID targetTierUuid,
            String targetTierName,
            int commissionsChanged,
            BigDecimal deltaAmount
    ) {}
}
