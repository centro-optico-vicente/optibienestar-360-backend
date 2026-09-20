package com.fenixcore.optibienestar360.modules.campaign.dto;

import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Shared create/update body — {@code CampaignService} validates the cross-field rules. */
public record CampaignRequest(
        @NotBlank String name,
        String description,
        @NotNull OffsetDateTime startsAt,
        @NotNull OffsetDateTime endsAt,
        Boolean enabled,
        @NotNull Campaign.CampaignScope scope,
        @NotNull Campaign.CampaignMode mode,
        boolean evaluateOnlyAtEnd,
        boolean payOnlyAtEnd,
        BigDecimal targetAmount,
        Integer targetCount,
        String exclusivityGroup,
        Integer priority,
        List<UUID> promoterUuids) {
}
