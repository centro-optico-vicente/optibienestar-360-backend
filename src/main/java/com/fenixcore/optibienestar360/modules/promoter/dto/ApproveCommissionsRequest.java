package com.fenixcore.optibienestar360.modules.promoter.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/**
 * Payload for {@code POST /v1/admin/commissions/approve} — approves the
 * given commission rows in one call (row-level granularity, hub plan §4,
 * PR5). Every id must currently be {@code PENDING}; the backend rejects
 * (400) the whole request otherwise (see {@code CommissionApprovalService})
 * rather than silently skipping the ones that don't qualify.
 */
public record ApproveCommissionsRequest(
        @NotEmpty List<UUID> commissionUuids
) {}
