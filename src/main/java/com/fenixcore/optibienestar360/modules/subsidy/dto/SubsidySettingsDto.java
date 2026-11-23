package com.fenixcore.optibienestar360.modules.subsidy.dto;

import com.fenixcore.optibienestar360.core.display.Display;

import java.math.BigDecimal;

/**
 * {@code GET /v1/admin/subsidies/settings} (hub ADR 0041 §6) — what the subsidy
 * screen needs to know before showing its form.
 *
 * @param mode               the organization's subsidy mode (DISABLED | MONTHLY | INSCRIPTION | MONTHLY_AND_INSCRIPTION)
 * @param monthlyEnabled     manual subsidies may set a monthly percentage
 * @param inscriptionEnabled manual subsidies may set an inscription percentage
 * @param discountCapPct     the current user's discount cap (%); {@code null} = uncapped
 * @param canExceedCap       the current user holds {@code SUBSIDY_EXCEED_DISCOUNT_CAP}
 */
public record SubsidySettingsDto(
        @Display(value = Display.Kind.ENUM, enumScope = "organization.subsidy_mode") String mode,
        boolean monthlyEnabled,
        boolean inscriptionEnabled,
        BigDecimal discountCapPct,
        boolean canExceedCap
) {}
