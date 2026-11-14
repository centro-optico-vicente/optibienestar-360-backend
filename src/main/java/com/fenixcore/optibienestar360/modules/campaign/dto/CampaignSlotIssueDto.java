package com.fenixcore.optibienestar360.modules.campaign.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.modules.promotion.entity.PromotionRedemption.RedemptionStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * One inconsistency between the campaign slots and their payments
 * ({@code GET /v1/admin/campaigns/{uuid}/slot-issues}, V260, hub ADR 0038 §9).
 * {@code since} = when the issue started (the payment approval, or the moment
 * the payment entered review for a frozen slot).
 */
public record CampaignSlotIssueDto(
        @Display(value = Display.Kind.ENUM, enumScope = "campaign.slotIssue") IssueType type,
        @Display DisplayRef member,
        @Display DisplayRef membership,
        @Display DisplayRef promotion,
        @Display DisplayRef payment,
        UUID slotUuid,
        @Display(value = Display.Kind.ENUM, enumScope = "promotion.redemptionStatus") RedemptionStatus redemptionStatus,
        @Display(Display.Kind.DATETIME) Instant since
) {

    public enum IssueType {
        /** An approved payment carries this campaign's discount but its slot is not CONFIRMED. */
        APPROVED_DISCOUNT_WITHOUT_SLOT,
        /** A PENDING slot whose qualifying payment was already registered/approved in time. */
        PENDING_WITH_APPROVED_PAYMENT,
        /** A PENDING slot frozen by a payment in review for more than 7 days. */
        FROZEN_TOO_LONG
    }
}
