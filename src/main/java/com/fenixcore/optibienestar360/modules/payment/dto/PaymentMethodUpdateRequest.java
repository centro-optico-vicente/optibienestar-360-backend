package com.fenixcore.optibienestar360.modules.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code code} is the immutable natural key — not editable here, same as {@code Currency.code}. */
public record PaymentMethodUpdateRequest(
        @NotBlank @Size(max = 80) String name,
        @NotNull Boolean mandatoryBankAccount,
        @NotNull Boolean mandatoryPhone,
        @NotNull Boolean mandatoryEmail,
        @NotNull Boolean mandatoryReferenceNumber,
        Boolean active
) {}
