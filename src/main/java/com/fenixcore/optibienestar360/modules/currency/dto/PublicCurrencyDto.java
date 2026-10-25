package com.fenixcore.optibienestar360.modules.currency.dto;

/**
 * Anonymous-facing currency projection for the public conversion picker —
 * the code/name/symbol/decimals a visitor needs to choose a target currency,
 * never the {@link CurrencyDto} {@code uuid} (an internal admin handle).
 * Only active currencies are ever listed, so {@code active} is always
 * {@code true}; it's kept so the frontend can reuse the catalog-item shape
 * its authenticated {@code /v1/me/currencies} counterpart already returns.
 */
public record PublicCurrencyDto(
        String code,
        String name,
        String symbol,
        Short decimalPlaces,
        boolean active
) {}
