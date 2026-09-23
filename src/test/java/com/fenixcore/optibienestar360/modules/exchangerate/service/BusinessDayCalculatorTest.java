package com.fenixcore.optibienestar360.modules.exchangerate.service;

import com.fenixcore.optibienestar360.modules.catalog.entity.Country;
import com.fenixcore.optibienestar360.modules.exchangerate.entity.Holiday;
import com.fenixcore.optibienestar360.modules.exchangerate.repository.HolidayRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BusinessDayCalculator} (ADR 0015 §3) — must match
 * the BCV screenshot example the ADR cites: a Friday publish stays vigente
 * all weekend until Monday 8 AM, and correctly evaluates every
 * {@link Holiday.RecurrenceType} against a candidate date.
 */
@ExtendWith(MockitoExtension.class)
class BusinessDayCalculatorTest {

    private static final ZoneId CARACAS = ZoneId.of("America/Caracas");
    private static final LocalTime EIGHT_AM = LocalTime.of(8, 0);

    @Mock
    private HolidayRepository holidayRepository;

    private BusinessDayCalculator calculator() {
        return new BusinessDayCalculator(holidayRepository);
    }

    private static Country venezuela() {
        Country country = new Country();
        country.setId(1L);
        country.setIsoCode("VE");
        country.setName("Venezuela");
        return country;
    }

    private static Holiday oneOff(LocalDate date) {
        Holiday h = new Holiday();
        h.setName("test-one-off");
        h.setHolidayDate(date);
        h.setRecurring(false);
        h.setRecurrenceType(Holiday.RecurrenceType.NONE);
        h.setRepetitionsCount(0);
        return h;
    }

    private static Holiday recurring(LocalDate anchor, Holiday.RecurrenceType type, int repetitionsCount) {
        Holiday h = new Holiday();
        h.setName("test-recurring-" + type);
        h.setHolidayDate(anchor);
        h.setRecurring(true);
        h.setRecurrenceType(type);
        h.setRepetitionsCount(repetitionsCount);
        return h;
    }

    @Test
    void fridayPublishBecomesVigenteTheFollowingMondayAt8am() {
        when(holidayRepository.findApplicableNationalRules(any())).thenReturn(List.of());
        // 2026-09-04 is a Friday.
        LocalDate friday = LocalDate.of(2026, 9, 4);

        Instant validFrom = calculator().nextBusinessDayAt(friday, venezuela(), EIGHT_AM, CARACAS);

        Instant expected = Instant.parse("2026-09-07T12:00:00Z"); // Monday 8 AM -04:00 = 12:00 UTC
        assertThat(validFrom).isEqualTo(expected);
    }

    @Test
    void mondayPublishBecomesVigenteTuesdayAt8amWhenTuesdayIsOrdinary() {
        when(holidayRepository.findApplicableNationalRules(any())).thenReturn(List.of());
        // 2026-09-07 is a Monday.
        LocalDate monday = LocalDate.of(2026, 9, 7);

        Instant validFrom = calculator().nextBusinessDayAt(monday, venezuela(), EIGHT_AM, CARACAS);

        Instant expected = Instant.parse("2026-09-08T12:00:00Z"); // Tuesday 8 AM -04:00 = 12:00 UTC
        assertThat(validFrom).isEqualTo(expected);
    }

    @Test
    void skipsPastOneOffHolidaysImmediatelyFollowingTheOperationDate() {
        // Operation on Wed 2026-04-01; Thursday 04-02 and Friday 04-03 are
        // one-off (NONE) holidays (Jueves/Viernes Santo), so the next
        // business day is Monday 2026-04-06.
        LocalDate wednesday = LocalDate.of(2026, 4, 1);
        when(holidayRepository.findApplicableNationalRules(any())).thenReturn(List.of(
                oneOff(LocalDate.of(2026, 4, 2)),
                oneOff(LocalDate.of(2026, 4, 3))
        ));

        Instant validFrom = calculator().nextBusinessDayAt(wednesday, venezuela(), EIGHT_AM, CARACAS);

        Instant expected = Instant.parse("2026-04-06T12:00:00Z"); // Monday 8 AM -04:00 = 12:00 UTC
        assertThat(validFrom).isEqualTo(expected);
    }

    @Test
    void weeklyRecurrenceMatchesSameWeekdayThreeWeeksLaterButNotOneDayOff() {
        // Anchor: Wednesday 2026-01-07, recurring WEEKLY, unbounded.
        // 2026-01-28 is also a Wednesday (exactly 3 weeks later) -> matches.
        // Operation date Tuesday 2026-01-27 -> candidate Wed 01-28 matches
        // the weekly rule and is skipped; candidate Thu 01-29 (one day off
        // the matching weekday) is ordinary -> business day.
        LocalDate anchor = LocalDate.of(2026, 1, 7);
        LocalDate operationDate = LocalDate.of(2026, 1, 27);
        when(holidayRepository.findApplicableNationalRules(any())).thenReturn(List.of(
                recurring(anchor, Holiday.RecurrenceType.WEEKLY, 0)
        ));

        Instant validFrom = calculator().nextBusinessDayAt(operationDate, venezuela(), EIGHT_AM, CARACAS);

        Instant expected = Instant.parse("2026-01-29T12:00:00Z"); // Thursday 8 AM -04:00 = 12:00 UTC
        assertThat(validFrom).isEqualTo(expected);
    }

