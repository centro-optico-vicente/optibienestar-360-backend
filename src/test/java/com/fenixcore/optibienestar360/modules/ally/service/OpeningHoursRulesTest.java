package com.fenixcore.optibienestar360.modules.ally.service;

import com.fenixcore.optibienestar360.modules.ally.service.OpeningHoursRules.Slot;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link OpeningHoursRules}: week validation, the half-open
 * "open at" check at its boundaries and the grouped human label.
 */
class OpeningHoursRulesTest {

    private static final Locale ES = Locale.forLanguageTag("es-VE");

    private static Slot slot(int day, String opens, String closes) {
        return new Slot(day, LocalTime.parse(opens), LocalTime.parse(closes));
    }

    // 2026-11-02 is a Monday.
    private static LocalDateTime monday(String time) {
        return LocalDateTime.of(2026, 11, 2, 0, 0).with(LocalTime.parse(time));
    }

    @Test
    void validate_acceptsTouchingRangesAndSeveralDays() {
        assertThatCode(() -> OpeningHoursRules.validate(List.of(
                slot(1, "08:00", "12:00"), slot(1, "12:00", "14:00"), slot(2, "08:00", "12:00"))))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_rejectsOverlappingRangesOfTheSameDay() {
        assertThatThrownBy(() -> OpeningHoursRules.validate(List.of(
                slot(3, "14:00", "18:00"), slot(3, "08:00", "14:30"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ally.opening_hours.overlap");
    }

    @Test
    void validate_rejectsTwoRangesStartingAtTheSameTime() {
        assertThatThrownBy(() -> OpeningHoursRules.validate(List.of(
                slot(5, "08:00", "09:00"), slot(5, "08:00", "12:00"))))
                .hasMessage("ally.opening_hours.overlap");
    }

    @Test
    void validate_rejectsARangeThatDoesNotCloseAfterItOpens() {
        assertThatThrownBy(() -> OpeningHoursRules.validate(List.of(slot(1, "12:00", "12:00"))))
                .hasMessage("ally.opening_hours.invalid_range");
        assertThatThrownBy(() -> OpeningHoursRules.validate(List.of(slot(1, "18:00", "08:00"))))
                .hasMessage("ally.opening_hours.invalid_range");
    }

    @Test
    void validate_rejectsADayOutsideIso() {
        assertThatThrownBy(() -> OpeningHoursRules.validate(List.of(slot(8, "08:00", "12:00"))))
                .hasMessage("ally.opening_hours.invalid_day");
    }

    @Test
    void isOpenAt_isHalfOpen_opensInclusiveClosesExclusive() {
        List<Slot> week = List.of(slot(1, "08:00", "12:00"), slot(1, "14:00", "18:00"));

        assertThat(OpeningHoursRules.isOpenAt(week, monday("07:59"))).isFalse();
        assertThat(OpeningHoursRules.isOpenAt(week, monday("08:00"))).isTrue();
        assertThat(OpeningHoursRules.isOpenAt(week, monday("11:59"))).isTrue();
        assertThat(OpeningHoursRules.isOpenAt(week, monday("12:00"))).isFalse();
        assertThat(OpeningHoursRules.isOpenAt(week, monday("14:00"))).isTrue();
        assertThat(OpeningHoursRules.isOpenAt(week, monday("18:00"))).isFalse();
    }

    @Test
    void isOpenAt_onlyMatchesTheSameDay() {
        assertThat(OpeningHoursRules.isOpenAt(List.of(slot(2, "08:00", "12:00")), monday("09:00"))).isFalse();
    }

    @Test
    void label_groupsConsecutiveDaysWithTheSameRanges() {
        List<Slot> week = List.of(
                slot(1, "08:00", "12:00"), slot(1, "14:00", "18:00"),
                slot(2, "14:00", "18:00"), slot(2, "08:00", "12:00"),
                slot(3, "08:00", "12:00"), slot(3, "14:00", "18:00"),
                slot(4, "08:00", "12:00"), slot(4, "14:00", "18:00"),
                slot(5, "08:00", "12:00"), slot(5, "14:00", "18:00"),
                slot(6, "08:00", "12:00"));

        assertThat(OpeningHoursRules.label(week, ES))
                .isEqualTo("Lun–Vie 08:00–12:00, 14:00–18:00; Sáb 08:00–12:00");
    }

    @Test
    void label_doesNotGroupDaysThatAreNotConsecutive() {
        List<Slot> week = List.of(slot(1, "09:00", "13:00"), slot(3, "09:00", "13:00"), slot(4, "09:00", "13:00"));

        assertThat(OpeningHoursRules.label(week, ES)).isEqualTo("Lun 09:00–13:00; Mié–Jue 09:00–13:00");
        assertThat(OpeningHoursRules.label(week, Locale.ENGLISH)).isEqualTo("Mon 09:00–13:00; Wed–Thu 09:00–13:00");
    }

    @Test
    void label_isNullForAnEmptyWeek() {
        assertThat(OpeningHoursRules.label(List.of(), ES)).isNull();
    }
}
