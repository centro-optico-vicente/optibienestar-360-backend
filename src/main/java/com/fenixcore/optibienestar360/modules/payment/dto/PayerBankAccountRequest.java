package com.fenixcore.optibienestar360.modules.payment.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Create / update payload for one of the caller's own accounts ({@code /v1/me/bank-accounts}, V204/V217). The
 * other fields are required as the sum of the is_mandatory_* checks of the accepted methods: Pago Móvil needs bank,
 * holder document and phone; a transfer needs account type and number; Zelle needs an email.
 */
public record PayerBankAccountRequest(
        @NotEmpty List<@NotNull UUID> paymentMethodUuids,
        UUID bankUuid,
        @Size(max = 40) String accountType,
        @Size(max = 40) String accountCode,
        @Size(max = 120) String accountIdentifier,
        @Size(max = 80) String identification,
        @Size(max = 30) String phone,
        @Size(max = 160) String email,
        @Size(max = 80) String alias
) {}