    @Test
    void monthlyRecurrenceMatchesSameDayOfMonthInALaterMonthButNotTheNextDay() {
        // Anchor: the 15th (2026-04-15, a Wednesday), recurring MONTHLY,
        // unbounded. 2026-05-15 (a Friday) matches on day-of-month -> skipped.
        // 2026-05-16/17 are Sat/Sun -> also skipped by the weekend rule.
        // Next ordinary business day is Monday 2026-05-18.
        LocalDate anchor = LocalDate.of(2026, 4, 15);
        LocalDate operationDate = LocalDate.of(2026, 5, 14);
        when(holidayRepository.findApplicableNationalRules(any())).thenReturn(List.of(
                recurring(anchor, Holiday.RecurrenceType.MONTHLY, 0)
        ));

        Instant validFrom = calculator().nextBusinessDayAt(operationDate, venezuela(), EIGHT_AM, CARACAS);

        Instant expected = Instant.parse("2026-05-18T12:00:00Z"); // Monday 8 AM -04:00 = 12:00 UTC
        assertThat(validFrom).isEqualTo(expected);
    }

    @Test
    void annualRecurrenceWithBoundedRepetitionsStopsMatchingAfterTheLastOccurrence() {
        // Anchor: 2024-03-10 (occurrence index 0), repeats exactly 2 times
        // (occurrence indices 0 and 1 -> years 2024 and 2025 match).
        // 2026-03-10 (occurrence index 2, a Tuesday) is past the bound and
        // must NOT match anymore -> ordinary ANNUAL matching is expired.
        LocalDate anchor = LocalDate.of(2024, 3, 10);
        LocalDate operationDate = LocalDate.of(2026, 3, 9); // Monday, the day before the (expired) anniversary
        when(holidayRepository.findApplicableNationalRules(any())).thenReturn(List.of(
                recurring(anchor, Holiday.RecurrenceType.ANNUAL, 2)
        ));

        Instant validFrom = calculator().nextBusinessDayAt(operationDate, venezuela(), EIGHT_AM, CARACAS);

        Instant expected = Instant.parse("2026-03-10T12:00:00Z"); // Tuesday 8 AM -04:00 = 12:00 UTC
        assertThat(validFrom).isEqualTo(expected);
    }

    @Test
    void annualRecurrenceStillMatchesWithinTheBoundedRepetitions() {
        // Same anchor/bound as above, but checked one year earlier: 2025-03-10
        // is occurrence index 1 (still within repetitions_count = 2), so it
        // must match and be skipped -> next business day is Tuesday 03-11.
        LocalDate anchor = LocalDate.of(2024, 3, 10);
        LocalDate operationDate = LocalDate.of(2025, 3, 9); // Sunday
        when(holidayRepository.findApplicableNationalRules(any())).thenReturn(List.of(
                recurring(anchor, Holiday.RecurrenceType.ANNUAL, 2)
        ));

        Instant validFrom = calculator().nextBusinessDayAt(operationDate, venezuela(), EIGHT_AM, CARACAS);

        Instant expected = Instant.parse("2025-03-11T12:00:00Z"); // Tuesday 8 AM -04:00 = 12:00 UTC (03-10 Monday matches the still-bounded annual rule and is skipped)
        assertThat(validFrom).isEqualTo(expected);
    }

    @Test
    void previousBusinessDayBeforeMondaySkipsBackOverTheWeekendToFriday() {
        when(holidayRepository.findApplicableNationalRules(any())).thenReturn(List.of());
        // 2026-09-07 is a Monday (the vigency/valueDate) -> the business day
        // strictly before it is Friday 2026-09-04, skipping Sat/Sun.
        LocalDate monday = LocalDate.of(2026, 9, 7);

        LocalDate operationDate = calculator().previousBusinessDayBefore(monday, venezuela());

        assertThat(operationDate).isEqualTo(LocalDate.of(2026, 9, 4));
    }

    @Test
    void previousBusinessDayBeforeSkipsPastOneOffHolidaysImmediatelyPrecedingTheValueDate() {
        // valueDate Monday 2026-04-06; Thursday 04-02 and Friday 04-03 are
        // one-off (NONE) holidays (Jueves/Viernes Santo), and 04-04/04-05 are
        // the weekend -> the previous business day is Wednesday 2026-04-01.
        LocalDate monday = LocalDate.of(2026, 4, 6);
        when(holidayRepository.findApplicableNationalRules(any())).thenReturn(List.of(
                oneOff(LocalDate.of(2026, 4, 2)),
                oneOff(LocalDate.of(2026, 4, 3))
        ));

        LocalDate operationDate = calculator().previousBusinessDayBefore(monday, venezuela());

        assertThat(operationDate).isEqualTo(LocalDate.of(2026, 4, 1));
    }
}
