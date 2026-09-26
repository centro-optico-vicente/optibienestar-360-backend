package com.fenixcore.optibienestar360.core.util;

import java.util.List;

/**
 * D15 (hub plan {@code 2026-09-25-competitive-commission-rules.md}, Fase A):
 * the retroactive settlement axis only makes sense — and is only enabled —
 * when the partial settlement axis is strictly finer than the accrual axis.
 * A rule can pay a weekly partial cut against a monthly goal, and only THEN
 * does "catch up the retroactive difference on a biweekly cut" mean
 * anything: if {@code partial == accrual}, settlement already happens once
 * per period (nothing to catch up on), and if {@code partial} were coarser
 * than {@code accrual} the axis ordering itself would be nonsensical.
 *
 * <p>Shared by the 4 pre-existing rule-frequency engines ({@code
 * CommissionTier}, {@code HierarchyOverrideTier}, {@code CommissionBonusRule},
 * {@code CollectionCommissionTier}) so the rule is enforced identically
 * everywhere instead of once per service. Before this class existed, none of
 * the 4 services validated the 3 axes against each other at all — a rule
 * could be saved with, say, a daily retroactive cut against a monthly
 * payout, which has no sensible meaning.</p>
 */
public final class SettlementAxes {

    private static final List<String> RANK_ORDER = List.of(
            "DAILY", "WEEKLY", "BIWEEKLY", "MONTHLY", "QUARTERLY", "SEMIANNUAL", "ANNUAL");

    private SettlementAxes() {
    }

    /**
     * 0 (finest, {@code DAILY}) .. 6 (coarsest, {@code ANNUAL}).
     *
     * @throws IllegalArgumentException on any non-periodic value (e.g. the
     *         bonus-only legacy {@code CAMPAIGN}/{@code LIFETIME})
     */
    public static int granularityRank(String periodicStrategy) {
        int idx = RANK_ORDER.indexOf(periodicStrategy);
        if (idx < 0) {
            throw new IllegalArgumentException("settlement_axes.value_must_be_periodic");
        }
        return idx;
    }

    /** {@code true} for {@code DAILY}..{@code ANNUAL} — the only values valid on the partial/final/retroactive axes today. */
    public static boolean isPeriodic(String strategy) {
        return RANK_ORDER.contains(strategy);
    }

    /** The outcome of resolving the retroactive axis for one rule (D15): what to persist, and whether the axis is live. */
    public record Resolution(String retroactiveStrategy, Short retroactiveAnchor, boolean retroactiveEnabled) {
    }

    /**
     * Validates {@code partial} against {@code accrual} and resolves the
     * retroactive axis per D15.
     *
     * @param accrual           the rule's accrual strategy name — may be a
     *                          non-periodic legacy value (bonus's {@code
     *                          CAMPAIGN}/{@code LIFETIME}); those always
     *                          count as "coarser than any periodic partial",
     *                          so the retroactive axis stays enabled and
     *                          independently configurable whenever partial is
     *                          periodic — exactly today's actual behavior for
     *                          campaign/lifetime bonus rules, unchanged by
     *                          this class.
     * @param partial           partial settlement strategy name — must be periodic.
     * @param retroactive       requested retroactive strategy name — read only
     *                          when the resolved axis ends up enabled.
     * @param retroactiveAnchor requested retroactive anchor — read only when enabled.
     * @return what to persist: when disabled, {@code retroactiveStrategy == partial}
     *         and {@code retroactiveAnchor == null} (a no-op: a single cut, same
     *         cadence as the partial/final settlement, so nothing is actually
     *         "caught up" separately)
     * @throws IllegalArgumentException {@code settlement_axes.value_must_be_periodic},
     *         {@code settlement_axes.partial_coarser_than_accrual} or
     *         {@code settlement_axes.retroactive_out_of_range}
     */
    public static Resolution resolve(String accrual, String partial, String retroactive, Short retroactiveAnchor) {
        int partialRank = granularityRank(partial);

        boolean accrualIsPeriodic = isPeriodic(accrual);
        boolean enabled;
        if (accrualIsPeriodic) {
            int accrualRank = granularityRank(accrual);
            if (partialRank > accrualRank) {
                throw new IllegalArgumentException("settlement_axes.partial_coarser_than_accrual");
            }
            enabled = partialRank < accrualRank;
        } else {
            // Non-periodic accrual (CAMPAIGN/LIFETIME): always coarser than any periodic partial.
            enabled = true;
        }

        if (!enabled) {
            return new Resolution(partial, null, false);
        }

        int retroRank = granularityRank(retroactive);
        if (retroRank < partialRank) {
            throw new IllegalArgumentException("settlement_axes.retroactive_out_of_range");
        }
        if (accrualIsPeriodic && retroRank > granularityRank(accrual)) {
            throw new IllegalArgumentException("settlement_axes.retroactive_out_of_range");
        }
        return new Resolution(retroactive, retroactiveAnchor, true);
    }
}
