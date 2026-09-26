package com.fenixcore.optibienestar360.core.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettlementAxesTest {

    @Test
    void granularityRank_ordersFinestToCoarsest() {
        assertThat(SettlementAxes.granularityRank("DAILY")).isEqualTo(0);
        assertThat(SettlementAxes.granularityRank("WEEKLY")).isLessThan(SettlementAxes.granularityRank("BIWEEKLY"));
        assertThat(SettlementAxes.granularityRank("BIWEEKLY")).isLessThan(SettlementAxes.granularityRank("MONTHLY"));
        assertThat(SettlementAxes.granularityRank("MONTHLY")).isLessThan(SettlementAxes.granularityRank("QUARTERLY"));
        assertThat(SettlementAxes.granularityRank("QUARTERLY")).isLessThan(SettlementAxes.granularityRank("SEMIANNUAL"));
        assertThat(SettlementAxes.granularityRank("SEMIANNUAL")).isLessThan(SettlementAxes.granularityRank("ANNUAL"));
    }

    @Test
    void granularityRank_rejectsNonPeriodicValues() {
        assertThatThrownBy(() -> SettlementAxes.granularityRank("CAMPAIGN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("settlement_axes.value_must_be_periodic");
        assertThatThrownBy(() -> SettlementAxes.granularityRank("LIFETIME"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resolve_partialSameAsAccrual_disablesRetroactive_andCollapsesToPartial() {
        // The user's exact original scenario, negative case: no gap between
        // accrual and partial (both MONTHLY) — nothing to catch up on.
        SettlementAxes.Resolution r = SettlementAxes.resolve("MONTHLY", "MONTHLY", "WEEKLY", (short) 1);

        assertThat(r.retroactiveEnabled()).isFalse();
        assertThat(r.retroactiveStrategy()).isEqualTo("MONTHLY");
        assertThat(r.retroactiveAnchor()).isNull();
    }

    @Test
    void resolve_partialFinerThanAccrual_enablesRetroactive_whenInRange() {
        // The user's exact scenario: monthly evaluation, weekly payout,
        // biweekly retroactive catch-up cut — must stay enabled and untouched.
        SettlementAxes.Resolution r = SettlementAxes.resolve("MONTHLY", "WEEKLY", "BIWEEKLY", (short) 1);

        assertThat(r.retroactiveEnabled()).isTrue();
        assertThat(r.retroactiveStrategy()).isEqualTo("BIWEEKLY");
        assertThat(r.retroactiveAnchor()).isEqualTo((short) 1);
    }

    @Test
    void resolve_partialCoarserThanAccrual_rejected() {
        assertThatThrownBy(() -> SettlementAxes.resolve("WEEKLY", "MONTHLY", "MONTHLY", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("settlement_axes.partial_coarser_than_accrual");
    }

    @Test
    void resolve_retroactiveFinerThanPartial_rejected() {
        // Enabled (partial WEEKLY < accrual MONTHLY), but a DAILY retroactive
        // cut would be finer than the partial cadence itself — out of range.
        assertThatThrownBy(() -> SettlementAxes.resolve("MONTHLY", "WEEKLY", "DAILY", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("settlement_axes.retroactive_out_of_range");
    }

    @Test
    void resolve_retroactiveCoarserThanAccrual_rejected() {
        assertThatThrownBy(() -> SettlementAxes.resolve("MONTHLY", "WEEKLY", "ANNUAL", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("settlement_axes.retroactive_out_of_range");
    }

    @Test
    void resolve_retroactiveEqualsAccrualOrPartial_isAllowed() {
        // The boundaries of the [partial..accrual] range are inclusive.
        assertThat(SettlementAxes.resolve("MONTHLY", "WEEKLY", "MONTHLY", null).retroactiveEnabled()).isTrue();
        assertThat(SettlementAxes.resolve("MONTHLY", "WEEKLY", "WEEKLY", null).retroactiveEnabled()).isTrue();
    }

    @Test
    void resolve_nonPeriodicAccrual_leavesRetroactiveIndependentlyConfigurable() {
        // commission_bonus_rules' legacy CAMPAIGN/LIFETIME accrual: today's
        // actual behavior (retroactive is any independent periodic value,
        // regardless of partial) must not change.
        SettlementAxes.Resolution campaign = SettlementAxes.resolve("CAMPAIGN", "MONTHLY", "DAILY", (short) 3);
        assertThat(campaign.retroactiveEnabled()).isTrue();
        assertThat(campaign.retroactiveStrategy()).isEqualTo("DAILY");
        assertThat(campaign.retroactiveAnchor()).isEqualTo((short) 3);

        SettlementAxes.Resolution lifetime = SettlementAxes.resolve("LIFETIME", "ANNUAL", "ANNUAL", null);
        assertThat(lifetime.retroactiveEnabled()).isTrue();
        assertThat(lifetime.retroactiveStrategy()).isEqualTo("ANNUAL");
    }

    @Test
    void resolve_regressionDefaults_allMonthly_isANoOp() {
        // Every pre-existing rule (V146-V149 backfill: all 4 axes MONTHLY)
        // must resolve to exactly the same, disabled state it has today.
        SettlementAxes.Resolution r = SettlementAxes.resolve("MONTHLY", "MONTHLY", "MONTHLY", null);
        assertThat(r.retroactiveEnabled()).isFalse();
        assertThat(r.retroactiveStrategy()).isEqualTo("MONTHLY");
        assertThat(r.retroactiveAnchor()).isNull();
    }
}
