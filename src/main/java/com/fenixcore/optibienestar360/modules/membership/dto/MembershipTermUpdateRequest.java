package com.fenixcore.optibienestar360.modules.membership.dto;

import java.time.LocalDate;

/**
 * Payload for {@code PUT /v1/admin/memberships/{uuid}/term} (hub ADR 0034).
 *
 * <ul>
 *   <li>{@code reinscriptionRequired} — null keeps the current value; {@code false} makes the membership
 *   open-ended (clears the snapshot and any pending re-inscription) and {@code expiresAt} becomes a plain
 *   end date (null = none); {@code true} on a membership without a rule uses the plan's rule, or 12 months
 *   when the plan has none (then {@code expiresAt} is required).</li>
 *   <li>{@code expiresAt} — end of the current term; must be after the term start. Null keeps it while
 *   re-inscription is required.</li>
 * </ul>
 */
public record MembershipTermUpdateRequest(
        Boolean reinscriptionRequired,
        LocalDate expiresAt
) {}
