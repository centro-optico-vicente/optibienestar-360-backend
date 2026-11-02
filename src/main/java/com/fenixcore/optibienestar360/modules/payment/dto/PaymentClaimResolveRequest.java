package com.fenixcore.optibienestar360.modules.payment.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Staff resolution of an OPEN claim (V205). {@code KEEP_AS_CREDIT} releases the frozen amount, {@code REFUND}
 * books a reversal of {@code refundedAmount} (required, at most the claimed amount; the transfer itself is done by
 * finance outside the system) and {@code REJECT} dismisses the claim.
 */
public record PaymentClaimResolveRequest(
        @NotNull @Pattern(regexp = "^(KEEP_AS_CREDIT|REFUND|REJECT)$", message = "{payment.claim.action.invalid}")
        String action,

        @Digits(integer = 8, fraction = 2) BigDecimal refundedAmount,

        @Size(max = 500) String note
) {}
