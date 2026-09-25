package com.fenixcore.optibienestar360.modules.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Input shape for a single method/amount block within a multi-line payment
 * (V117 lines feature). Used both embedded in the create requests'
 * {@code lines} list and in {@link PaymentLinesUpdateRequest}.
 *
 * <p>{@code currencyUuid} is optional — {@code null} falls back to the
 * header's resolved currency (same "USD default" behavior the single-line
 * flow already has), letting every line share one currency without
 * repeating it, while still allowing a genuinely mixed-currency split.</p>
 */
public record PaymentLineRequest(
        @NotNull UUID paymentMethodUuid,

        UUID bankUuid,

        @NotNull
        @Digits(integer = 8, fraction = 2)
        @DecimalMin(value = "0.01", message = "{payment.amount.positive}")
        BigDecimal amount,

        UUID currencyUuid,

        @Size(max = 80) String identification,
        @Size(max = 40) String bankAccountType,
        @Size(max = 40) String bankAccountCode,
        @Size(max = 120) String bankAccountIdentifier,
        @Size(max = 40) String phone,
        @Size(max = 160) String email,
        @Size(max = 80) String referenceNumber
) {}
