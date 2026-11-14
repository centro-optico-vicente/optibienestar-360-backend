package com.fenixcore.optibienestar360.modules.promotion.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A campaign an ally can add its own benefits to from the ally portal (V257,
 * hub ADR 0038 §8). {@code exclusive} = the campaign belongs to the caller
 * ally; {@code freeSlots} is {@code null} when the campaign has no quota.
 */
public record AllyPartnershipCampaignDto(
        UUID uuid,
        String name,
        String description,
        @Display(Display.Kind.DATETIME) OffsetDateTime startsAt,
        @Display(Display.Kind.DATETIME) OffsetDateTime endsAt,
        @Display(value = Display.Kind.ENUM, enumScope = "campaign.lifecycleStatus") Campaign.LifecycleStatus lifecycleStatus,
        Integer maxRedemptions,
        Integer freeSlots,
        boolean exclusive
) {}
