package com.fenixcore.optibienestar360.modules.campaign.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;

import java.util.List;

/**
 * Quota counters of a campaign and of each promotion (V257, hub ADR 0038 §5),
 * for {@code GET /v1/admin/campaigns/{uuid}/quota}. {@code free} is
 * {@code null} when there is no cap; a promotion with its own quota is
 * bounded by the smaller of its own free slots and the campaign's.
 */
public record CampaignQuotaDto(
        Integer maxRedemptions,
        int confirmed,
        int pending,
        Integer free,
        int expired,
        int released,
        List<PromotionQuota> promotions) {

    public record PromotionQuota(
            @Display DisplayRef promotion,
            boolean ownQuota,
            Integer maxRedemptions,
            int confirmed,
            int pending,
            Integer free,
            int expired,
            int released) {
    }
}
