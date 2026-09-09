package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.PeriodStrategy;
import com.fenixcore.optibienestar360.modules.promoter.entity.HierarchyOverrideTier.OverrideCategory;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * PATCH-style payload for {@code PUT /v1/admin/hierarchy-override-tiers/{uuid}}
 * — every field optional ({@code null} = leave unchanged). To switch
 * pct↔flat, send the new one (plus its currency for flat); the service
 * re-validates the XOR after applying. {@code rank}/{@code category} stay
 * editable (unlike {@code CommissionTier.planType}) since a band is a pure
 * config row with no calculation-basis snapshot pointing back at it.
 */
public record HierarchyOverrideTierUpdateRequest(
        String name,
        UUID rankUuid,
        OverrideCategory category,
        @PositiveOrZero Integer thresholdCount,
        @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal overridePct,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal flatAmount,
        UUID flatAmountCurrencyUuid,
        PeriodStrategy periodStrategy,
        Boolean active
) {}
