package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.core.display.DisplayRefs;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionAwardSettlement;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionAwardSettlement.CutKind;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionAwardSettlement.SettlementStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CompetitiveAwardSettlementDto(
        UUID uuid,
        @Display(Display.Kind.ENUM) CutKind cutKind,
        int cutSequence,
        LocalDate cutStart,
        LocalDate cutEnd,
        Integer awardPositionAtCut,
        BigDecimal entitlementCumulative,
        BigDecimal alreadyPaidAmount,
        @Display(value = Display.Kind.MONEY, moneyCurrencyField = "currency_Code") BigDecimal amount,
        @Display DisplayRef currency,
        @Display(Display.Kind.DATETIME) Instant paidAt,
        String payoutReference,
        String voidReason,
        @Display(Display.Kind.ENUM) SettlementStatus status) {

    public static CompetitiveAwardSettlementDto from(CompetitiveCommissionAwardSettlement settlement) {
        return new CompetitiveAwardSettlementDto(
                settlement.getUuid(),
                settlement.getCutKind(),
                settlement.getCutSequence(),
                settlement.getCutStart(),
                settlement.getCutEnd(),
                settlement.getAwardPositionAtCut(),
                settlement.getEntitlementCumulative(),
                settlement.getAlreadyPaidAmount(),
                settlement.getAmount(),
                DisplayRefs.ref(settlement.getCurrency()),
                settlement.getPaidAt(),
                settlement.getPayoutReference(),
                settlement.getVoidReason(),
                SettlementStatus.valueOf(settlement.getStatus()));
    }
}
