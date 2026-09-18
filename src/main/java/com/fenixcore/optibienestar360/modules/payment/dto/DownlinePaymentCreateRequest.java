package com.fenixcore.optibienestar360.modules.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * JSON part of the {@code POST /v1/promoter/me/payments} multipart request
 * — a promoter registering a collection on behalf of an affiliate in their
 * own portfolio. Same shape as {@link MyPaymentCreateRequest} plus the
 * target {@code memberUuid}; the service layer verifies that member belongs
 * to the caller's downline (same {@code ownedMember} check
 * {@code PromoterCollectionService} already applies for reminders/payment
 * promises) before resolving their active membership.
 */
public record DownlinePaymentCreateRequest(
        @NotNull UUID memberUuid,

        @NotNull
        @Digits(integer = 8, fraction = 2)
        @DecimalMin(value = "0.01", message = "{payment.amount.positive}")
        BigDecimal amount,

        @Pattern(regexp = "^[A-Z]{3}$", message = "{payment.currency.iso}")
        String currency,   // default 'USD' server-side when null

        @NotBlank String paymentMethod,

        @Size(max = 80) String referenceNumber,

        @NotNull @PastOrPresent LocalDate paymentDate,

        Boolean inscription,           // default false server-side
        LocalDate appliedPeriod,        // first day of covered month (recurring only)

        String adminNotes
) {}
