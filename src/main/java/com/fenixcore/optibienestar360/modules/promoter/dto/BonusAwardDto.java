package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule.RewardType;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterBonusAward;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Read view of a granted bonus — shared by the admin awards queue
 * ({@code GET /v1/admin/bonus-awards}) and the promoter self-service list
 * ({@code GET /v1/promoter/me/bonuses}). Reward fields are the inline snapshot
 * taken at grant time; {@code basisAmount}/{@code rewardPct} present only for
 * PERCENTAGE rewards.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BonusAwardDto(
        UUID uuid,
        UUID ruleUuid,
        String ruleName,
        UUID promoterUuid,
        String promoterDisplayName,
        int metricCount,
        int blocksAwarded,
        LocalDate windowStart,
        LocalDate windowEnd,
        RewardType rewardType,
        BigDecimal flatAmount,
        BigDecimal rewardPct,
        BigDecimal basisAmount,
        BigDecimal amount,
        String currency,
        String status,
        Instant evaluatedAt,
        Instant createdAt
) {

    public static BonusAwardDto from(PromoterBonusAward a) {
        Promoter promoter = a.getPromoter();
        return new BonusAwardDto(
                a.getUuid(),
                a.getRule() != null ? a.getRule().getUuid() : null,
                a.getRuleNameSnapshot(),
                promoter != null ? promoter.getUuid() : null,
                promoter != null ? promoter.getDisplayName() : null,
                a.getMetricCount(),
                a.getBlocksAwarded(),
                a.getWindowStart(),
                a.getWindowEnd(),
                a.getRewardType(),
                a.getFlatAmount(),
                a.getRewardPct(),
                a.getBasisAmount(),
                a.getAmount(),
                a.getRewardCurrency(),
                a.getStatus(),
                a.getEvaluatedAt(),
                a.getCreatedAt());
    }
}
