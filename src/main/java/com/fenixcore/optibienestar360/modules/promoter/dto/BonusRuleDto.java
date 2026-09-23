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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Admin read view of a bonus rule. {@code campaign*} present only for CAMPAIGN
 * windows; {@code flatAmount}/{@code rewardPct} mutually exclusive per reward type;
 * {@code thresholdCount} only set for count metrics, {@code thresholdAmount}/
 * {@code thresholdCurrency} only set for {@code AMOUNT_COLLECTED} (I-BE, hub plan
 * Part I). Scalars carry a localized {@code _Display} sibling (hub ADR 0014).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BonusRuleDto(
        UUID uuid,
        String name,
        String description,
        /** M:N promoter-type scope (V137, hub plan Part F) — empty = applies to all. */
        List<DisplayRef> promoterTypes,
        @Display(Display.Kind.ENUM) BonusMetric metric,
        @Display(Display.Kind.ENUM) AccrualMode accrual,
        int thresholdCount,
        @Display(value = Display.Kind.MONEY, moneyCurrencyField = "thresholdCurrency_Code") BigDecimal thresholdAmount,
        @Display DisplayRef thresholdCurrency,
        String thresholdCurrency_Code,
        @Display(Display.Kind.ENUM) WindowStrategy accrualPeriodStrategy,
        @Display(Display.Kind.ENUM) WindowStrategy partialSettlementPeriodStrategy,
        @Display(Display.Kind.ENUM) WindowStrategy finalSettlementPeriodStrategy,
        @Display(Display.Kind.ENUM) WindowStrategy retroactiveSettlementPeriodStrategy,
        Short accrualPeriodAnchor,
        Short partialSettlementPeriodAnchor,
        Short finalSettlementPeriodAnchor,
        Short retroactiveSettlementPeriodAnchor,
        @Display(Display.Kind.DATETIME) OffsetDateTime campaignStart,
        @Display(Display.Kind.DATETIME) OffsetDateTime campaignEnd,
        @Display(Display.Kind.ENUM) RewardType rewardType,
        @Display(Display.Kind.MONEY) BigDecimal flatAmount,
        @Display(Display.Kind.NUMBER) BigDecimal rewardPct,
        String rewardCurrency,
		/** Same currency as {@code rewardCurrency}, exposed as a selectable FK ref (uuid) for the form. */
		@Display DisplayRef rewardCurrencyRef,
        @Display(Display.Kind.BOOLEAN) boolean includeSystemPromoters,
        /** Formal campaign anchor (V120) — {@code null} = a standing rule not tied to a Campaign. */
        @Display DisplayRef campaign,
        @Display(Display.Kind.DATETIME) OffsetDateTime startsAt,
        @Display(Display.Kind.DATETIME) OffsetDateTime endsAt,
        @Display(Display.Kind.BOOLEAN) boolean active,
        @Display(Display.Kind.DATETIME) Instant createdAt
) {

    public static BonusRuleDto from(CommissionBonusRule r) {
        return new BonusRuleDto(
                r.getUuid(),
                r.getName(),
                r.getDescription(),
                r.getPromoterTypes().stream().map(DisplayRefs::ref).toList(),
                r.getMetric(),
                r.getAccrual(),
                r.getThresholdCount(),
                r.getThresholdAmount(),
                DisplayRefs.ref(r.getThresholdCurrency()),
                r.getThresholdCurrency() != null ? r.getThresholdCurrency().getCode() : null,
                r.getAccrualPeriodStrategy(), r.getPartialSettlementPeriodStrategy(),
                r.getFinalSettlementPeriodStrategy(), r.getRetroactiveSettlementPeriodStrategy(),
                r.getAccrualPeriodAnchor(), r.getPartialSettlementPeriodAnchor(),
                r.getFinalSettlementPeriodAnchor(), r.getRetroactiveSettlementPeriodAnchor(),
                r.getCampaignStart(),
                r.getCampaignEnd(),
                r.getRewardType(),
                r.getFlatAmount(),
                r.getRewardPct(),
                r.getRewardCurrency().getCode(),
                DisplayRefs.ref(r.getRewardCurrency()),
                r.isIncludeSystemPromoters(),
                DisplayRefs.ref(r.getCampaign()),
                r.getStartsAt(),
                r.getEndsAt(),
                r.isActive(),
                r.getCreatedAt());
    }
}
