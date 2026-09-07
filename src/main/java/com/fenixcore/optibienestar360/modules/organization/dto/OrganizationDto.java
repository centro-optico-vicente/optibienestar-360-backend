package com.fenixcore.optibienestar360.modules.organization.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;

import java.util.UUID;

/**
 * Admin projection for {@code /v1/admin/organizations/me} (ADR 0015 §4,
 * plan "CRUD admin de Currency + ExchangeRate y país↔moneda oficial").
 * Single row today — no {@code active}/{@code status} exposed, same as
 * {@code CurrencyDto} does not surface it for other {@code BaseEntity}
 * catalogs where the lifecycle flag is not meaningful to an admin editing it.
 */
public record OrganizationDto(
        UUID uuid,
        String name,
        String legalName,
        String taxIdentifier,
        String logoKey,
        @Display DisplayRef officialCurrency,
        @Display DisplayRef referenceCurrency
) {}
