package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fenixcore.optibienestar360.modules.membership.entity.Plan.PlanType;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.PeriodStrategy;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionTier.AppliesTo;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payload for {@code POST /v1/admin/commission-tiers}. Exactly one of
 * {@code commissionPct} / {@code flatAmount} must be present — enforced
 * service-side ({@code commission_tier.pct_xor_flat}). {@code planType} and
 * {@code promoterTypeUuid} are optional ({@code null} = applies to every plan
 * / promoter type).
 */
public record CommissionTierCreateRequest(
        @NotBlank String name,
        String description,
        PlanType planType,
        UUID promoterTypeUuid,
        @PositiveOrZero Integer thresholdCount,
        @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal commissionPct,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal flatAmount,
        @NotNull PeriodStrategy periodStrategy,
        @NotNull AppliesTo appliesTo
) {}
