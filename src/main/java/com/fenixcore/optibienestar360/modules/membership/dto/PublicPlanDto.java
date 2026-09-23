package com.fenixcore.optibienestar360.modules.membership.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan.PlanType;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Sanitized projection of {@link com.fenixcore.optibienestar360.modules.membership.entity.Plan}
 * for the public pricing surface ({@code GET /v1/public/plans}). Mirrors the
 * {@code PublicAlly*} sanitization convention: only the fields a prospective
 * customer needs to compare and pick a plan are exposed.
 *
 * <p>Deliberately omitted (internal lifecycle / audit, never for anonymous
 * eyes):</p>
 * <ul>
 *   <li>{@code published} / {@code publishedAt} — the endpoint only ever
 *       returns published plans, so the flag is redundant and its timestamp is
 *       internal.</li>
 *   <li>{@code active} / {@code status} — soft-delete + audit lifecycle.</li>
 *   <li>{@code createdAt} / {@code updatedAt} — audit trail.</li>
 * </ul>
 *
 * <p>{@code uuid} is exposed so the frontend can deep-link to a plan (e.g. a
 * checkout / signup flow); {@code code} is the stable natural key of the SKU
 * (INDIVIDUAL / FAMILIAR / CORPORATIVO) — public-facing by nature, useful for
 * per-tier styling. Beneficiary fields are selling points straight off the
 * flyer ("incluye 3 beneficiarios, hasta 5, +$5 por adicional"), so they stay
 * in. {@code currency_Code} rides along solely so the {@code MONEY} fields
 * format in the plan's real currency (ADR 0008: plan pricing is USD today)
 * instead of {@link com.fenixcore.optibienestar360.core.display.DisplayFormatter}'s
 * VES fallback.</p>
 */
public record PublicPlanDto(
        UUID uuid,
        String code,
        String name,
        String description,
        @Display(Display.Kind.ENUM) PlanType type,

        // Pricing
        @Display(value = Display.Kind.MONEY, moneyCurrencyField = "currency_Code") BigDecimal inscriptionFee,
        @Display(value = Display.Kind.MONEY, moneyCurrencyField = "currency_Code") BigDecimal monthlyFee,
        String currency_Code,

        // Beneficiaries (public selling points)
        int includedBeneficiaries,
        Integer maxBeneficiaries,
        @Display(value = Display.Kind.MONEY, moneyCurrencyField = "currency_Code") BigDecimal extraBeneficiaryInscriptionFee,

        int gracePeriodDays
) {}
