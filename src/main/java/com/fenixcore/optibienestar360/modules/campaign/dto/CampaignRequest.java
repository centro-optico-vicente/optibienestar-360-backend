package com.fenixcore.optibienestar360.modules.campaign.dto;

import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Shared create/update body — {@code CampaignService} validates the cross-field rules.
 *
 * <p>Lifecycle and quota (V256, hub ADR 0038): {@code endsAt} is optional;
 * {@code closeConditions} null means {@code [END_DATE]} when {@code endsAt}
 * is set, else {@code []} (closes only by hand); {@code draft} is read on
 * create only (true = DRAFT, else ACTIVE).</p>
 */
public record CampaignRequest(
        @NotBlank String name,
        String description,
        @NotNull OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        Boolean enabled,
        @NotNull Campaign.CampaignScope scope,
        @NotNull Campaign.CampaignMode mode,
        boolean evaluateOnlyAtEnd,
        boolean payOnlyAtEnd,
        BigDecimal targetAmount,
        /** Required alongside {@code targetAmount} — the currency the goal is expressed in. */
        UUID targetAmountCurrencyUuid,
        Integer targetCount,
        String exclusivityGroup,
        Integer priority,
        List<UUID> promoterUuids,
        List<Campaign.CloseCondition> closeConditions,
        @Min(1) Integer maxRedemptions,
        OffsetDateTime confirmGraceUntil,
        @Min(1) Integer confirmGraceDays,
        Boolean draft,
        /** When a reserved slot is confirmed (V257); null = ON_ENROLLMENT. */
        Campaign.QuotaTrigger quotaTrigger,
        @Min(1) Integer pendingTtlDays,
        /** How long a confirmed slot keeps its benefit (V257); null = CAMPAIGN_DATES. */
        Campaign.UsageWindow usageWindow,
        @Min(1) Integer usageWindowDays) {
}
