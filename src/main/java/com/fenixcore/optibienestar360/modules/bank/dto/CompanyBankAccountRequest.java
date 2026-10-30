package com.fenixcore.optibienestar360.modules.bank.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Create/update payload for a company bank account (V201); the same shape serves both verbs. */
public record CompanyBankAccountRequest(
        @NotNull UUID bankUuid,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "{payment.currency.iso}") String currency,
        @NotBlank @Size(max = 40) String accountType,
        @NotBlank @Size(max = 40) String accountNumber,
        @NotBlank @Size(max = 160) String holderName,
        @NotBlank @Size(max = 30) String holderDocument,
        @Size(max = 255) String notes,
        Integer sortOrder,
        Boolean active
) {}
