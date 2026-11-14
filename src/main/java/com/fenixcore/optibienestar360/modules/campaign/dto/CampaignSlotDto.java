package com.fenixcore.optibienestar360.modules.campaign.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion;
import com.fenixcore.optibienestar360.modules.promotion.entity.PromotionRedemption.RedemptionStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * A slot of the campaign quota ({@code GET /v1/admin/campaigns/{uuid}/slots},
 * V260, hub ADR 0038 §9). {@code effectiveDeadline} is set only for a PENDING
 * slot (its own deadline cut by the post-close grace); {@code deadlineFrozen}
 * = a qualifying payment registered before that deadline is in review, so the
 * slot does not expire. {@code manualNote}/{@code manualBy} are set when an
 * admin confirmed or released it by hand.
 */
public record CampaignSlotDto(
        UUID uuid,
        @Display DisplayRef member,
        @Display DisplayRef membership,
        @Display DisplayRef promotion,
        @Display DisplayRef advisor,
        @Display(value = Display.Kind.ENUM, enumScope = "promotion.benefitKind") Promotion.BenefitKind benefitKind,
        @Display(value = Display.Kind.ENUM, enumScope = "promotion.redemptionStatus") RedemptionStatus redemptionStatus,
        @Display(value = Display.Kind.ENUM, enumScope = "campaign.quotaTrigger") Campaign.QuotaTrigger quotaTrigger,
        @Display(Display.Kind.DATETIME) Instant reservedAt,
        @Display(Display.Kind.DATETIME) Instant confirmedAt,
        @Display(Display.Kind.DATETIME) Instant effectiveDeadline,
        boolean deadlineFrozen,
        @Display DisplayRef confirmedByPayment,
        String manualNote,
        @Display DisplayRef manualBy
) {}
