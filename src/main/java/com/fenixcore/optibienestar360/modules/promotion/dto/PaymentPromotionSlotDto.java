package com.fenixcore.optibienestar360.modules.promotion.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
import com.fenixcore.optibienestar360.modules.promotion.entity.PromotionRedemption.RedemptionStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * The campaign slot a payment confirmed or may confirm ({@code promotionSlot}
 * of the payment detail, V260, hub ADR 0038 §9). {@code confirmsOnApproval} =
 * the payment is in review, the slot is PENDING, the campaign confirms on the
 * approved payment and this payment qualifies for it.
 */
public record PaymentPromotionSlotDto(
        UUID slotUuid,
        @Display DisplayRef promotion,
        @Display DisplayRef campaign,
        @Display(value = Display.Kind.ENUM, enumScope = "promotion.redemptionStatus") RedemptionStatus redemptionStatus,
        @Display(value = Display.Kind.ENUM, enumScope = "campaign.quotaTrigger") Campaign.QuotaTrigger quotaTrigger,
        boolean confirmsOnApproval,
        @Display(Display.Kind.DATETIME) Instant effectiveDeadline,
        boolean deadlineFrozen
) {}
