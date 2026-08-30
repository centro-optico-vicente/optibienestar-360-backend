package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.AppliesTo;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.PeriodStrategy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Output DTO for {@code GET /v1/admin/commissions}. Flat record with
 * promoter / payment / member refs extracted as UUIDs + labels so the
 * admin queue renders without N+1. Presentational scalars carry a localized
 * {@code _Display} sibling (hub ADR 0014).
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
        @Display(Display.Kind.MONEY) BigDecimal amount,
        String currency,
        @Display(Display.Kind.MONEY) BigDecimal calculationBasis,
        @Display(Display.Kind.NUMBER) BigDecimal commissionPct,
        @Display(Display.Kind.MONEY) BigDecimal flatAmount,
        String tierNameSnapshot,

        // Categorization
        @Display(Display.Kind.ENUM) AppliesTo appliesTo,
        @Display(Display.Kind.ENUM) PeriodStrategy periodStrategy,
        @Display(Display.Kind.DATE) LocalDate periodStart,
        @Display(Display.Kind.DATE) LocalDate periodEnd,
        @Display(Display.Kind.DATETIME) Instant earnedAt,

        // Payout tracking
        String payoutReference,
        @Display(Display.Kind.DATETIME) Instant paidAt,
        @Display(Display.Kind.DATETIME) Instant voidedAt,
        String voidReason,
        String adminNotes,

        // Audit + workflow status
        @Display(Display.Kind.BOOLEAN) boolean active,
        @Display(value = Display.Kind.ENUM, enumScope = "commission.status") String status,
        @Display(Display.Kind.DATETIME) Instant createdAt,
        @Display(Display.Kind.DATETIME) Instant updatedAt
) {}
