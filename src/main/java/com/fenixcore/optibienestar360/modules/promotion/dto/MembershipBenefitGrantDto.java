package com.fenixcore.optibienestar360.modules.promotion.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.modules.promotion.entity.MembershipBenefitGrant.GrantStatus;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.BenefitType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * An ally voucher of a membership (V257, hub ADR 0038 §7): shown to the ally
 * in the validator ({@code benefitGrants}, AVAILABLE ones of the calling ally)
 * and to the member in {@code GET /v1/me/benefit-grants}.
 */
public record MembershipBenefitGrantDto(
        UUID uuid,
        @Display DisplayRef promotion,
        @Display DisplayRef campaign,
        @Display DisplayRef ally,
        @Display DisplayRef allyProduct,
        @Display(value = Display.Kind.ENUM, enumScope = "promotion.benefitType") BenefitType benefitType,
        @Display(Display.Kind.PERCENT) BigDecimal benefitPct,
        @Display(value = Display.Kind.MONEY, moneyCurrencyField = "benefitCurrency_Code") BigDecimal benefitAmount,
        @Display DisplayRef benefitCurrency,
        String benefitCurrency_Code,
        int quantity,
        int remainingQty,
        @Display(value = Display.Kind.ENUM, enumScope = "promotion.grantStatus") GrantStatus grantStatus,
        @Display(Display.Kind.DATETIME) Instant grantedAt,
        @Display(Display.Kind.DATETIME) Instant expiresAt,
        @Display(Display.Kind.DATETIME) Instant redeemedAt
) {}
