package com.fenixcore.optibienestar360.modules.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Live "what would this convert to" preview for the payment registration
 * form (ADR 0015 §7 Caso B) — {@code amount}/{@code currency} entered so far
 * converted to the target membership's own currency, at the rate vigente
 * right now. Distinct from {@code PaymentDto.exchangeRateUsed} (Caso A — the
 * snapshot taken when the payment is actually approved): this is informative
 * only, computed before the payment even exists, and is never persisted.
 *
 * <p>{@code available = false} (every other field {@code null}) is the
 * degrade path for a missing rate — not an error the admin should be
 * blocked on (ADR 0015 §7 "degrade, never block"). Same currency is not a
 * special case: it still comes back {@code available = true} with
 * {@code rate = 1}, same as {@link com.fenixcore.optibienestar360.modules.currency.service.CurrencyConversionService#convert}.</p>
 */
public record PaymentExchangeRatePreviewDto(
        boolean available,
        BigDecimal convertedAmount,
        String convertedCurrencyCode,
        BigDecimal rate,
        LocalDate rateDate
) {
    public static final PaymentExchangeRatePreviewDto UNAVAILABLE =
            new PaymentExchangeRatePreviewDto(false, null, null, null, null);
}
