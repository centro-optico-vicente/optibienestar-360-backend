package com.fenixcore.optibienestar360.modules.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * JSON part of the {@code POST /v1/me/payments} multipart request. Same
 * shape as {@link PaymentCreateRequest} minus {@code membershipUuid} and
 * {@code payerUserUuid} — self-service resolves both server-side (the
 * caller's own active membership; the caller is implicitly the payer).
 */
public record MyPaymentCreateRequest(
        @NotNull
        @Digits(integer = 8, fraction = 2)
        @DecimalMin(value = "0.01", message = "{payment.amount.positive}")
        BigDecimal amount,

        @Pattern(regexp = "^[A-Z]{3}$", message = "{payment.currency.iso}")
        String currency,   // default 'USD' server-side when null

        @NotNull UUID paymentMethodUuid,

        UUID bankUuid,
        @Size(max = 80) String identification,
        @Size(max = 40) String bankAccountType,
        @Size(max = 40) String bankAccountCode,
        @Size(max = 120) String bankAccountIdentifier,
        @Size(max = 40) String phone,
        @Size(max = 160) String email,

        @Size(max = 80) String referenceNumber,

	@NotNull @PastOrPresent Instant paymentDate,

        Boolean inscription,           // default false server-side
        LocalDate appliedPeriod,        // first day of covered month (recurring only)

        /** See {@link PaymentCreateRequest#coverageThroughPeriod()} — same rules. */
        LocalDate coverageThroughPeriod,

        String adminNotes
) {}
