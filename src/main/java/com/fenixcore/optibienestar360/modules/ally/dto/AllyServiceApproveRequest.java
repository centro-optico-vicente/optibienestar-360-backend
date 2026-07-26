package com.fenixcore.optibienestar360.modules.ally.dto;

import jakarta.validation.constraints.Size;

/**
 * Optional payload for {@code POST /v1/admin/ally-services/{uuid}/approve} — an
 * approval note recorded in the review log. Approval needs no reason (the V11
 * CHECK only requires one for REJECTED / REMOVED), so the whole body is
 * optional.
 */
public record AllyServiceApproveRequest(
        @Size(max = 2000) String comment
) {}
