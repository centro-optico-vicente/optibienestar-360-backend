package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.core.display.DisplayRefs;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRulePosition;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRulePosition.RewardType;

import java.math.BigDecimal;
import java.util.UUID;

public record CompetitiveRulePositionDto(
        UUID uuid,
        int positionFrom,
        int positionTo,
        String label,
        @Display(Display.Kind.ENUM) RewardType rewardType,
        @Display(value = Display.Kind.MONEY, moneyCurrencyField = "rewardCurrency_Code") BigDecimal flatAmount,
        @Display(Display.Kind.NUMBER) BigDecimal rewardPct,
        @Display DisplayRef rewardCurrency,
        String rewardCurrency_Code,
        @Display(value = Display.Kind.MONEY, moneyCurrencyField = "rewardCurrency_Code") BigDecimal rewardMinAmount,
        @Display(value = Display.Kind.MONEY, moneyCurrencyField = "rewardCurrency_Code") BigDecimal rewardMaxAmount,
        Integer minThresholdCount,
        BigDecimal minThresholdAmount
) {
    public static CompetitiveRulePositionDto from(CompetitiveCommissionRulePosition p) {
        return new CompetitiveRulePositionDto(
                p.getUuid(), p.getPositionFrom(), p.getPositionTo(), p.getLabel(),
                p.getRewardType(), p.getFlatAmount(), p.getRewardPct(),
                DisplayRefs.ref(p.getRewardCurrency()),
                p.getRewardCurrency() != null ? p.getRewardCurrency().getCode() : null,
                p.getRewardMinAmount(), p.getRewardMaxAmount(),
                p.getMinThresholdCount(), p.getMinThresholdAmount());
    }
}
