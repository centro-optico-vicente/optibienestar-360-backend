package com.fenixcore.optisaludplus.modules.membership.dto;

import com.fenixcore.optisaludplus.modules.membership.entity.Plan.PlanType;

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
 * without a follow-up GET to /plans.</p>
 */
public record MembershipDto(
        UUID uuid,

        UUID memberUuid,

        // Plan reference (flat)
        UUID planUuid,
        String planCode,
        String planName,
        PlanType planType,

        // Lifecycle dates
        LocalDate enrolledAt,
        LocalDate expiresAt,
        LocalDate nextDueDate,
        LocalDate lastPaidThrough,

        // Pricing snapshot
        BigDecimal inscriptionFee,
        BigDecimal monthlyFee,
        int gracePeriodDays,

        // Status
        String status,
        Instant lastStatusChangeAt,
        String lastStatusChangeReason,

        // Audit
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {}
