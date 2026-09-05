package com.fenixcore.optibienestar360.modules.promoter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for {@code PUT /v1/admin/bonus-awards/{uuid}/pay}. */
public record BonusAwardPayRequest(
        @NotBlank @Size(max = 120) String payoutReference
) {}
