package com.fenixcore.optibienestar360.modules.promotion.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.AppliesTo;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.DiscountType;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.Kind;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A campaign promotion; {@code discountPct} is null for a FLAT discount, {@code discountAmount}/{@code discountCurrency} are null for a PERCENTAGE one. */
public record PromotionDto(
        UUID uuid,
        @Display DisplayRef campaign,
        String name,
        String description,
        @Display(Display.Kind.ENUM) Kind kind,
        @Display(Display.Kind.ENUM) DiscountType discountType,
        @Display(Display.Kind.PERCENT) BigDecimal discountPct,
        @Display(value = Display.Kind.MONEY, moneyCurrencyField = "discountCurrency_Code") BigDecimal discountAmount,
        @Display DisplayRef discountCurrency,
        String discountCurrency_Code,
        @Display(Display.Kind.ENUM) AppliesTo appliesTo,
        Integer cycles,
        boolean coversExtraBeneficiaries,
        Integer maxRedemptions,
        int redemptionsCount,
        boolean requiresCode,
        boolean acceptsPromoterCode,
        boolean acceptsMemberCode,
        boolean acceptsAllyCode,
        @Display(Display.Kind.PERCENT) BigDecimal referrerRewardPct,
        Integer referrerRewardCycles,
        List<DisplayRef> plans,
        @Display(Display.Kind.BOOLEAN) boolean active,
        @Display(Display.Kind.DATETIME) Instant createdAt
) {}
