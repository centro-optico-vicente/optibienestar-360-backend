package com.fenixcore.optibienestar360.modules.membership.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan.PlanType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Output DTO for {@code GET /v1/admin/plans} (list) and
 * {@code GET /v1/admin/plans/{uuid}} (detail). Flat record — Plan has no
 * nested relationships at this layer (memberships hang off the inverse FK
 * and are queried separately on the affiliate detail page). Scalars carry a
 * localized {@code _Display} sibling (ADR 0014), matching {@link PublicPlanDto}.
 */
public record PlanDto(
        UUID uuid,
        String code,
        String name,
        String description,
        @Display(Display.Kind.ENUM) PlanType type,

        // Pricing (ADR 0015 §6 Caso B — a plan's sticker price has no payout
        // snapshot; amountConverted/etc are a live conversion of monthlyFee to
        // the organization's official currency, computed by PlansService via
        // ConversionEnricher — null when unavailable)
        @Display(value = Display.Kind.MONEY, moneyCurrencyField = "currency_Code") BigDecimal inscriptionFee,
        @Display(value = Display.Kind.MONEY, moneyCurrencyField = "currency_Code") BigDecimal monthlyFee,
        String currency_Code,
        @Display(value = Display.Kind.MONEY, moneyCurrencyField = "convertedCurrency_Code") BigDecimal amountConverted,
        String convertedCurrency_Code,
        BigDecimal exchangeRateUsed,
        @Display(Display.Kind.DATE) LocalDate exchangeRateDate,

        // Beneficiaries
        int includedBeneficiaries,
        Integer maxBeneficiaries,
        @Display(Display.Kind.MONEY) BigDecimal extraBeneficiaryInscriptionFee,

        int gracePeriodDays,

        // Publishing
        @Display(Display.Kind.BOOLEAN) boolean published,
        @Display(Display.Kind.DATETIME) Instant publishedAt,

        // Audit
        @Display(Display.Kind.BOOLEAN) boolean active,
        @Display(value = Display.Kind.ENUM, enumScope = "plan.status") String status,
        @Display(Display.Kind.DATETIME) Instant createdAt,
        @Display(Display.Kind.DATETIME) Instant updatedAt
) {
    /** Rebuilds this record with the live conversion of {@link #monthlyFee} populated — see {@code ConversionEnricher}. */
    public PlanDto withConversion(BigDecimal amountConverted, String convertedCurrencyCode,
            BigDecimal exchangeRateUsed, LocalDate exchangeRateDate) {
        return new PlanDto(uuid, code, name, description, type,
                inscriptionFee, monthlyFee, currency_Code, amountConverted, convertedCurrencyCode,
                exchangeRateUsed, exchangeRateDate,
                includedBeneficiaries, maxBeneficiaries, extraBeneficiaryInscriptionFee,
                gracePeriodDays, published, publishedAt,
                active, status, createdAt, updatedAt);
    }
}
