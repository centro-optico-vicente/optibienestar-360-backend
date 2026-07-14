package com.fenixcore.optibienestar360.modules.membership.dto;

import jakarta.validation.constraints.Size;

/**
 * Optional body for {@code PUT /v1/admin/memberships/{uuid}/reactivate}.
 * Mirrors {@link MembershipCancelRequest} — separate type so future
 * reactivation-specific fields (e.g. {@code newDueDate}, {@code keepGrace})
 * don't pollute the cancel request.
 */
public record MembershipReactivateRequest(
        @Size(max = 500) String reason
) {}
