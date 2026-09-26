package com.fenixcore.optibienestar360.modules.promoter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Request body for the award/settlement pay endpoints. */
public record CompetitiveAwardPayRequest(
        @NotBlank @Size(max = 120) String payoutReference,
        UUID payoutPaymentUuid
) {}
