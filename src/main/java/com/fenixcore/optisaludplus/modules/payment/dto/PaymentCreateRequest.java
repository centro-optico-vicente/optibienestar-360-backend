package com.fenixcore.optisaludplus.modules.payment.dto;

import com.fenixcore.optisaludplus.modules.payment.entity.Payment.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * JSON part of the {@code POST /v1/admin/payments} multipart request. The
 * proof of payment file travels as a separate {@code MultipartFile} part
 * (see {@code AdminPaymentController}) so this DTO stays purely declarative
 * and easy to validate.
 *
 * <p>Allocation rules (cross-field, enforced at the service layer + V23
 * CHECK):</p>
 * <ul>
 *   <li>If {@link #inscription} is {@code true}, {@link #appliedPeriod}
 *       must be {@code null} (one-time fee, not aligned to a billing
 *       month).</li>
 *   <li>If {@link #inscription} is {@code false}, {@link #appliedPeriod}
 *       <i>should</i> be set — service falls back to the first day of the
 *       current month when null so admin can register a "current month"
 *       payment without retyping.</li>
 * </ul>
 *
 * <p>{@code payerUserUuid} is optional. When {@code null}, the payer is
 * unknown / cash-at-the-counter scenario.</p>
 */
public record PaymentCreateRequest(
        @NotNull UUID membershipUuid,

        @NotNull
        @Digits(integer = 8, fraction = 2)
        @DecimalMin(value = "0.01", message = "{payment.amount.positive}")
        BigDecimal amount,

        @Pattern(regexp = "^[A-Z]{3}$", message = "{payment.currency.iso}")
        String currency,   // default 'USD' server-side when null

        @NotNull PaymentMethod paymentMethod,

        @Size(max = 80) String referenceNumber,

        @NotNull @PastOrPresent LocalDate paymentDate,

        Boolean inscription,           // default false server-side
        LocalDate appliedPeriod,        // first day of covered month (recurring only)

        UUID payerUserUuid,             // optional — null for cash-at-counter

        String adminNotes
) {}
