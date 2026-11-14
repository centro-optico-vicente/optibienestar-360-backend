package com.fenixcore.optibienestar360.modules.campaign.dto;

import jakarta.validation.constraints.Min;

import java.time.OffsetDateTime;

/**
 * Body of {@code POST /v1/admin/campaigns/{uuid}/reopen} (hub ADR 0038 §3):
 * a closed campaign reopens only when the quota grows above the current one
 * or the end date moves past now and past the current end.
 */
public record CampaignReopenRequest(
        @Min(1) Integer maxRedemptions,
        OffsetDateTime endsAt) {
}
