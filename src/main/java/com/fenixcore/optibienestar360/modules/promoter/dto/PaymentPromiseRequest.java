package com.fenixcore.optibienestar360.modules.promoter.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Payload for {@code POST /v1/promoter/me/contacts/{memberUuid}/payment-promise}
 * (v2 PDF 2.b). The affiliate committed to pay {@code promisedAmount} by
 * {@code promisedAtDate}; both are required (the V36 CHECK enforces it at the DB
 * too). {@code promisedAtDate} must be today or later.
 */
public record PaymentPromiseRequest(
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 8, fraction = 2)
        BigDecimal promisedAmount,

        @NotNull @FutureOrPresent LocalDate promisedAtDate,

        @Size(max = 2000) String note
) {}
