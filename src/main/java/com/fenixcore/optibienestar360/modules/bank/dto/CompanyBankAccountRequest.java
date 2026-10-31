package com.fenixcore.optibienestar360.modules.bank.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Create/update payload for a company bank account (V201); the same shape serves both verbs. */
public record CompanyBankAccountRequest(
        /** The method decides which of the fields below are required (its is_mandatory_* flags). */
        @NotNull UUID paymentMethodUuid,
        UUID bankUuid,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "{payment.currency.iso}") String currency,
        @Size(max = 40) String accountType,
        @Size(max = 40) String accountNumber,
        @NotBlank @Size(max = 160) String holderName,
        @Size(max = 30) String holderDocument,
        @Size(max = 255) String notes,
        @Size(max = 30) String phone,
        @Size(max = 160) String email,
        Integer sortOrder,
        /** Show it without authentication on the public endpoint; defaults to private on create. */
        Boolean publicVisible,
        Boolean active
) {}
