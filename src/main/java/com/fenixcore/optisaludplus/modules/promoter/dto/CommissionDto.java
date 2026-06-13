package com.fenixcore.optisaludplus.modules.promoter.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fenixcore.optisaludplus.modules.promoter.entity.Commission.AppliesTo;
import com.fenixcore.optisaludplus.modules.promoter.entity.Commission.PeriodStrategy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Output DTO for {@code GET /v1/admin/commissions}. Flat record with
 * promoter / payment / member refs extracted as UUIDs + labels so the
 * admin queue renders without N+1.
 *
 * <p>The calculation snapshot (basis, pct, flat, tier name) is echoed
 * back so reports stay legible without re-deriving from the live
 * commission_tiers table (which may have moved since this row was
 * computed).</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CommissionDto(
        UUID uuid,

        // Subject (flat refs)
        UUID promoterUuid,
        String promoterCode,
        String promoterDisplayName,
        UUID paymentUuid,
        UUID memberUuid,

        // Money + snapshot
        BigDecimal amount,
        String currency,
        BigDecimal calculationBasis,
        BigDecimal commissionPct,
        BigDecimal flatAmount,
        String tierNameSnapshot,

        // Categorization
        AppliesTo appliesTo,
        PeriodStrategy periodStrategy,
        LocalDate periodStart,
        LocalDate periodEnd,
        Instant earnedAt,

        // Payout tracking
        String payoutReference,
        Instant paidAt,
        Instant voidedAt,
        String voidReason,
        String adminNotes,

        // Audit + workflow status
        boolean active,
        String status,
        Instant createdAt,
        Instant updatedAt
) {}
