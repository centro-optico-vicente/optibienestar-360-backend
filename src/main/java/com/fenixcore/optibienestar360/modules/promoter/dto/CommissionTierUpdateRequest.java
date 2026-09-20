package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fenixcore.optibienestar360.modules.membership.entity.Plan.PlanType;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.PeriodStrategy;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionTier.AppliesTo;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * PATCH-style payload for {@code PUT /v1/admin/commission-tiers/{uuid}} — every
 * field optional ({@code null} = leave unchanged). To switch pct↔flat, send the
 * new one; the service re-validates the XOR after applying.
 */
public record CommissionTierUpdateRequest(
        String name,
        String description,
        PlanType planType,
        UUID promoterTypeUuid,
        @PositiveOrZero Integer thresholdCount,
        @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal commissionPct,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal flatAmount,
        PeriodStrategy periodStrategy,
        AppliesTo appliesTo,
        Boolean active
) {}
