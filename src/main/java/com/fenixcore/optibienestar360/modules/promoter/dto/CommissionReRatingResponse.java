package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fenixcore.optibienestar360.core.display.Display;

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
 *
 * <p>Scalars carry a localized {@code _Display} sibling (ADR 0014).</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CommissionReRatingResponse(
        @Display(Display.Kind.DATE) LocalDate periodStart,
        @Display(Display.Kind.DATE) LocalDate periodEnd,
        @Display(Display.Kind.BOOLEAN) boolean dryRun,

        int totalPromoters,
        int commissionsUpdated,
        @Display(Display.Kind.MONEY) BigDecimal totalDeltaAmount,
        String currency,
        @Display(Display.Kind.DATETIME) Instant executedAt,

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
            @Display(Display.Kind.MONEY) BigDecimal deltaAmount
    ) {}
}
