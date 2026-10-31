package com.fenixcore.optibienestar360.modules.payment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Create / update payload for one of the caller's own accounts ({@code /v1/me/bank-accounts}, V204/V207). The
 * payment method decides which of the other fields are required (its is_mandatory_* flags): Pago Móvil needs bank,
 * holder document and phone; a transfer needs account type and number; Zelle needs an email.
 */
public record PayerBankAccountRequest(
        @NotNull UUID paymentMethodUuid,
        UUID bankUuid,
        @Size(max = 40) String accountType,
        @Size(max = 40) String accountCode,
        @Size(max = 120) String accountIdentifier,
        @Size(max = 80) String identification,
        @Size(max = 30) String phone,
        @Size(max = 160) String email,
        @Size(max = 80) String alias
) {}
