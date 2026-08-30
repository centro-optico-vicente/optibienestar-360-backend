package com.fenixcore.optibienestar360.modules.membership.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan.PlanType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Output DTO for {@code GET /v1/admin/members/{memberUuid}/memberships}
 * (list / detail) and the response of POST + cancel/reactivate endpoints.
 *
 * <p>Plan fields are flat-extracted via the mapper (planUuid + planCode +
 * planName + planType) so the frontend can render the subscription card
 * without a follow-up GET to /plans. Presentational scalars carry a
 * localized {@code _Display} sibling (hub ADR 0014) so the frontend renders
 * money / dates / status without re-formatting.</p>
 */
public record MembershipDto(
        UUID uuid,

        UUID memberUuid,

        // Plan reference (flat)
        UUID planUuid,
        String planCode,
        String planName,
        @Display(Display.Kind.ENUM) PlanType planType,

        // Lifecycle dates
        @Display(Display.Kind.DATE) LocalDate enrolledAt,
        @Display(Display.Kind.DATE) LocalDate expiresAt,
        @Display(Display.Kind.DATE) LocalDate nextDueDate,
        @Display(Display.Kind.DATE) LocalDate lastPaidThrough,

        // Pricing snapshot
        @Display(Display.Kind.MONEY) BigDecimal inscriptionFee,
        @Display(Display.Kind.MONEY) BigDecimal monthlyFee,
        int gracePeriodDays,

        // Status
        @Display(value = Display.Kind.ENUM, enumScope = "membership.status") String status,
        @Display(Display.Kind.DATETIME) Instant lastStatusChangeAt,
        String lastStatusChangeReason,

        // Audit
        @Display(Display.Kind.BOOLEAN) boolean active,
        @Display(Display.Kind.DATETIME) Instant createdAt,
        @Display(Display.Kind.DATETIME) Instant updatedAt
) {}
