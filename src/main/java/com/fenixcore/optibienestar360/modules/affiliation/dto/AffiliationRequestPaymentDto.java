package com.fenixcore.optibienestar360.modules.affiliation.dto;

import com.fenixcore.optibienestar360.core.display.Display;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Inscription payment reported on an affiliation request ({@code payment} of
 * {@link AffiliationRequestDetailDto}). {@code amount}/{@code currencyCode}
 * are the plan's inscription fee at submit time. {@code hasSupport} tells
 * whether a proof file was attached (download through
 * {@code GET /v1/admin/affiliation-requests/{uuid}/payment-support});
 * {@code paymentUuid} is the payment created on approval ({@code null} before).
 */
public record AffiliationRequestPaymentDto(
        String paymentMethodName,
        String bankName,
        String referenceNumber,
        @Display(value = Display.Kind.MONEY, moneyCurrencyField = "currencyCode") BigDecimal amount,
        String currencyCode,
        @Display(Display.Kind.DATETIME) Instant paymentDate,
        String identification,
        String phone,
        String email,
        String bankAccountIdentifier,
        String supportFileName,
        boolean hasSupport,
        UUID paymentUuid
) {}
