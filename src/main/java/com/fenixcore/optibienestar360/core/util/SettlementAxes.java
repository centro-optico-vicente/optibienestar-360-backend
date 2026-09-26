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
 *
 * <p><b>{@code END_DATE} (Fase 1, hub plan competitive-commission-rules,
 * D8/D14/D15)</b> — the coarsest possible value, "pay at the rule's own
 * {@code ends_at}", is only ever legal on {@code CompetitiveCommissionRule}
 * (the 4 legacy rule tables' DB CHECKs never allow it, so it's backward
 * compatible by construction). Two special cases beyond plain rank
 * comparison:
 * <ul>
 *   <li>{@code partial == END_DATE} is always legal regardless of {@code
 *       accrual}'s own granularity (a period-closing accrual can still defer
 *       every payout to the rule's end) and always disables retroactive —
 *       nothing to catch up on separately when everything pays out once, at
 *       the very end.</li>
 *   <li>{@code retroactive == END_DATE} is a single lump retroactive cut at
 *       {@code ends_at}, allowed whenever the axis is otherwise enabled.</li>
 * </ul>
 */
public final class SettlementAxes {

    private static final List<String> RANK_ORDER = List.of(
            "DAILY", "WEEKLY", "BIWEEKLY", "MONTHLY", "QUARTERLY", "SEMIANNUAL", "ANNUAL", "END_DATE");

    private SettlementAxes() {
    }

    /**
     * 0 (finest, {@code DAILY}) .. 7 (coarsest, {@code END_DATE}).
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

    /** {@code true} for {@code DAILY}..{@code ANNUAL}/{@code END_DATE} — the only values valid on the partial/final/retroactive axes. */
    public static boolean isPeriodic(String strategy) {
        return RANK_ORDER.contains(strategy);
    }

    /**
     * {@code true} when {@code value} is a legal choice for an axis that must
     * be no coarser than {@code accrual} — either {@code value}'s rank is ≤
     * {@code accrual}'s (when accrual is periodic), {@code accrual} is
     * non-periodic (legacy {@code CAMPAIGN}/{@code LIFETIME}, always "coarser"
     * than anything periodic), or {@code value} is {@code END_DATE} (always
     * allowed, regardless of {@code accrual} — D14/D15). Used directly by
     * callers that need this one relationship without the full retroactive
     * resolution — e.g. {@code CompetitiveCommissionRulesService} validating
     * FIRST_TO_REACH's partial/final axes against D14's own error codes.
     */
    public static boolean isNoCoarserThanAccrual(String accrual, String value) {
        if ("END_DATE".equals(value)) {
            return true;
        }
        int valueRank = granularityRank(value);
        return !isPeriodic(accrual) || valueRank <= granularityRank(accrual);
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
        // partial == END_DATE: a single lump payment at the rule's own ends_at,
        // legal regardless of accrual's granularity — nothing left to catch up
        // on separately, so retroactive is always disabled (D15).
        if ("END_DATE".equals(partial)) {
            return new Resolution("END_DATE", null, false);
        }

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

        // retroactive == END_DATE: a single lump retroactive cut at the rule's
        // own ends_at instead of a periodic one — legal whenever the axis is
        // otherwise enabled, regardless of accrual's granularity (D15).
        if ("END_DATE".equals(retroactive)) {
            return new Resolution("END_DATE", null, true);
        }

        int retroRank = granularityRank(retroactive);
        // The [partial..accrual] bound only makes sense when accrual is itself
        // periodic (there's a real window to bound retroactive within). With a
        // non-periodic accrual (CAMPAIGN/LIFETIME) there's nothing to bound
        // against — retroactive stays any independent periodic value, exactly
        // today's actual (pre-D15) behavior.
        if (accrualIsPeriodic) {
            int accrualRank = granularityRank(accrual);
            if (retroRank < partialRank || retroRank > accrualRank) {
                throw new IllegalArgumentException("settlement_axes.retroactive_out_of_range");
            }
        }
        return new Resolution(retroactive, retroactiveAnchor, true);
    }
}
