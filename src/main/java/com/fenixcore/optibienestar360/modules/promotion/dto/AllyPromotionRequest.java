package com.fenixcore.optibienestar360.modules.promotion.dto;

import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.BenefitType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Body of the ally portal's own campaign benefit (V257, hub ADR 0038 §8):
 * always an ALLY_BENEFIT of kind ACQUISITION for the caller's ally.
 * {@code benefitPct} only for PERCENTAGE, {@code benefitAmount} +
 * {@code benefitCurrencyUuid} only for FLAT, neither for FREE.
 */
public record AllyPromotionRequest(
        @NotBlank @Size(max = 150) String name,
        String description,
        @NotNull UUID allyProductUuid,
        @NotNull BenefitType benefitType,
        @DecimalMin("0.01") @DecimalMax("100.00") BigDecimal benefitPct,
        @DecimalMin("0.01") BigDecimal benefitAmount,
        UUID benefitCurrencyUuid,
        @Min(1) Integer benefitQuantity,
        boolean ownQuota,
        @Min(1) Integer maxRedemptions,
        /** Eligible plans; empty or null = every plan. */
        List<UUID> planUuids
) {}
