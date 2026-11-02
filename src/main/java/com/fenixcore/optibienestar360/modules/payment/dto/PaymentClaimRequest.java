package com.fenixcore.optibienestar360.modules.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Opens a claim on the remaining credit of an approved payment (V205): the payer — or staff — says it is wrong,
 * overpaid or should be refunded. {@code claimedAmount} cannot exceed the payment's remaining credit.
 */
public record PaymentClaimRequest(
        @NotNull @Pattern(regexp = "^(ERROR|OVERPAID|REFUND_REQUEST|OTHER)$", message = "{payment.claim.reason.invalid}")
        String reason,

        @NotNull
        @Digits(integer = 8, fraction = 2)
        @DecimalMin(value = "0.01", message = "{payment.amount.positive}")
        BigDecimal claimedAmount,

        @Size(max = 500) String comment
) {}
