package com.fenixcore.optibienestar360.modules.campaign.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

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
        List<UUID> promoterUuids) {
}
