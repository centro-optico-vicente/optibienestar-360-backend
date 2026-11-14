package com.fenixcore.optibienestar360.modules.campaign.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A campaign. Lifecycle and quota (V256, hub ADR 0038): {@code endsAt} is
 * {@code null} for an open-ended campaign; {@code freeSlots} is {@code null}
 * when the campaign has no quota.
 */
public record CampaignDto(
        UUID uuid,
        String name,
        String description,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        boolean enabled,
        Campaign.CampaignScope scope,
        Campaign.CampaignMode mode,
        boolean evaluateOnlyAtEnd,
        boolean payOnlyAtEnd,
        @Display(value = Display.Kind.MONEY, moneyCurrencyField = "targetAmountCurrency_Code") BigDecimal targetAmount,
        @Display DisplayRef targetAmountCurrency,
        String targetAmountCurrency_Code,
        Integer targetCount,
        String exclusivityGroup,
        Integer priority,
        boolean active,
        List<UUID> promoterUuids,
        @Display(value = Display.Kind.ENUM, enumScope = "campaign.lifecycleStatus") Campaign.LifecycleStatus lifecycleStatus,
        List<Campaign.CloseCondition> closeConditions,
        Integer maxRedemptions,
        int redemptionsCount,
        int confirmedCount,
        Integer freeSlots,
        @Display(Display.Kind.DATETIME) OffsetDateTime closedAt,
        @Display(value = Display.Kind.ENUM, enumScope = "campaign.closedReason") Campaign.CloseReason closedReason,
        @Display DisplayRef closedBy,
        String closedNote,
        OffsetDateTime confirmGraceUntil,
        Integer confirmGraceDays,
        @Display(value = Display.Kind.ENUM, enumScope = "campaign.quotaTrigger") Campaign.QuotaTrigger quotaTrigger,
        Integer pendingTtlDays,
        @Display(value = Display.Kind.ENUM, enumScope = "campaign.usageWindow") Campaign.UsageWindow usageWindow,
        Integer usageWindowDays,
        /** Slots reserved and still waiting for their confirmation (occupied − confirmed). */
        int pendingCount,
        /** The ally the campaign is exclusive to (V257); {@code null} = several allies. */
        @Display DisplayRef ally,
        boolean allowsAllyPartnership) {
}
