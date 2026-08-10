package com.fenixcore.optibienestar360.modules.promoter.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Payload for {@code POST /v1/admin/commissions/re-rate}. Recomputes every
 * PENDING INSCRIPTION commission inside the date range to the highest volume
 * band (vertical-8 Ítem A) the promoter's monthly inscription count reached —
 * so 60 inscriptions in the month all pay 30%, not a progressive mix of
 * 25%/30% depending on when each one landed. PAID commissions are never
 * touched (already settled).
 *
 * <p>{@code dryRun} = {@code true} computes the deltas without writing —
 * lets the admin verify totals before committing at month close.</p>
 */
public record CommissionReRatingRequest(
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd,
        Boolean dryRun
) {}
