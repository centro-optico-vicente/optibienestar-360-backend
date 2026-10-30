package com.fenixcore.optibienestar360.modules.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Create / update payload for one of the caller's own origin bank accounts ({@code /v1/me/bank-accounts}, V204). */
public record PayerBankAccountRequest(
        @NotNull UUID bankUuid,
        @Size(max = 40) String accountType,
        @Size(max = 40) String accountCode,
        @NotBlank @Size(max = 120) String accountIdentifier,
        @Size(max = 80) String identification,
        @Size(max = 80) String alias
) {}
