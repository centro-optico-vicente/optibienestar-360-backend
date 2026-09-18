package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.AppliesTo;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.PeriodStrategy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Output DTO for {@code GET /v1/admin/commissions}. Flat record with
 * promoter / payment / member refs extracted as UUIDs + labels so the
 * admin queue renders without N+1. Presentational scalars carry a localized
 * {@code _Display} sibling (hub ADR 0014).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CommissionDto(
        UUID uuid,

        // Subject (flat refs — payment/member resolve to a readable
        // _Display via DisplayRefs, never a bare UUID; see ADR 0014)
        @Display DisplayRef promoter,
        @Display DisplayRef payment,
        @Display DisplayRef member,

        // Money + snapshot. `amountConverted`/etc read the persisted paid-time
        // snapshot once the commission is PAID (frozen, never recomputed);
        // for a still-PENDING/APPROVED row they fall back to a live
        // conversion to the organization's official currency, computed by
        // the service via ConversionEnricher (ADR 0015 §6 Caso B) — null when
        // no rate is available either way.
        @Display(value = Display.Kind.MONEY, moneyCurrencyField = "currency_Code") BigDecimal amount,
        String currency_Code,
        @Display DisplayRef currency,
        @Display(value = Display.Kind.MONEY, moneyCurrencyField = "convertedCurrency_Code") BigDecimal amountConverted,
        String convertedCurrency_Code,
        BigDecimal exchangeRateUsed,
        @Display(Display.Kind.DATE) LocalDate exchangeRateDate,

        // FX snapshot pair — the rate vigente at devengo vs. at payout, so the
        // currency variance the company absorbs between the two moments is
        // visible (null until each respective event happens)
        BigDecimal exchangeRateAtEarned,
        @Display(Display.Kind.DATE) LocalDate earnedRateDate,
        BigDecimal exchangeRateAtPaid,
        @Display(Display.Kind.DATE) LocalDate paidRateDate,
        @Display(value = Display.Kind.MONEY, moneyCurrencyField = "convertedCurrency_Code") BigDecimal fxVarianceAmountConverted,

        @Display(Display.Kind.MONEY) BigDecimal calculationBasis,
        @Display(Display.Kind.NUMBER) BigDecimal commissionPct,
        @Display(Display.Kind.MONEY) BigDecimal flatAmount,
        String tierNameSnapshot,

        // Categorization
        @Display(Display.Kind.ENUM) AppliesTo appliesTo,
        @Display(Display.Kind.ENUM) PeriodStrategy periodStrategy,
        @Display(Display.Kind.DATE) LocalDate periodStart,
        @Display(Display.Kind.DATE) LocalDate periodEnd,
        @Display(Display.Kind.DATETIME) Instant earnedAt,

        // Payout tracking — `payoutPayment` is the real OUT Payment that
        // disbursed this row (V118/CommissionPayoutService, hub plan
        // payments-unification); null for histórico PAID rows from before
        // that FK existed, or when the row is still un-paid. Distinct from
        // `payment` above, which is the IN payment that TRIGGERED this
        // commission, not the one that paid it out.
        @Display DisplayRef payoutPayment,
        String payoutReference,
        @Display(Display.Kind.DATETIME) Instant paidAt,
        @Display(Display.Kind.DATETIME) Instant voidedAt,
        String voidReason,
        String adminNotes,

        // Audit + workflow status
        @Display(Display.Kind.BOOLEAN) boolean active,
        @Display(value = Display.Kind.ENUM, enumScope = "commission.status") String status,
        @Display(Display.Kind.DATETIME) Instant createdAt,
        @Display(Display.Kind.DATETIME) Instant updatedAt
) {
    /**
     * Rebuilds this record with the conversion fields populated — either the
     * persisted paid-time snapshot or a live conversion, plus the FX variance
     * between devengo and payout rates; see {@code CommissionsService.enrich}.
     */
    public CommissionDto withConversion(BigDecimal amountConverted, String convertedCurrencyCode,
            BigDecimal exchangeRateUsed, LocalDate exchangeRateDate, BigDecimal fxVarianceAmountConverted) {
        return new CommissionDto(uuid, promoter, payment, member, amount, currency_Code, currency,
                amountConverted, convertedCurrencyCode, exchangeRateUsed, exchangeRateDate,
                exchangeRateAtEarned, earnedRateDate, exchangeRateAtPaid, paidRateDate, fxVarianceAmountConverted,
                calculationBasis, commissionPct, flatAmount, tierNameSnapshot,
                appliesTo, periodStrategy, periodStart, periodEnd, earnedAt,
                payoutPayment, payoutReference, paidAt, voidedAt, voidReason, adminNotes,
                active, status, createdAt, updatedAt);
    }
}
