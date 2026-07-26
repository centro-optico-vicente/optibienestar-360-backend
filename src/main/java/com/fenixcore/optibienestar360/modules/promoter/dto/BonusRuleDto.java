package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BonusRuleDto(
        UUID uuid,
        String name,
        String description,
        BonusMetric metric,
        AccrualMode accrual,
        int thresholdCount,
        WindowStrategy windowStrategy,
        LocalDate campaignStart,
        LocalDate campaignEnd,
        RewardType rewardType,
        BigDecimal flatAmount,
        BigDecimal rewardPct,
        String rewardCurrency,
        boolean includeSystemPromoters,
        boolean active,
        Instant createdAt
) {

    public static BonusRuleDto from(CommissionBonusRule r) {
        return new BonusRuleDto(
                r.getUuid(),
                r.getName(),
                r.getDescription(),
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
