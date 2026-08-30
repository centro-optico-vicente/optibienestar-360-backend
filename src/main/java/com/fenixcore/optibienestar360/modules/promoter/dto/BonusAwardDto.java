package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fenixcore.optibienestar360.core.display.Display;
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
 * taken at grant time. Scalars carry a localized {@code _Display} sibling (hub ADR 0014).
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
        @Display(Display.Kind.DATE) LocalDate windowStart,
        @Display(Display.Kind.DATE) LocalDate windowEnd,
        @Display(Display.Kind.ENUM) RewardType rewardType,
        @Display(Display.Kind.MONEY) BigDecimal flatAmount,
        @Display(Display.Kind.NUMBER) BigDecimal rewardPct,
        @Display(Display.Kind.MONEY) BigDecimal basisAmount,
        @Display(Display.Kind.MONEY) BigDecimal amount,
        String currency,
        @Display(value = Display.Kind.ENUM, enumScope = "bonus_award.status") String status,
        @Display(Display.Kind.DATETIME) Instant evaluatedAt,
        @Display(Display.Kind.DATETIME) Instant createdAt
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
