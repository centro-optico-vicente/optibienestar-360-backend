package com.fenixcore.optibienestar360.modules.currency.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * An amount converted between two arbitrary currencies as of a point in
 * time (ADR 0015 §7) — backs {@code GET /v1/exchange-rates/convert}, the
 * "convert to another currency" picker of the frontend's conversion popover.
 * Unlike {@link CurrentExchangeRateDto} (rate only, the caller multiplies),
 * this resolves the converted amount server-side and returns it both raw and
 * formatted ({@code _Display}, hub ADR 0014) for the request's locale.
 *
 * <p>{@code viaCurrencyCode} is the bridge currency (the organization's
 * official one) when no direct/inverse pair existed and the rate had to be
 * crossed — e.g. USD→EUR through VES; {@code null} for a direct pair.
 * {@code rate_Display} always reads "1 X = Y Z" in whichever direction gives
 * {@code Y >= 1}, so a VES→USD rate isn't rendered as "Bs. 1,00 = US$ 0,00".</p>
 *
 * <p>{@code available = false} (every other field {@code null}) is the
 * degrade path — no rate vigente, unknown currency code or invalid amount —
 * never an error (ADR 0015 §7 "degrade, never block").</p>
 */
public record ConvertedAmountDto(
        boolean available,
        BigDecimal amount,
        String amount_Display,
        String fromCurrencyCode,
        String toCurrencyCode,
        BigDecimal convertedAmount,
        String convertedAmount_Display,
        BigDecimal rate,
        String rate_Display,
        LocalDate rateDate,
        String rateDate_Display,
        String viaCurrencyCode
) {
    public static final ConvertedAmountDto UNAVAILABLE = new ConvertedAmountDto(
            false, null, null, null, null, null, null, null, null, null, null, null);
}
