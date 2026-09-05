package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.core.display.DisplayRefs;
import com.fenixcore.optibienestar360.modules.currency.service.ConversionEnricher;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.PeriodStrategy;
import com.fenixcore.optibienestar360.modules.promoter.entity.LeaderboardPrizeAward;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Output DTO for one granted-and/or-paid {@link LeaderboardPrizeAward} row
 * (v2 PDF #5, V42/V92) — response of the mark-as-paid endpoint. Distinct
 * from {@link LeaderboardPrizeDto}, which is the prize <em>configuration</em>
 * (per rank/strategy), not an individual award instance.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LeaderboardPrizeAwardDto(
        UUID uuid,
        @Display DisplayRef promoter,
        @Display(Display.Kind.ENUM) PeriodStrategy periodStrategy,
        @Display(Display.Kind.DATE) LocalDate periodStart,
        @Display(Display.Kind.DATE) LocalDate periodEnd,
        int rank,
        @Display(Display.Kind.MONEY) BigDecimal metricAmount,
        @Display(value = Display.Kind.MONEY, moneyCurrencyField = "currency_Code") BigDecimal prizeAmount,
        String currency_Code,
        @Display(value = Display.Kind.MONEY, moneyCurrencyField = "convertedCurrency_Code") BigDecimal amountConverted,
        String convertedCurrency_Code,
        BigDecimal exchangeRateUsed,
        @Display(Display.Kind.DATE) LocalDate exchangeRateDate,
        @Display(value = Display.Kind.ENUM, enumScope = "leaderboard_prize_award.status") String status,
        @Display(Display.Kind.DATETIME) Instant awardedAt,
        @Display(Display.Kind.DATETIME) Instant paidAt
) {
    public static LeaderboardPrizeAwardDto from(LeaderboardPrizeAward a) {
        return from(a, null);
    }

    /** {@code enricher} is only used to label {@code convertedCurrency_Code} when a snapshot exists — the snapshot itself never records the target currency, only the rate/date. */
    public static LeaderboardPrizeAwardDto from(LeaderboardPrizeAward a, ConversionEnricher enricher) {
        BigDecimal amountConverted = a.getExchangeRateUsed() != null
                ? a.getPrizeAmount().multiply(a.getExchangeRateUsed()).setScale(2, RoundingMode.HALF_UP)
                : null;
        String convertedCurrencyCode = a.getExchangeRateUsed() != null && enricher != null
                ? enricher.officialCurrencyCode() : null;
        return new LeaderboardPrizeAwardDto(
                a.getUuid(),
                DisplayRefs.ref(a.getPromoter()),
                a.getPeriodStrategy(),
                a.getPeriodStart(),
                a.getPeriodEnd(),
                a.getRank(),
                a.getMetricAmount(),
                a.getPrizeAmount(),
                a.getPrizeCurrency().getCode(),
                amountConverted,
                convertedCurrencyCode,
                a.getExchangeRateUsed(),
                a.getExchangeRateDate(),
                a.getStatus(),
                a.getAwardedAt(),
                a.getPaidAt());
    }
}
