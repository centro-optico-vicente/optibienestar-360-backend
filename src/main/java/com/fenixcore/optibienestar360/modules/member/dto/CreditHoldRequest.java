package com.fenixcore.optibienestar360.modules.member.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Freezes ({@code hold = true}, reason required) or releases ({@code hold = false}) the automatic use of a person's
 * credit ({@code PUT /v1/admin/members/{uuid}/credit-hold}, V205).
 */
public record CreditHoldRequest(
        @NotNull Boolean hold,
        @Size(max = 255) String reason
) {}
