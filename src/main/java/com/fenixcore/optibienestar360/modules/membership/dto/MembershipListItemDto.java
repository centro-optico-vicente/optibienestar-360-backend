package com.fenixcore.optibienestar360.modules.membership.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan.PlanType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Compact row of {@code GET /v1/admin/memberships} — the cross-member list with filters. Details (campaign,
 * conversions, audit) stay on {@link MembershipDto}; edit forms must load the detail via the member sub-resource.
 * {@code paymentStatus} is the derived collection state: {@code OVERDUE} (due date passed on an ACTIVE or
 * SUSPENDED membership), {@code DUE_SOON}, or {@code UP_TO_DATE}; {@code null} for CANCELED / EXPIRED.
 */
public record MembershipListItemDto(
        UUID uuid,
        UUID memberUuid,
        @Display DisplayRef member,
        @Display DisplayRef advisor,
        UUID planUuid,
        String planCode,
        String planName,
        @Display(Display.Kind.ENUM) PlanType planType,
        @Display(Display.Kind.DATE) LocalDate enrolledAt,
        @Display(Display.Kind.DATE) LocalDate nextDueDate,
        @Display(Display.Kind.DATE) LocalDate lastPaidThrough,
        @Display(value = Display.Kind.MONEY, moneyCurrencyField = "currency_Code") BigDecimal monthlyFee,
        String currency_Code,
        @Display(value = Display.Kind.ENUM, enumScope = "membership.status") String status,
        @Display(value = Display.Kind.ENUM, enumScope = "membership.paymentStatus") String paymentStatus,
        long daysOverdue
) {}
