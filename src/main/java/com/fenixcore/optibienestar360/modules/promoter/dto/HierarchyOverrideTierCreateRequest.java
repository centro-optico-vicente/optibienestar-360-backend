package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.PeriodStrategy;
import com.fenixcore.optibienestar360.modules.promoter.entity.HierarchyOverrideTier.OverrideCategory;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payload for {@code POST /v1/admin/hierarchy-override-tiers}. Exactly one
 * of {@code overridePct} / {@code flatAmount} must be present — enforced
 * service-side ({@code hierarchy_override_tier.pct_xor_flat}); {@code
 * flatAmountCurrencyUuid} is required alongside {@code flatAmount} (ADR
 * 0015 pattern — a flat amount always carries its own currency).
 */
public record HierarchyOverrideTierCreateRequest(
        @NotBlank String name,
        String description,
        @NotNull UUID rankUuid,
        @NotNull OverrideCategory category,
        @PositiveOrZero Integer thresholdCount,
        @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal overridePct,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal flatAmount,
        UUID flatAmountCurrencyUuid,
        @NotNull PeriodStrategy periodStrategy
) {}
