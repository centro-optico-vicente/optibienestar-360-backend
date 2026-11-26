package com.fenixcore.optibienestar360.modules.organization.dto;

import com.fenixcore.optibienestar360.modules.organization.entity.MembersCounterMode;

/**
 * Body of {@code GET /v1/public/organization/stats} (hub ADR 0042): the
 * aggregate counters of the login screen. Only totals, never personal data.
 *
 * <ul>
 *   <li>{@code visible = false} → the card is not shown; {@code count} (and
 *       {@code mode}) are {@code null}.</li>
 *   <li>{@code visible = true}, {@code count = null} → the real figure is
 *       below the configured minimum; the card shows a generic text.</li>
 *   <li>{@code visible = true}, {@code count} set → the real figure.</li>
 * </ul>
 */
public record PublicOrganizationStatsDto(AlliesCounter allies, MembersCounter members) {

    /** Allies in the public directory (active, published, approved). */
    public record AlliesCounter(boolean visible, Long count) {}

    /** Members with an ACTIVE membership, plus their active beneficiaries when {@code mode} says so. */
    public record MembersCounter(boolean visible, Long count, MembersCounterMode mode) {}
}
