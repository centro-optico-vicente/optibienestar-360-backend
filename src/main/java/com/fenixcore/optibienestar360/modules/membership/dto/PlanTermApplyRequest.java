package com.fenixcore.optibienestar360.modules.membership.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Payload of {@code POST /v1/admin/plans/{uuid}/membership-terms/apply} (hub ADR 0034).
 *
 * <ul>
 *   <li>{@code membershipUuids} — memberships to update; null or empty = every live membership of the plan that
 *   does not comply with the plan's rule.</li>
 *   <li>{@code expiresAt} — first expiry date chosen by the admin (one for all); must be after today.</li>
 * </ul>
 */
public record PlanTermApplyRequest(
        List<UUID> membershipUuids,
        @NotNull LocalDate expiresAt
) {}
