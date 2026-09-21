package com.fenixcore.optibienestar360.modules.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code code} is the immutable natural key — not editable here, same as {@code Currency.code}. */
public record PaymentMethodUpdateRequest(
        @NotBlank @Size(max = 80) String name,
	@Size(max = 255) String description,
	@NotNull Boolean mandatoryIdentification,
	@NotNull Boolean mandatoryBank,
        @NotNull Boolean mandatoryBankAccount,
	@NotNull Boolean mandatoryAccountType,
	@NotNull Boolean mandatoryAccountCode,
        @NotNull Boolean mandatoryPhone,
        @NotNull Boolean mandatoryEmail,
        @NotNull Boolean mandatoryReferenceNumber,
        Boolean active
) {
	public PaymentMethodUpdateRequest(String name, boolean mandatoryBankAccount,
			boolean mandatoryPhone, boolean mandatoryEmail,
			boolean mandatoryReferenceNumber, Boolean active) {
		this(name, null, false, false, mandatoryBankAccount, false, false, mandatoryPhone, mandatoryEmail, mandatoryReferenceNumber, active);
	}
}
