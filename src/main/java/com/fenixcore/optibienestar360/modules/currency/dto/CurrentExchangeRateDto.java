package com.fenixcore.optibienestar360.modules.currency.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The exchange rate vigente right now for a currency pair (ADR 0015 §7) —
 * generic, unopinionated response reused by any "preview a conversion
 * before saving" need across the app (payments, commissions, ally
 * services, ...): the caller multiplies its own amount by {@code rate}
 * client-side, so this endpoint never needs to know what the amount is for.
 *
 * <p>{@code available = false} (every other field {@code null}) is the
 * degrade path — no rate vigente for the pair, or an unresolvable currency
 * code — never an error (ADR 0015 §7 "degrade, never block").</p>
 */
public record CurrentExchangeRateDto(
        boolean available,
        String baseCurrencyCode,
        String quoteCurrencyCode,
        BigDecimal rate,
        LocalDate rateDate
) {
    public static final CurrentExchangeRateDto UNAVAILABLE = new CurrentExchangeRateDto(false, null, null, null, null);
}
