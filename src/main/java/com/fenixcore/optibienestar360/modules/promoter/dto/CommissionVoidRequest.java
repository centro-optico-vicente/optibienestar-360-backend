package com.fenixcore.optibienestar360.modules.promoter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /v1/admin/commissions/{uuid}/void}. The reason is
 * REQUIRED — persisted to {@code void_reason} so a promoter disputing the
 * exclusion (or a future audit) can see why it never made a payout.
 */
public record CommissionVoidRequest(
        @NotBlank
        @Size(max = 500)
        String reason
) {}
