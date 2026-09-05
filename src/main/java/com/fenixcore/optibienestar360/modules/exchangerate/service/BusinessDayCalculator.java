package com.fenixcore.optibienestar360.modules.exchangerate.service;

import com.fenixcore.optibienestar360.modules.catalog.entity.Country;
import com.fenixcore.optibienestar360.modules.exchangerate.entity.Holiday;
import com.fenixcore.optibienestar360.modules.exchangerate.repository.HolidayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Pure(ish) calendar helper computing a country's next business day: a rate
 * published on {@code operationDate} becomes vigente at a given local time
 * on the next day that is neither Saturday, nor Sunday, nor matched by any
 * applicable {@link Holiday} rule for {@code country} (ADR 0015 §2/§3 — BCV
 * policy specifically calls this with 8:00 AM {@code America/Caracas} and
 * Venezuela, but the calculator itself is country-agnostic).
 *
 * <p>The only side effect is the holiday lookup — everything else is
 * deterministic date arithmetic, kept in one place so no reader ever has to
 * re-derive the "Friday publish -&gt; Monday 8 AM" rule (ADR 0015 §2,
 * "Toda la complejidad del calendario ... vive una sola vez, en el job de
 * ingesta").</p>
 */
@Component
@RequiredArgsConstructor
public class BusinessDayCalculator {

    private final HolidayRepository holidayRepository;

    /**
     * Returns the {@link Instant} of {@code at} on {@code zone} on the next
     * business day strictly after {@code operationDate}, for {@code country}.
     */
    public Instant nextBusinessDayAt(LocalDate operationDate, Country country, LocalTime at, ZoneId zone) {
        List<Holiday> applicableRules = holidayRepository.findApplicableNationalRules(country);

        LocalDate candidate = operationDate.plusDays(1);
        while (!isBusinessDay(candidate, applicableRules)) {
            candidate = candidate.plusDays(1);
        }
        return ZonedDateTime.of(candidate, at, zone).toInstant();
    }

    private boolean isBusinessDay(LocalDate date, List<Holiday> applicableRules) {
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return false;
        }
        return applicableRules.stream().noneMatch(rule -> matches(rule, date));
    }

    /** Evaluates one {@link Holiday} rule against a candidate date. */
    private boolean matches(Holiday holiday, LocalDate date) {
        if (date.isBefore(holiday.getHolidayDate())) {
            return false; // rule not active before its own anchor date
        }
        return switch (holiday.getRecurrenceType()) {
            case NONE -> date.equals(holiday.getHolidayDate());
            case ANNUAL -> {
                if (date.getMonthValue() != holiday.getHolidayDate().getMonthValue()
                        || date.getDayOfMonth() != holiday.getHolidayDate().getDayOfMonth()) {
                    yield false;
                }
                long occurrence = date.getYear() - holiday.getHolidayDate().getYear();
                yield withinRepetitions(occurrence, holiday.getRepetitionsCount());
            }
            case WEEKLY -> {
                if (date.getDayOfWeek() != holiday.getHolidayDate().getDayOfWeek()) {
                    yield false;
                }
                long occurrence = ChronoUnit.WEEKS.between(holiday.getHolidayDate(), date);
                yield withinRepetitions(occurrence, holiday.getRepetitionsCount());
            }
            case MONTHLY -> {
                // Months shorter than the anchor day-of-month never match — acceptable, documented operational debt.
                if (date.getDayOfMonth() != holiday.getHolidayDate().getDayOfMonth()) {
                    yield false;
                }
                long occurrence = ChronoUnit.MONTHS.between(
                        holiday.getHolidayDate().withDayOfMonth(1), date.withDayOfMonth(1));
                yield withinRepetitions(occurrence, holiday.getRepetitionsCount());
            }
        };
    }

    /**
     * {@code repetitionsCount == 0} recurs forever going forward from the
     * anchor date; {@code repetitionsCount > 0} recurs exactly that many
     * times, starting from occurrence index 0 at the anchor date itself.
     */
    private boolean withinRepetitions(long occurrenceIndex, int repetitionsCount) {
        if (occurrenceIndex < 0) {
            return false;
        }
        return repetitionsCount == 0 || occurrenceIndex < repetitionsCount;
    }
}
