package com.fenixcore.optibienestar360.modules.promoter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Body for {@code POST /v1/admin/commissions/void-bulk} — every row must be PENDING (all or nothing). */
public record CommissionBulkVoidRequest(
        @NotEmpty @Size(max = 500) List<@NotNull UUID> commissionUuids,
        @NotBlank @Size(max = 500) String reason
) {}
