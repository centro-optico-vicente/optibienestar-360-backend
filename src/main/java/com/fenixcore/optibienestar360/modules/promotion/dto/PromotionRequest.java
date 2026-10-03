package com.fenixcore.optibienestar360.modules.promotion.dto;

import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.AppliesTo;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.Kind;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Create/replace body for a campaign promotion (V175). Cross-field rules are enforced by {@code PromotionService}. */
public record PromotionRequest(
        @NotBlank @Size(max = 150) String name,
        String description,
        @NotNull Kind kind,
        @NotNull @DecimalMin("0.01") @DecimalMax("100.00") BigDecimal discountPct,
        @NotNull AppliesTo appliesTo,
        @Min(1) Integer cycles,
        boolean coversExtraBeneficiaries,
        @Min(1) Integer maxRedemptions,
        boolean requiresCode,
        boolean acceptsPromoterCode,
        boolean acceptsMemberCode,
        boolean acceptsAllyCode,
        @DecimalMin("0.01") @DecimalMax("100.00") BigDecimal referrerRewardPct,
        @Min(1) Integer referrerRewardCycles,
        /** Eligible plans; empty or null = every plan. */
        List<UUID> planUuids
) {}
