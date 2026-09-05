package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.core.display.DisplayRefs;
import com.fenixcore.optibienestar360.modules.currency.service.ConversionEnricher;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule.RewardType;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterBonusAward;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
        @Display DisplayRef rule,
        @Display DisplayRef promoter,
        int metricCount,
        int blocksAwarded,
        @Display(Display.Kind.DATE) LocalDate windowStart,
        @Display(Display.Kind.DATE) LocalDate windowEnd,
        @Display(Display.Kind.ENUM) RewardType rewardType,
        @Display(Display.Kind.MONEY) BigDecimal flatAmount,
        @Display(Display.Kind.NUMBER) BigDecimal rewardPct,
        @Display(Display.Kind.MONEY) BigDecimal basisAmount,
        @Display(value = Display.Kind.MONEY, moneyCurrencyField = "currency_Code") BigDecimal amount,
        String currency_Code,
        // ADR 0015 §6: PAID rows read the persisted payout snapshot (Caso A,
        // never recomputed); PENDING/VOIDED rows get a live conversion to the
        // organization's official currency (Caso B) via ConversionEnricher.
        @Display(value = Display.Kind.MONEY, moneyCurrencyField = "convertedCurrency_Code") BigDecimal amountConverted,
        String convertedCurrency_Code,
        BigDecimal exchangeRateUsed,
        @Display(Display.Kind.DATE) LocalDate exchangeRateDate,
        @Display(value = Display.Kind.ENUM, enumScope = "bonus_award.status") String status,
        @Display(Display.Kind.DATETIME) Instant evaluatedAt,
        @Display(Display.Kind.DATETIME) Instant createdAt
) {

    /** Basic mapping, conversion fields left null — see {@link #from(PromoterBonusAward, ConversionEnricher)} for the enriched read path. */
    public static BonusAwardDto from(PromoterBonusAward a) {
        return from(a, null);
    }

    /**
     * {@code enricher} is only consulted for PENDING/VOIDED rows (Caso B, live
     * conversion) — a PAID row always reads its own persisted snapshot
     * (Caso A). Pass {@code null} to skip enrichment entirely (conversion
     * fields stay null).
     */
    public static BonusAwardDto from(PromoterBonusAward a, ConversionEnricher enricher) {
        Promoter promoter = a.getPromoter();

        BigDecimal amountConverted = null;
        String convertedCurrencyCode = null;
        BigDecimal exchangeRateUsed = null;
        LocalDate exchangeRateDate = null;

        if (PromoterBonusAward.AwardStatus.PAID.name().equals(a.getStatus())) {
            exchangeRateUsed = a.getExchangeRateUsed();
            exchangeRateDate = a.getExchangeRateDate();
            if (exchangeRateUsed != null) {
                amountConverted = a.getAmount().multiply(exchangeRateUsed).setScale(2, RoundingMode.HALF_UP);
                // Snapshot never records which currency it converted to (only the rate/date) —
                // it's always the org's official currency at pay time, per BonusAwardsService.pay.
                convertedCurrencyCode = enricher != null ? enricher.officialCurrencyCode() : null;
            }
        } else if (enricher != null) {
            var conv = enricher.toOfficial(a.getAmount(), a.getRewardCurrency());
            amountConverted = conv.amountConverted();
            convertedCurrencyCode = conv.currencyCode();
            exchangeRateUsed = conv.rate();
            exchangeRateDate = conv.rateDate();
        }

        return new BonusAwardDto(
                a.getUuid(),
                DisplayRef.of(a.getRule() != null ? a.getRule().getUuid() : null, null, a.getRuleNameSnapshot()),
                DisplayRefs.ref(promoter),
                a.getMetricCount(),
                a.getBlocksAwarded(),
                a.getWindowStart(),
                a.getWindowEnd(),
                a.getRewardType(),
                a.getFlatAmount(),
                a.getRewardPct(),
                a.getBasisAmount(),
                a.getAmount(),
                a.getRewardCurrency().getCode(),
                amountConverted,
                convertedCurrencyCode,
                exchangeRateUsed,
                exchangeRateDate,
                a.getStatus(),
                a.getEvaluatedAt(),
                a.getCreatedAt());
    }
}
