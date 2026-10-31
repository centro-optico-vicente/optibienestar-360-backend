package com.fenixcore.optibienestar360.modules.payment.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A claim on the remaining credit of a payment (V205): inside {@link PaymentDto#openClaim()} and in the admin claims list. */
public record PaymentClaimDto(
        UUID uuid,
        UUID paymentUuid,
        @Display DisplayRef payment,
        @Display DisplayRef payer,
        @Display DisplayRef beneficiary,
        @Display(value = Display.Kind.ENUM, enumScope = "payment.claimReason") String reason,
        @Display(value = Display.Kind.MONEY, moneyCurrencyField = "currency_Code") BigDecimal claimedAmount,
        String currency_Code,
        String comment,
        @Display(value = Display.Kind.ENUM, enumScope = "payment.claimStatus") String status,
        @Display(Display.Kind.DATETIME) Instant openedAt,
        @Display DisplayRef openedBy,
        @Display(Display.Kind.DATETIME) Instant resolvedAt,
        @Display DisplayRef resolvedBy,
        String resolutionNote,
        @Display(value = Display.Kind.MONEY, moneyCurrencyField = "currency_Code") BigDecimal refundedAmount
) {}
