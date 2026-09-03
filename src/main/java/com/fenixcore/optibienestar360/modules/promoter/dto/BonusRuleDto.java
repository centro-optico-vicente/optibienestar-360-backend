package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.core.display.DisplayRefs;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule.AccrualMode;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule.BonusMetric;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule.RewardType;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule.WindowStrategy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Admin read view of a bonus rule. {@code campaign*} present only for CAMPAIGN
 * windows; {@code flatAmount}/{@code rewardPct} mutually exclusive per reward type.
 * Scalars carry a localized {@code _Display} sibling (hub ADR 0014).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BonusRuleDto(
        UUID uuid,
        String name,
        String description,
        @Display DisplayRef promoterType,
        @Display(Display.Kind.ENUM) BonusMetric metric,
        @Display(Display.Kind.ENUM) AccrualMode accrual,
        int thresholdCount,
        @Display(Display.Kind.ENUM) WindowStrategy windowStrategy,
        @Display(Display.Kind.DATE) LocalDate campaignStart,
        @Display(Display.Kind.DATE) LocalDate campaignEnd,
        @Display(Display.Kind.ENUM) RewardType rewardType,
        @Display(Display.Kind.MONEY) BigDecimal flatAmount,
        @Display(Display.Kind.NUMBER) BigDecimal rewardPct,
        String rewardCurrency,
        @Display(Display.Kind.BOOLEAN) boolean includeSystemPromoters,
        @Display(Display.Kind.BOOLEAN) boolean active,
        @Display(Display.Kind.DATETIME) Instant createdAt
) {

    public static BonusRuleDto from(CommissionBonusRule r) {
        return new BonusRuleDto(
                r.getUuid(),
                r.getName(),
                r.getDescription(),
                DisplayRefs.ref(r.getPromoterType()),
                r.getMetric(),
                r.getAccrual(),
                r.getThresholdCount(),
                r.getWindowStrategy(),
                r.getCampaignStart(),
                r.getCampaignEnd(),
                r.getRewardType(),
                r.getFlatAmount(),
                r.getRewardPct(),
                r.getRewardCurrency(),
                r.isIncludeSystemPromoters(),
                r.isActive(),
                r.getCreatedAt());
    }
}
