package com.fenixcore.optibienestar360.modules.promotion.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.BenefitType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * An ally benefit of the validated membership whose slot is still PENDING
 * ({@code pendingBenefits} of the validator, V260, hub ADR 0038 §9):
 * information only, it cannot be redeemed until the slot is confirmed.
 */
public record PendingBenefitDto(
        @Display DisplayRef promotion,
        @Display DisplayRef campaign,
        @Display DisplayRef allyProduct,
        @Display(value = Display.Kind.ENUM, enumScope = "promotion.benefitType") BenefitType benefitType,
        @Display(Display.Kind.PERCENT) BigDecimal benefitPct,
        @Display(value = Display.Kind.MONEY, moneyCurrencyField = "benefitCurrency_Code") BigDecimal benefitAmount,
        String benefitCurrency_Code,
        int quantity,
        @Display(value = Display.Kind.ENUM, enumScope = "campaign.quotaTrigger") Campaign.QuotaTrigger quotaTrigger,
        @Display(Display.Kind.DATETIME) Instant effectiveDeadline,
        boolean deadlineFrozen
) {}
