package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.AppliesTo;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One individual commission row inside a promoter's node of {@code GET
 * /v1/admin/commissions/approval-queue} (V107, hub plan §4, PR5) — the
 * level-2 rows of the 2-level expandable table ({@code
 * RolePermissionsModal}'s collapsible-group pattern).
 *
 * <p>{@link #locked} = {@code true} for anything not {@code PENDING} — the
 * client renders it checked-and-disabled (or unchecked-and-disabled for
 * {@code REJECTED}/{@code VOIDED}) and must never let the level-1 "select
 * all" toggle touch it. {@link #checked} is the row's initial checkbox
 * state: {@code true} for {@code PENDING}/{@code APPROVED}/{@code PAID}
 * (money that counts toward the period total), {@code false} for {@code
 * REJECTED}/{@code VOIDED} (excluded).</p>
 */
public record CommissionApprovalRowDto(
        UUID uuid,
        @Display(Display.Kind.ENUM) AppliesTo appliesTo,
        @Display(Display.Kind.MONEY) BigDecimal amount,
        String currencyCode,
        @Display(Display.Kind.DATE) LocalDate periodStart,
        @Display(Display.Kind.DATE) LocalDate periodEnd,
        @Display(Display.Kind.DATETIME) Instant earnedAt,
        @Display(value = Display.Kind.ENUM, enumScope = "commission.status") String status,
        @Display(Display.Kind.BOOLEAN) boolean locked,
        @Display(Display.Kind.BOOLEAN) boolean checked
) {}
