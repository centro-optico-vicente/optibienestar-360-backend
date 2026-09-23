package com.fenixcore.optibienestar360.modules.organization.dto;

/**
 * The organization's two system currencies (ADR 0015 §4), stripped to what
 * a currency-conversion display needs — never the full {@link OrganizationDto}
 * (name, legal name, tax id, logo, social links), which stays behind
 * {@code ORGANIZATION_VIEW}. Backs {@code GET /v1/organizations/currencies},
 * the generic "which currencies does this org work in" lookup any
 * authenticated user needs regardless of role (an affiliate registering
 * their own payment, an ally, a promoter, an admin), same reasoning as
 * {@code ExchangeRateLookupController}.
 */
public record OrganizationCurrenciesDto(
        CurrencyRef official,
        CurrencyRef reference
) {

    /** Minimal currency projection — no uuid, no active flag, nothing an unprivileged caller doesn't need. */
    public record CurrencyRef(String code, String symbol, Short decimalPlaces) {}
}
