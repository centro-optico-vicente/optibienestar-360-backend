package com.fenixcore.optibienestar360.modules.membership.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan.PlanType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Output DTO for {@code GET /v1/admin/members/{memberUuid}/memberships}
 * (list / detail) and the response of POST + cancel/reactivate endpoints.
 *
 * <p>Plan fields are flat-extracted via the mapper (planUuid + planCode +
 * planName + planType) so the frontend can render the subscription card
 * without a follow-up GET to /plans. Presentational scalars carry a
 * localized {@code _Display} sibling (hub ADR 0014) so the frontend renders
 * money / dates / status without re-formatting.</p>
 */
public record MembershipDto(
        UUID uuid,

        UUID memberUuid,

        // Plan reference (flat)
        UUID planUuid,
        String planCode,
        String planName,
        @Display(Display.Kind.ENUM) PlanType planType,

        // Lifecycle dates
        @Display(Display.Kind.DATE) LocalDate enrolledAt,
        @Display(Display.Kind.DATE) LocalDate expiresAt,
        @Display(Display.Kind.DATE) LocalDate nextDueDate,
        @Display(Display.Kind.DATE) LocalDate lastPaidThrough,

        // Pricing snapshot (ADR 0015 §6 Caso B — an active membership has no
        // payout snapshot; amountConverted/etc are a live conversion of
        // monthlyFee to the organization's official currency, computed by
        // MembershipsService via ConversionEnricher — null when unavailable)
        @Display(value = Display.Kind.MONEY, moneyCurrencyField = "currency_Code") BigDecimal inscriptionFee,
        @Display(value = Display.Kind.MONEY, moneyCurrencyField = "currency_Code") BigDecimal monthlyFee,
        String currency_Code,
        @Display(value = Display.Kind.MONEY, moneyCurrencyField = "convertedCurrency_Code") BigDecimal amountConverted,
        String convertedCurrency_Code,
        BigDecimal exchangeRateUsed,
        @Display(Display.Kind.DATE) LocalDate exchangeRateDate,
        int gracePeriodDays,

        // Status
        @Display(value = Display.Kind.ENUM, enumScope = "membership.status") String status,
        @Display(Display.Kind.DATETIME) Instant lastStatusChangeAt,
        String lastStatusChangeReason,

        // Audit
        @Display(Display.Kind.BOOLEAN) boolean active,
        @Display(Display.Kind.DATETIME) Instant createdAt,
        @Display(Display.Kind.DATETIME) Instant updatedAt
) {
    /** Rebuilds this record with the live conversion of {@link #monthlyFee} populated — see {@code ConversionEnricher}. */
    public MembershipDto withConversion(BigDecimal amountConverted, String convertedCurrencyCode,
            BigDecimal exchangeRateUsed, LocalDate exchangeRateDate) {
        return new MembershipDto(uuid, memberUuid, planUuid, planCode, planName, planType,
                enrolledAt, expiresAt, nextDueDate, lastPaidThrough,
                inscriptionFee, monthlyFee, currency_Code, amountConverted, convertedCurrencyCode,
                exchangeRateUsed, exchangeRateDate, gracePeriodDays,
                status, lastStatusChangeAt, lastStatusChangeReason,
                active, createdAt, updatedAt);
    }
}
