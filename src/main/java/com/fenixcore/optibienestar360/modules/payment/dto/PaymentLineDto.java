package com.fenixcore.optibienestar360.modules.payment.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Response projection of a single {@code payment_lines} row (V117, hub plan
 * ".ai/plans/2026-09-17-payments-unification-plan.md"). Surfaced as
 * {@link PaymentDto#lines()} alongside the historical flattened first-line
 * fields on the header DTO — added, not a replacement, so existing readers
 * of the flat shape keep working unchanged.
 *
 * <p>{@code method} is the {@code payment_methods} catalog entry (the HOW —
 * cash, transfer, Zelle...); not to be confused with {@link PaymentDto#paymentType()}
 * (the header's REASON, {@code payment_categories}).</p>
 */
public record PaymentLineDto(
        UUID uuid,

        @Display DisplayRef method,
        String methodDescription,
        boolean methodMandatoryIdentification,
        boolean methodMandatoryBank,
        boolean methodMandatoryBankAccount,
        boolean methodMandatoryAccountType,
        boolean methodMandatoryAccountCode,
        boolean methodMandatoryPhone,
        boolean methodMandatoryEmail,
        boolean methodMandatoryReferenceNumber,

        @Display(value = Display.Kind.MONEY, moneyCurrencyField = "currency_Code") BigDecimal amount,
        String currency_Code,
        @Display DisplayRef currency,

        String referenceNumber,
        @Display DisplayRef bank,
        String identification,
        String bankAccountType,
        String bankAccountCode,
        String bankAccountIdentifier,
        String phone,
        String email,

        @Display(value = Display.Kind.ENUM, enumScope = "payment.status") String status,
        @Display DisplayRef reviewedBy,
        @Display(Display.Kind.DATETIME) Instant reviewedAt,
        String reviewReason
) {}
