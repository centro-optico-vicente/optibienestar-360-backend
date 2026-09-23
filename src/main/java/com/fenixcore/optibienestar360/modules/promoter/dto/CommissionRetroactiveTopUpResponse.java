package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fenixcore.optibienestar360.core.display.Display;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Response body for {@code POST /v1/admin/commissions/retroactive-topups}. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CommissionRetroactiveTopUpResponse(
        @Display(Display.Kind.DATE) LocalDate periodStart,
        @Display(Display.Kind.DATE) LocalDate periodEnd,
        @Display(Display.Kind.BOOLEAN) boolean dryRun,

        int totalTopUps,
        @Display(Display.Kind.MONEY) BigDecimal totalRetroAmount,
        String currency,
        @Display(Display.Kind.DATETIME) Instant executedAt,

        List<TopUpOutcome> topUps
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TopUpOutcome(
            UUID promoterUuid,
            String promoterCode,
            String promoterDisplayName,
            String ledgerType,
            @Display(Display.Kind.MONEY) BigDecimal basisAmount,
            @Display(Display.Kind.MONEY) BigDecimal targetAmount,
            @Display(Display.Kind.MONEY) BigDecimal alreadyPaidAmount,
            @Display(Display.Kind.MONEY) BigDecimal retroAmount,
            String targetTierName,

            /** Only populated in cut mode ({@code asOf} request) — null in legacy whole-period mode. */
            Integer cutSequence,
            @Display(Display.Kind.DATE) LocalDate accrualPeriodStart,
            @Display(Display.Kind.DATE) LocalDate accrualPeriodEnd,
            @Display(Display.Kind.DATE) LocalDate cutStart,
            @Display(Display.Kind.DATE) LocalDate cutEnd
    ) {
        /** Legacy whole-period outcome — no cut fields. */
        public TopUpOutcome(UUID promoterUuid, String promoterCode, String promoterDisplayName, String ledgerType,
                             BigDecimal basisAmount, BigDecimal targetAmount, BigDecimal alreadyPaidAmount,
                             BigDecimal retroAmount, String targetTierName) {
            this(promoterUuid, promoterCode, promoterDisplayName, ledgerType, basisAmount, targetAmount,
                    alreadyPaidAmount, retroAmount, targetTierName, null, null, null, null, null);
        }
    }
}
