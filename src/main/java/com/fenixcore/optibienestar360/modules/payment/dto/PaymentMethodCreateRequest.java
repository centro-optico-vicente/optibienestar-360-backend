package com.fenixcore.optibienestar360.modules.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PaymentMethodCreateRequest(
        @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,39}$", message = "{validation.code.uppercase.long}") String code,
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
        @NotNull Boolean requiresApproval
) {
	public PaymentMethodCreateRequest(String code, String name, boolean mandatoryBankAccount,
					boolean mandatoryPhone, boolean mandatoryEmail,
					boolean mandatoryReferenceNumber) {
		this(code, name, null, false, false, mandatoryBankAccount, false, false, mandatoryPhone, mandatoryEmail,
				mandatoryReferenceNumber, true);
	}
}
