package com.fenixcore.optibienestar360.modules.campaign.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;

import java.util.UUID;

/**
 * Output row for {@code GET/POST /v1/admin/campaigns/{uuid}/audience} — one
 * {@code CampaignPromoter} bridge row. {@code uuid} is the bridge row's own
 * uuid (used for nothing today — deletion routes by {@code promoterUuid}
 * instead, since that's what the admin UI actually holds); {@code promoter}
 * is the audience member.
 */
public record CampaignAudienceDto(
        UUID uuid,
        @Display DisplayRef promoter,
        @Display DisplayRef promoterType,
        @Display DisplayRef rank) {
}
