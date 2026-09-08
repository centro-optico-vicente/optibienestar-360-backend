package com.fenixcore.optibienestar360.core.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PeriodCutCalculator} — the whiteboard "semanas ISO
 * recortadas al mes calendario" example (hub plan §3), generalized to any
 * (cut strategy, settlement strategy) combination.
 */
class PeriodCutCalculatorTest {

    @Test
    void weeklyCutsClippedToMonthlySettlement_firstAndLastCutArePartial() {
        // June 2026: the 1st is a Monday, the 30th a Tuesday — first cut is a
        // full ISO week, last cut is clipped to just Mon-Tue (6/29-6/30).
        List<PeriodCutCalculator.Cut> cuts =
                PeriodCutCalculator.cuts("WEEKLY", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        assertThat(cuts).containsExactly(
                new PeriodCutCalculator.Cut(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 7)),
                new PeriodCutCalculator.Cut(LocalDate.of(2026, 6, 8), LocalDate.of(2026, 6, 14)),
                new PeriodCutCalculator.Cut(LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 21)),
                new PeriodCutCalculator.Cut(LocalDate.of(2026, 6, 22), LocalDate.of(2026, 6, 28)),
                new PeriodCutCalculator.Cut(LocalDate.of(2026, 6, 29), LocalDate.of(2026, 6, 30)));
    }

    @Test
    void biweeklyCutsClippedToMonthlySettlement_splitAtThe15th() {
        List<PeriodCutCalculator.Cut> cuts =
                PeriodCutCalculator.cuts("BIWEEKLY", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        assertThat(cuts).containsExactly(
                new PeriodCutCalculator.Cut(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 15)),
                new PeriodCutCalculator.Cut(LocalDate.of(2026, 6, 16), LocalDate.of(2026, 6, 30)));
    }

    @Test
    void monthlyCutEqualsTheWholeMonthlySettlement_singleCut() {
        // A promoter with a MONTHLY payout rule has no "partial" corte at all —
        // the mechanism doesn't special-case this, it just yields one cut.
        List<PeriodCutCalculator.Cut> cuts =
                PeriodCutCalculator.cuts("MONTHLY", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        assertThat(cuts).containsExactly(
                new PeriodCutCalculator.Cut(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)));
    }

    @Test
    void quarterlyCutsClippedToAnnualSettlement_fourEvenCuts() {
        List<PeriodCutCalculator.Cut> cuts =
                PeriodCutCalculator.cuts("QUARTERLY", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        assertThat(cuts).hasSize(4);
        assertThat(cuts.get(0)).isEqualTo(new PeriodCutCalculator.Cut(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31)));
        assertThat(cuts.get(3)).isEqualTo(new PeriodCutCalculator.Cut(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31)));
    }

    @Test
    void dailyCutsProduceOneCutPerCalendarDay() {
        List<PeriodCutCalculator.Cut> cuts =
                PeriodCutCalculator.cuts("DAILY", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 3));

        assertThat(cuts).containsExactly(
                new PeriodCutCalculator.Cut(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1)),
                new PeriodCutCalculator.Cut(LocalDate.of(2026, 6, 2), LocalDate.of(2026, 6, 2)),
                new PeriodCutCalculator.Cut(LocalDate.of(2026, 6, 3), LocalDate.of(2026, 6, 3)));
    }

    @Test
    void cutsCoverTheWholeSettlementWindowContiguouslyWithNoGapsOrOverlaps() {
        List<PeriodCutCalculator.Cut> cuts =
                PeriodCutCalculator.cuts("WEEKLY", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        assertThat(cuts.get(0).start()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(cuts.get(cuts.size() - 1).end()).isEqualTo(LocalDate.of(2026, 6, 30));
        for (int i = 1; i < cuts.size(); i++) {
            assertThat(cuts.get(i).start()).isEqualTo(cuts.get(i - 1).end().plusDays(1));
        }
    }

    @Test
    void rejectsInvertedRange() {
        assertThatThrownBy(() -> PeriodCutCalculator.cuts("WEEKLY", LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid_range");
    }

    @Test
    void cutContaining_returnsTheClippedCutForAGivenDate() {
        PeriodCutCalculator.Cut cut = PeriodCutCalculator.cutContaining(
                "WEEKLY", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), LocalDate.of(2026, 6, 30));

        assertThat(cut).isEqualTo(new PeriodCutCalculator.Cut(LocalDate.of(2026, 6, 29), LocalDate.of(2026, 6, 30)));
    }
}
