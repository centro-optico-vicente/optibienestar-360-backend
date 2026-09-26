package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.core.display.DisplayRefs;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionAward;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRulePosition.RewardType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CompetitiveAwardDto(
        UUID uuid,
        @Display DisplayRef rule,
        @Display DisplayRef promoter,
        LocalDate periodStart,
        LocalDate periodEnd,
        int awardPosition,
        short tieGroupSize,
        BigDecimal metricValue,
        int metricTransactionCount,
        @Display(Display.Kind.DATETIME) Instant achievedAt,
        @Display(Display.Kind.ENUM) RewardType rewardType,
        @Display(value = Display.Kind.MONEY, moneyCurrencyField = "currency_Code") BigDecimal amount,
        @Display DisplayRef currency,
        @Display(Display.Kind.DATETIME) Instant paidAt,
        String payoutReference,
        String voidReason,
        String adminNotes,
        @Display(Display.Kind.ENUM) CompetitiveCommissionAward.SelectionSource selectionSource,
        @Display(Display.Kind.ENUM) CompetitiveCommissionAward.AwardStatus status) {

    public static CompetitiveAwardDto from(CompetitiveCommissionAward award) {
        return new CompetitiveAwardDto(
                award.getUuid(),
                DisplayRefs.ref(award.getRule()),
                DisplayRefs.ref(award.getPromoter()),
                award.getPeriodStart(),
                award.getPeriodEnd(),
                award.getAwardPosition(),
                award.getTieGroupSize(),
                award.getMetricValue(),
                award.getMetricTransactionCount(),
                award.getAchievedAt(),
                award.getRewardType(),
                award.getAmount(),
                DisplayRefs.ref(award.getCurrency()),
                award.getPaidAt(),
                award.getPayoutReference(),
                award.getVoidReason(),
                award.getAdminNotes(),
                award.getSelectionSource(),
                CompetitiveCommissionAward.AwardStatus.valueOf(award.getStatus()));
    }
}
