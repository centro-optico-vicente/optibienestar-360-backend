package com.fenixcore.optibienestar360.modules.promoter.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Payload for {@code POST /v1/admin/commissions/retroactive-topups}. Closes
 * a settlement period (hub plan §3, PR4): for every beneficiary with at
 * least one {@code PAID} commission/hierarchy-override in the range, inserts
 * a {@code commission_retroactive_topups} row for the gap between what the
 * period's final highest-qualifying band would have paid and what was
 * already disbursed across the partial cuts. Run this AFTER {@code
 * POST /v1/admin/commissions/re-rate} and its hierarchy-override sibling —
 * those bump the still-{@code PENDING} rows in place; this covers what's
 * already {@code PAID} and therefore off-limits to them.
 *
 * <p>{@code dryRun = true} computes the deltas without writing.</p>
 */
public record CommissionRetroactiveTopUpRequest(
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd,
        Boolean dryRun
) {}
