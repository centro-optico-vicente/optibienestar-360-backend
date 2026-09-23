package com.fenixcore.optibienestar360.modules.promoter.dto;

import java.time.LocalDate;

/**
 * Payload for {@code POST /v1/admin/commissions/retroactive-topups}.
 *
 * <p><b>Legacy whole-period mode</b> ({@link #periodStart}/{@link
 * #periodEnd} set, {@link #asOf} {@code null}) — hub plan §3, PR4: for every
 * beneficiary with at least one {@code PAID} commission/hierarchy-override
 * in the range, upserts a {@code commission_retroactive_topups} row (V105,
 * frozen historical shape — one row per whole settlement period) for the
 * gap between what the period's final highest-qualifying band would have
 * paid and what was already disbursed. Run this AFTER {@code
 * POST /v1/admin/commissions/re-rate} and its hierarchy-override sibling.</p>
 *
 * <p><b>Cut mode</b> ({@link #asOf} set) — Fase A, retroactive settlement
 * axis: computes ONE retroactive cut — the {@code
 * retroactiveSettlementPeriodStrategy} cut containing {@link #asOf}, inside
 * the accrual period containing {@link #asOf} — for every ledger type,
 * writing to the successor {@code commission_retroactive_topup_cuts} table
 * (V150), which supports several such cuts per accrual period with
 * automatic netting against earlier PAID cuts of the same period. {@link
 * #periodStart}/{@link #periodEnd} are ignored in this mode.</p>
 *
 * <p>{@code dryRun = true} computes the deltas without writing, in either mode.</p>
 */
public record CommissionRetroactiveTopUpRequest(
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate asOf,
        Boolean dryRun
) {}
