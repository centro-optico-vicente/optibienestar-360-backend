package com.fenixcore.optibienestar360.modules.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PaymentMethodCreateRequest(
        @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,39}$", message = "{validation.code.uppercase.long}") String code,
        @NotBlank @Size(max = 80) String name,
        @NotNull Boolean mandatoryBankAccount,
        @NotNull Boolean mandatoryPhone,
        @NotNull Boolean mandatoryEmail,
        @NotNull Boolean mandatoryReferenceNumber
) {}
