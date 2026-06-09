package com.fenixcore.optisaludplus.modules.membership.dto;

import jakarta.validation.constraints.Size;

/**
 * Optional body for {@code PUT /v1/admin/memberships/{uuid}/cancel}. Free
 * text reason gets stored alongside the lifecycle transition in
 * {@code lastStatusChangeReason}. Body may be omitted entirely — the
 * service defaults the reason to "Cancelled by admin".
 */
public record MembershipCancelRequest(
        @Size(max = 500) String reason
) {}
