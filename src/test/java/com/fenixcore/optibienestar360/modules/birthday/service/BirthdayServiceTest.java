package com.fenixcore.optibienestar360.modules.birthday.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Date rules of {@link BirthdayService}: next birthday, age turned and the month-day window. */
class BirthdayServiceTest {

    @Test
    void nextBirthday_isTodayWhenItMatches() {
        LocalDate today = LocalDate.of(2026, 11, 18);
        assertThat(BirthdayService.nextBirthday(LocalDate.of(1990, 11, 18), today)).isEqualTo(today);
    }

    @Test
    void nextBirthday_rollsToNextYearWhenAlreadyPassed() {
        LocalDate today = LocalDate.of(2026, 11, 18);
        assertThat(BirthdayService.nextBirthday(LocalDate.of(1990, 1, 5), today))
                .isEqualTo(LocalDate.of(2027, 1, 5));
    }

    @Test
    void nextBirthday_leapDayIsCelebratedOnFeb28InNonLeapYears() {
        LocalDate born = LocalDate.of(2000, 2, 29);
        assertThat(BirthdayService.nextBirthday(born, LocalDate.of(2027, 2, 1))).isEqualTo(LocalDate.of(2027, 2, 28));
        assertThat(BirthdayService.nextBirthday(born, LocalDate.of(2028, 2, 1))).isEqualTo(LocalDate.of(2028, 2, 29));
    }

    @Test
    void ageOn_countsYearsAtTheBirthday() {
        assertThat(BirthdayService.ageOn(LocalDate.of(1990, 11, 18), LocalDate.of(2026, 11, 18))).isEqualTo(36);
    }

    @Test
    void monthDaysInWindow_coversTodayPlusDaysAndCrossesYearEnd() {
        assertThat(BirthdayService.monthDaysInWindow(LocalDate.of(2026, 12, 29), 3))
                .containsExactly("12-29", "12-30", "12-31", "01-01");
    }

    @Test
    void monthDaysInWindow_addsLeapDayWhenFeb28OfNonLeapYearIsInside() {
        assertThat(BirthdayService.monthDaysInWindow(LocalDate.of(2027, 2, 27), 2))
                .containsExactly("02-27", "02-28", "02-29", "03-01");
        assertThat(BirthdayService.monthDaysInWindow(LocalDate.of(2028, 2, 27), 1))
                .containsExactly("02-27", "02-28");
    }
}
