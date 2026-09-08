package com.fenixcore.optibienestar360.modules.promoter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Payload for {@code POST /v1/admin/commissions/reject} — rejects the given
 * commission rows in one call (row-level granularity, hub plan §4, PR5).
 * {@code reason} is mandatory and cascades to every hierarchy override that
 * depends on each rejected row (directly or through a chain of overrides),
 * which get {@code VOIDED} with the same reason — see {@code
 * CommissionApprovalService}.
 */
public record RejectCommissionsRequest(
        @NotEmpty List<UUID> commissionUuids,
        @NotBlank @Size(max = 2000) String reason
) {}
