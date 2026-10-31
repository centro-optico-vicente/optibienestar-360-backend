package com.fenixcore.optibienestar360.modules.ally.service;

import com.fenixcore.optibienestar360.modules.ally.entity.AllyOpeningHour;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Pure rules of an ally's weekly opening hours (hub ADR 0031 §7): validation
 * of a week, "open at" checks and the human label used by the agreement
 * ({@code ally.openingHours}) and the public detail. Days are ISO-8601
 * (1 = Monday … 7 = Sunday); a range is half-open {@code [opensAt, closesAt)},
 * so 08:00–12:00 and 12:00–14:00 touch without overlapping.
 */
public final class OpeningHoursRules {

    /** One range, independent of how it is stored or received. */
    public record Slot(int dayOfWeek, LocalTime opensAt, LocalTime closesAt) {}

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final String[] SHORT_ES = {"Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"};
    private static final String[] SHORT_EN = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
    private static final Comparator<Slot> ORDER =
            Comparator.comparingInt(Slot::dayOfWeek).thenComparing(Slot::opensAt);

    private OpeningHoursRules() {
    }

    public static Slot of(AllyOpeningHour hour) {
        return new Slot(hour.getDayOfWeek(), hour.getOpensAt(), hour.getClosesAt());
    }

    public static List<Slot> of(Collection<AllyOpeningHour> hours) {
        return hours == null ? List.of() : hours.stream().map(OpeningHoursRules::of).toList();
    }

    /**
     * Rejects a week with an invalid day, a range that does not close after it
     * opens, or two overlapping ranges on the same day.
     *
     * @throws IllegalArgumentException {@code ally.opening_hours.invalid_day},
     *         {@code ally.opening_hours.invalid_range} or {@code ally.opening_hours.overlap}
     */
    public static void validate(List<Slot> week) {
        if (week == null || week.isEmpty()) {
            return;
        }
        for (Slot slot : week) {
            if (slot.dayOfWeek() < 1 || slot.dayOfWeek() > 7) {
                throw new IllegalArgumentException("ally.opening_hours.invalid_day");
            }
            if (slot.opensAt() == null || slot.closesAt() == null || !slot.closesAt().isAfter(slot.opensAt())) {
                throw new IllegalArgumentException("ally.opening_hours.invalid_range");
            }
        }
        List<Slot> sorted = week.stream().sorted(ORDER).toList();
        for (int i = 1; i < sorted.size(); i++) {
            Slot previous = sorted.get(i - 1);
            Slot current = sorted.get(i);
            if (previous.dayOfWeek() == current.dayOfWeek() && current.opensAt().isBefore(previous.closesAt())) {
                throw new IllegalArgumentException("ally.opening_hours.overlap");
            }
        }
    }

    /** Whether any range covers {@code localTime} (wall clock in the business zone). */
    public static boolean isOpenAt(List<Slot> week, LocalDateTime localTime) {
        if (week == null || localTime == null) {
            return false;
        }
        int day = localTime.getDayOfWeek().getValue();
        LocalTime time = localTime.toLocalTime();
        return week.stream().anyMatch(slot -> slot.dayOfWeek() == day
                && !time.isBefore(slot.opensAt()) && time.isBefore(slot.closesAt()));
    }

    /**
     * Human text of the week, e.g. {@code "Lun–Vie 08:00–12:00, 14:00–18:00; Sáb 08:00–12:00"}.
     * Consecutive days with the same ranges are grouped. {@code null} for an
     * empty week, so templates show their pending placeholder.
     */
    public static String label(List<Slot> week, Locale locale) {
        if (week == null || week.isEmpty()) {
            return null;
        }
        Map<Integer, String> byDay = new TreeMap<>(week.stream().sorted(ORDER).collect(Collectors.groupingBy(
                Slot::dayOfWeek,
                Collectors.mapping(slot -> HH_MM.format(slot.opensAt()) + "–" + HH_MM.format(slot.closesAt()),
                        Collectors.joining(", ")))));
        List<String> groups = new ArrayList<>();
        Integer runStart = null;
        Integer runEnd = null;
        String runText = null;
        for (Map.Entry<Integer, String> entry : byDay.entrySet()) {
            int day = entry.getKey();
            if (runText != null && runEnd == day - 1 && runText.equals(entry.getValue())) {
                runEnd = day;
                continue;
            }
            if (runText != null) {
                groups.add(group(runStart, runEnd, runText, locale));
            }
            runStart = day;
            runEnd = day;
            runText = entry.getValue();
        }
        groups.add(group(runStart, runEnd, runText, locale));
        return String.join("; ", groups);
    }

    /** Localized full day name, capitalized ("Lunes", "Monday"). */
    public static String dayName(int dayOfWeek, Locale locale) {
        String name = DayOfWeek.of(dayOfWeek).getDisplayName(TextStyle.FULL, locale);
        return name.isEmpty() ? name : name.substring(0, 1).toUpperCase(locale) + name.substring(1);
    }

    private static String group(int from, int to, String ranges, Locale locale) {
        String days = from == to ? shortDay(from, locale) : shortDay(from, locale) + "–" + shortDay(to, locale);
        return days + " " + ranges;
    }

    private static String shortDay(int dayOfWeek, Locale locale) {
        String[] names = "en".equals(locale == null ? null : locale.getLanguage()) ? SHORT_EN : SHORT_ES;
        return names[dayOfWeek - 1];
    }
}
