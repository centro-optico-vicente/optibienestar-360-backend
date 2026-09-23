package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fenixcore.optibienestar360.modules.promoter.entity.CollectionCommissionTier.Basis;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * PATCH-style payload for {@code PUT /v1/admin/collection-commission-tiers/{uuid}}
 * — every field optional. To switch pct↔flat, send the new one (plus its
 * currency for flat); to switch basis (DAYS↔AMOUNT), send {@code basis} plus
 * the matching bucket field — the service re-validates both XORs after
 * applying. {@code campaignUuid} has no dedicated "clear" sentinel — sending
 * it re-resolves and overwrites the anchor.
 */
public record CollectionCommissionTierUpdateRequest(
        String name,
        String description,
        Basis basis,
        @Positive Integer maxDays,
        @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal minAmount,
        UUID minAmountCurrencyUuid,
        @DecimalMin("0.01") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal commissionPct,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal flatAmount,
        UUID flatAmountCurrencyUuid,
        List<UUID> promoterTypeUuids,
        Boolean active,
        UUID campaignUuid,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt
) {}
