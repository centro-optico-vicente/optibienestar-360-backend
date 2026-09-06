package com.fenixcore.optibienestar360.modules.currency.dto;

import com.fenixcore.optibienestar360.core.display.Display;

import java.util.UUID;

/**
 * Admin catalog projection for {@code /v1/admin/currencies} (ADR 0015, plan
 * "CRUD admin de Currency + ExchangeRate"). Mirrors {@code ExchangeRateDto}
 * (same {@code BaseEntity} lineage) in NOT exposing {@code status} —
 * {@code active} is the only lifecycle flag this catalog surfaces, same as
 * {@code CountryDto}/{@code GenderDto} do for {@code BaseAuditEntity} catalogs.
 */
public record CurrencyDto(
        UUID uuid,
        String code,
        String name,
        String symbol,
        Short decimalPlaces,
        @Display(Display.Kind.BOOLEAN) boolean active
) {}
