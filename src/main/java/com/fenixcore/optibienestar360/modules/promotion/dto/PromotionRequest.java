package com.fenixcore.optibienestar360.modules.promotion.dto;

import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.AppliesTo;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.BenefitKind;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.BenefitType;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.DiscountType;
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

/**
 * Create/replace body for a campaign promotion (V175, V255). Cross-field rules
 * are enforced by {@code PromotionService}: {@code discountType} null means
 * PERCENTAGE; PERCENTAGE requires {@code discountPct}, FLAT requires
 * {@code discountAmount} + {@code discountCurrencyUuid}.
 *
 * <p>Ally benefit (V257): {@code benefitKind} null means DISCOUNT (which
 * requires {@code appliesTo}); ALLY_BENEFIT requires {@code allyProductUuid}
 * and {@code benefitType} (plus {@code allyUuid} unless the campaign is
 * exclusive to an ally) and takes no discount fields.</p>
 */
public record PromotionRequest(
        @NotBlank @Size(max = 150) String name,
        String description,
        @NotNull Kind kind,
        DiscountType discountType,
        @DecimalMin("0.01") @DecimalMax("100.00") BigDecimal discountPct,
        @DecimalMin("0.01") BigDecimal discountAmount,
        UUID discountCurrencyUuid,
        AppliesTo appliesTo,
        @Min(1) Integer cycles,
        boolean coversExtraBeneficiaries,
        /** Own quota (V256); {@code maxRedemptions} is only accepted when true. */
        boolean ownQuota,
        @Min(1) Integer maxRedemptions,
        boolean requiresCode,
        boolean acceptsPromoterCode,
        boolean acceptsMemberCode,
        boolean acceptsAllyCode,
        @DecimalMin("0.01") @DecimalMax("100.00") BigDecimal referrerRewardPct,
        @Min(1) Integer referrerRewardCycles,
        /** Eligible plans; empty or null = every plan. */
        List<UUID> planUuids,
        BenefitKind benefitKind,
        UUID allyUuid,
        UUID allyProductUuid,
        BenefitType benefitType,
        @DecimalMin("0.01") @DecimalMax("100.00") BigDecimal benefitPct,
        @DecimalMin("0.01") BigDecimal benefitAmount,
        UUID benefitCurrencyUuid,
        @Min(1) Integer benefitQuantity
) {}
