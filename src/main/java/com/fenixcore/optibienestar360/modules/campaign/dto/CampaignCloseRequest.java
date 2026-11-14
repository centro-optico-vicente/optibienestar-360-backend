package com.fenixcore.optibienestar360.modules.campaign.dto;

import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /v1/admin/campaigns/{uuid}/close} (hub ADR 0038 §6):
 * {@code cancelPending} also releases the slots still waiting for
 * confirmation instead of letting them run until their deadline.
 */
public record CampaignCloseRequest(
        boolean cancelPending,
        @Size(max = 500) String note) {
}
