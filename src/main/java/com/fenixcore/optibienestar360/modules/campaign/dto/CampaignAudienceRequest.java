package com.fenixcore.optibienestar360.modules.campaign.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Body of {@code POST /v1/admin/campaigns/{uuid}/audience}. */
public record CampaignAudienceRequest(@NotNull UUID promoterUuid) {
}
