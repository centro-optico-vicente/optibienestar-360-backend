package com.fenixcore.optibienestar360.modules.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Payload for {@code POST /v1/admin/payments/{uuid}/discount} — a one-off
 * discount on a single PENDING payment (permission {@code ALLOWS_DISCOUNT}).
 * A {@code reason} is mandatory (the discount is auditable). The amount must be
 * positive and not exceed the payment total — enforced service-side
 * ({@code payment.discount.exceeds_amount}).
 */
public record PaymentDiscountRequest(
        @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal amount,
        @NotBlank String reason
) {}
