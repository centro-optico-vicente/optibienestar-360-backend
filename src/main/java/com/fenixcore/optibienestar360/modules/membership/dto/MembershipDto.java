package com.fenixcore.optibienestar360.modules.membership.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan.PlanType;
import com.fenixcore.optibienestar360.modules.membership.entity.TermMode;

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

        /** Human contract number (V203) — what a payer quotes. */
        String contractNumber,

        // Plan reference (flat)
        UUID planUuid,
        String planCode,
        String planName,
        @Display(Display.Kind.ENUM) PlanType planType,

        /** Simple-reporting mirror of the campaign this enrollment counted towards (V124) — read-only, see {@code Membership#campaign}. */
        @Display DisplayRef campaign,

        // Lifecycle dates
        @Display(Display.Kind.DATE) LocalDate enrolledAt,
        @Display(Display.Kind.DATE) LocalDate expiresAt,
        @Display(Display.Kind.DATE) LocalDate nextDueDate,
        @Display(Display.Kind.DATE) LocalDate lastPaidThrough,

        // Term (V220, hub ADR 0034): snapshot of the plan's rule; expiresAt is the end of the current term
        @Display(Display.Kind.BOOLEAN) boolean reinscriptionRequired,
        @Display(Display.Kind.ENUM) TermMode termMode,
        Integer termFixedMonth,
        Integer termFixedDay,
        Integer termPeriodMonths,
        @Display(Display.Kind.DATE) LocalDate termStartedAt,
        /** Date since a re-inscription is pending (the membership stays SUSPENDED until it is paid); null = none. */
        @Display(Display.Kind.DATE) LocalDate inscriptionDueSince,
        /** True while a re-inscription is pending. */
        @Display(Display.Kind.BOOLEAN) boolean inscriptionDue,

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

        /**
         * "Modalidad de pago" of the B2C dossier (hub ADR 0031 §14): the payment
         * method of the first approved inscription payment line — read-only,
         * derived, {@code null} until the inscription is approved.
         */
        @Display DisplayRef inscriptionPaymentMethod,

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
        return new MembershipDto(uuid, memberUuid, contractNumber, planUuid, planCode, planName, planType, campaign,
                enrolledAt, expiresAt, nextDueDate, lastPaidThrough,
                reinscriptionRequired, termMode, termFixedMonth, termFixedDay, termPeriodMonths, termStartedAt,
                inscriptionDueSince, inscriptionDue,
                inscriptionFee, monthlyFee, currency_Code, amountConverted, convertedCurrencyCode,
                exchangeRateUsed, exchangeRateDate, gracePeriodDays, inscriptionPaymentMethod,
                status, lastStatusChangeAt, lastStatusChangeReason,
                active, createdAt, updatedAt);
    }

    /** Rebuilds this record with the derived {@link #inscriptionPaymentMethod}. */
    public MembershipDto withInscriptionPaymentMethod(DisplayRef method) {
        return new MembershipDto(uuid, memberUuid, contractNumber, planUuid, planCode, planName, planType, campaign,
                enrolledAt, expiresAt, nextDueDate, lastPaidThrough,
                reinscriptionRequired, termMode, termFixedMonth, termFixedDay, termPeriodMonths, termStartedAt,
                inscriptionDueSince, inscriptionDue,
                inscriptionFee, monthlyFee, currency_Code, amountConverted, convertedCurrency_Code,
                exchangeRateUsed, exchangeRateDate, gracePeriodDays, method,
                status, lastStatusChangeAt, lastStatusChangeReason,
                active, createdAt, updatedAt);
    }
}
