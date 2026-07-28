package com.fenixcore.optibienestar360.core.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PeriodStrategiesTest {

    @Test
    void monthly_boundsTheCalendarMonth() {
        PeriodStrategies.Window w = PeriodStrategies.window("MONTHLY", LocalDate.of(2026, 6, 15));
        assertThat(w.start()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(w.end()).isEqualTo(LocalDate.of(2026, 6, 30));
    }

    @Test
    void weekly_isMondayToSunday() {
        // 2026-06-15 is a Monday.
        PeriodStrategies.Window w = PeriodStrategies.window("WEEKLY", LocalDate.of(2026, 6, 17));
        assertThat(w.start()).isEqualTo(LocalDate.of(2026, 6, 15));
        assertThat(w.end()).isEqualTo(LocalDate.of(2026, 6, 21));
    }

    @Test
    void biweekly_splitsAtThe15th() {
        assertThat(PeriodStrategies.window("BIWEEKLY", LocalDate.of(2026, 6, 10)).end())
                .isEqualTo(LocalDate.of(2026, 6, 15));
        assertThat(PeriodStrategies.window("BIWEEKLY", LocalDate.of(2026, 6, 20)).start())
                .isEqualTo(LocalDate.of(2026, 6, 16));
    }

    @Test
    void quarterly_boundsTheCalendarQuarter() {
        PeriodStrategies.Window w = PeriodStrategies.window("QUARTERLY", LocalDate.of(2026, 5, 3));
        assertThat(w.start()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(w.end()).isEqualTo(LocalDate.of(2026, 6, 30));
    }

    @Test
    void semiannual_splitsAtJune() {
        assertThat(PeriodStrategies.window("SEMIANNUAL", LocalDate.of(2026, 3, 1)).end())
                .isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(PeriodStrategies.window("SEMIANNUAL", LocalDate.of(2026, 9, 1)).start())
                .isEqualTo(LocalDate.of(2026, 7, 1));
    }

    @Test
    void annual_boundsTheYear() {
        PeriodStrategies.Window w = PeriodStrategies.window("ANNUAL", LocalDate.of(2026, 8, 20));
        assertThat(w.start()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(w.end()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    void unknownStrategy_throws() {
        assertThatThrownBy(() -> PeriodStrategies.window("LIFETIME", LocalDate.of(2026, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
