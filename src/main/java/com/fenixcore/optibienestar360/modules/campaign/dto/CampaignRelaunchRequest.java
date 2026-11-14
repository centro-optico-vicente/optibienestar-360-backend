package com.fenixcore.optibienestar360.modules.campaign.dto;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

/**
 * New dates for the relaunched clone — every other field is copied from the
 * source campaign. {@code endsAt} is optional: without it the new edition is
 * open-ended (it drops the END_DATE close condition).
 */
public record CampaignRelaunchRequest(
        @NotNull OffsetDateTime startsAt,
        OffsetDateTime endsAt) {
}
