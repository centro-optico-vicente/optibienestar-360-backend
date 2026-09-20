package com.fenixcore.optibienestar360.modules.campaign.dto;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

/** New dates for the relaunched clone — every other field is copied from the source campaign. */
public record CampaignRelaunchRequest(
        @NotNull OffsetDateTime startsAt,
        @NotNull OffsetDateTime endsAt) {
}
