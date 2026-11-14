package com.fenixcore.optibienestar360.modules.promotion.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.AppliesTo;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.DiscountType;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.Kind;
import com.fenixcore.optibienestar360.modules.promotion.entity.PromotionRedemption.RedemptionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A promotion applied to a membership (V175). {@code codeOwnerType} is PROMOTER/MEMBER/ALLY or null when no code was used. */
public record MembershipPromotionDto(
        UUID uuid,
        @Display DisplayRef promotion,
        @Display DisplayRef campaign,
        @Display(Display.Kind.ENUM) Kind kind,
        @Display(Display.Kind.ENUM) DiscountType discountType,
        @Display(Display.Kind.PERCENT) BigDecimal discountPct,
        @Display(value = Display.Kind.MONEY, moneyCurrencyField = "discountCurrency_Code") BigDecimal discountAmount,
        @Display DisplayRef discountCurrency,
        String discountCurrency_Code,
        @Display(Display.Kind.ENUM) AppliesTo appliesTo,
        Integer cyclesRemaining,
        boolean inscriptionApplied,
        String codeUsed,
        String codeOwnerType,
        @Display DisplayRef codeOwner,
        String origin,
        @Display(Display.Kind.ENUM) String status,
        @Display(Display.Kind.DATETIME) Instant assignedAt,
        @Display(Display.Kind.DATETIME) Instant endedAt,
        String endedReason,
        /** The slot of the campaign quota (V257); {@code null} only for rows without one. */
        @Display(value = Display.Kind.ENUM, enumScope = "promotion.redemptionStatus") RedemptionStatus redemptionStatus,
        /** Effective deadline of a PENDING slot (own deadline cut by the post-close grace); {@code null} otherwise. */
        @Display(Display.Kind.DATETIME) Instant redemptionExpiresAt,
        @Display(Display.Kind.DATETIME) Instant usageEndsAt
) {}
