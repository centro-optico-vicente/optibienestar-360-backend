package com.fenixcore.optibienestar360.modules.campaign.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.modules.promotion.entity.PromotionRedemption.RedemptionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One slot of the campaign with the payment that confirms (or confirmed) it,
 * for the campaign "Pagos" section (V260, hub ADR 0038 §9). The payment
 * fields are {@code null} for a PENDING slot without a payment yet.
 * {@code confirmsOnApproval} = approving the payment confirms the slot.
 */
public record CampaignPaymentRowDto(
        @Display DisplayRef payment,
        String paymentReference,
        @Display(value = Display.Kind.MONEY, moneyCurrencyField = "amountCurrency_Code") BigDecimal amount,
        String amountCurrency_Code,
        @Display(Display.Kind.DATE) LocalDate paymentDate,
        @Display(value = Display.Kind.ENUM, enumScope = "payment.status") String paymentStatus,
        @Display DisplayRef member,
        @Display DisplayRef membership,
        @Display DisplayRef advisor,
        @Display DisplayRef promotion,
        UUID slotUuid,
        @Display(value = Display.Kind.ENUM, enumScope = "promotion.redemptionStatus") RedemptionStatus redemptionStatus,
        @Display(Display.Kind.DATETIME) Instant effectiveDeadline,
        boolean deadlineFrozen,
        boolean confirmsOnApproval,
        @Display(Display.Kind.DATETIME) Instant lastRemindedAt
) {}
