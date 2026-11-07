package com.fenixcore.optibienestar360.modules.allyrequest.importer;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The template's opening-hours text → weekly ranges (ISO day 1–7):
 * {@code "L-V 08:00-12:00, 14:00-18:00; S 08:00-12:00"}. Blocks are separated
 * by {@code ;} (or a line break); each block is a day spec — one day
 * ({@code S}), a span ({@code L-V}) or a list ({@code L,X,V}) with the
 * Spanish initials L M X J V S D — followed by one or more
 * {@code HH:MM-HH:MM} ranges separated by commas. Closing must be after
 * opening (no ranges past midnight).
 */
public final class OpeningHoursParser {

    public record Range(short dayOfWeek, LocalTime opensAt, LocalTime closesAt) {}

    private static final Map<Character, Integer> DAYS = Map.of(
            'L', 1, 'M', 2, 'X', 3, 'J', 4, 'V', 5, 'S', 6, 'D', 7);
    private static final Pattern BLOCK = Pattern.compile("^([LMXJVSD](?:\\s*[-,]\\s*[LMXJVSD])*)\\s+(.+)$");
    private static final Pattern RANGE = Pattern.compile("^(\\d{1,2}:\\d{2})\\s*-\\s*(\\d{1,2}:\\d{2})$");

    private OpeningHoursParser() {}

    /** @throws IllegalArgumentException when the text does not follow the format */
    public static List<Range> parse(String text) {
        List<Range> ranges = new ArrayList<>();
        if (text == null || text.isBlank()) return ranges;
        for (String rawBlock : text.split("[;\\n]")) {
            String block = rawBlock.trim().toUpperCase(Locale.ROOT);
            if (block.isEmpty()) continue;
            Matcher m = BLOCK.matcher(block);
            if (!m.matches()) throw new IllegalArgumentException(rawBlock.trim());
            List<Integer> days = days(m.group(1).replace(" ", ""));
            for (String rawRange : m.group(2).split(",")) {
                Matcher r = RANGE.matcher(rawRange.trim());
                if (!r.matches()) throw new IllegalArgumentException(rawRange.trim());
                LocalTime opens = time(r.group(1));
                LocalTime closes = time(r.group(2));
                if (!closes.isAfter(opens)) throw new IllegalArgumentException(rawRange.trim());
                for (int day : days) {
                    ranges.add(new Range((short) day, opens, closes));
                }
            }
        }
        return ranges;
    }

    private static List<Integer> days(String spec) {
        List<Integer> days = new ArrayList<>();
        for (String part : spec.split(",")) {
            if (part.contains("-")) {
                String[] bounds = part.split("-");
                if (bounds.length != 2) throw new IllegalArgumentException(spec);
                int from = DAYS.get(bounds[0].charAt(0));
                int to = DAYS.get(bounds[1].charAt(0));
                if (to < from) throw new IllegalArgumentException(spec);
                for (int d = from; d <= to; d++) days.add(d);
            } else {
                days.add(DAYS.get(part.charAt(0)));
            }
        }
        return days;
    }

    private static LocalTime time(String hhmm) {
        try {
            return LocalTime.parse(hhmm.length() == 4 ? "0" + hhmm : hhmm);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(hhmm);
        }
    }
}
