package com.fenixcore.optibienestar360.core.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;

/**
 * Fixed calendar period bounds for the seven period strategies shared across the
 * commission engine (commission tiers, leaderboard, prizes) and the bonus engine.
 * We deliberately use fixed calendar periods — never sliding windows — so every
 * report and payout lines up with accounting.
 *
 * <table>
 *   <tr><th>Strategy</th><th>Window containing {@code asOf} (no anchor)</th></tr>
 *   <tr><td>DAILY</td><td>the calendar day</td></tr>
 *   <tr><td>WEEKLY</td><td>Monday–Sunday</td></tr>
 *   <tr><td>BIWEEKLY</td><td>1st–15th / 16th–end of month</td></tr>
 *   <tr><td>MONTHLY</td><td>the calendar month</td></tr>
 *   <tr><td>QUARTERLY</td><td>the calendar quarter</td></tr>
 *   <tr><td>SEMIANNUAL</td><td>Jan–Jun / Jul–Dec</td></tr>
 *   <tr><td>ANNUAL</td><td>the calendar year</td></tr>
 * </table>
 *
 * <p><b>Anchor (Fase A, hub plan commission-frequency-currency-unification,
 * phase 2)</b>: {@link #window(String, LocalDate, Short)} accepts an optional
 * {@code anchor} that shifts the window boundary away from the fixed-calendar
 * default above:</p>
 * <ul>
 *   <li>{@code WEEKLY} — {@code anchor} is an ISO day-of-week, {@code 1=Monday}
 *       … {@code 7=Sunday} ({@link DayOfWeek#of(int)} convention), and becomes
 *       the first day of the 7-day window instead of the hardcoded Monday.</li>
 *   <li>{@code BIWEEKLY} — {@code anchor} (1-31) is the day-of-month that splits
 *       the first half from the second half, instead of the hardcoded 15/16
 *       split ({@code asOf.day <= anchor} → 1st..anchor; else (anchor+1)..end
 *       of month). Clamped to the month's actual length.</li>
 *   <li>{@code MONTHLY}/{@code QUARTERLY}/{@code SEMIANNUAL}/{@code ANNUAL} —
 *       {@code anchor} (1-31) is the day-of-month a period "rolls over" on
 *       (a billing-cycle cut day), instead of always starting on the 1st.
 *       Clamped to the last day of the relevant month when that month is
 *       shorter (e.g. {@code anchor=31} in February → the 28th/29th).</li>
 *   <li>{@code DAILY} — {@code anchor} is ignored (no sub-day granularity).</li>
 * </ul>
 * <p>{@code anchor == null} (or the 2-arg overload) reproduces the fixed
 * default above exactly — existing rules that never set an anchor see zero
 * behavior change.</p>
 */
public final class PeriodStrategies {

    private PeriodStrategies() {}

    /** Inclusive calendar bounds of a period. */
    public record Window(LocalDate start, LocalDate end) {}

    /**
     * The window of the given strategy that contains {@code asOf}, using the
     * fixed-calendar default boundary (no anchor). Equivalent to {@link
     * #window(String, LocalDate, Short)} with a {@code null} anchor.
     *
     * @param strategy one of DAILY/WEEKLY/BIWEEKLY/MONTHLY/QUARTERLY/SEMIANNUAL/ANNUAL
     * @throws IllegalArgumentException on an unknown strategy
     */
    public static Window window(String strategy, LocalDate asOf) {
        return window(strategy, asOf, null);
    }

    /**
     * The window of the given strategy that contains {@code asOf}, optionally
     * shifted by {@code anchor} — see the class Javadoc for the per-strategy
     * anchor semantics. {@code anchor == null} reproduces the fixed-calendar
     * default exactly.
     *
     * @param strategy one of DAILY/WEEKLY/BIWEEKLY/MONTHLY/QUARTERLY/SEMIANNUAL/ANNUAL
     * @throws IllegalArgumentException on an unknown strategy
     */
    public static Window window(String strategy, LocalDate asOf, Short anchor) {
        return switch (strategy) {
            case "DAILY" -> new Window(asOf, asOf);
            case "WEEKLY" -> weeklyWindow(asOf, anchor);
            case "BIWEEKLY" -> biweeklyWindow(asOf, anchor);
            case "MONTHLY" -> anchoredWindow(asOf, anchor, 1, naturalMonthlyStart(asOf));
            case "QUARTERLY" -> anchoredWindow(asOf, anchor, 3, naturalQuarterlyStart(asOf));
            case "SEMIANNUAL" -> anchoredWindow(asOf, anchor, 6, naturalSemiannualStart(asOf));
            case "ANNUAL" -> anchoredWindow(asOf, anchor, 12, naturalAnnualStart(asOf));
            default -> throw new IllegalArgumentException("Unknown period strategy: " + strategy);
        };
    }

    // ─── WEEKLY ─────────────────────────────────────────────────────────────

    private static Window weeklyWindow(LocalDate asOf, Short anchor) {
        DayOfWeek startDay = anchor != null ? DayOfWeek.of(anchor) : DayOfWeek.MONDAY;
        LocalDate start = asOf.with(TemporalAdjusters.previousOrSame(startDay));
        return new Window(start, start.plusDays(6));
    }

    // ─── BIWEEKLY ───────────────────────────────────────────────────────────

    private static Window biweeklyWindow(LocalDate asOf, Short anchor) {
        int splitDay = anchor != null ? Math.min(anchor, asOf.lengthOfMonth()) : 15;
        return asOf.getDayOfMonth() <= splitDay
                ? new Window(asOf.withDayOfMonth(1), asOf.withDayOfMonth(splitDay))
                : new Window(asOf.withDayOfMonth(splitDay + 1), asOf.with(TemporalAdjusters.lastDayOfMonth()));
    }

    // ─── MONTHLY / QUARTERLY / SEMIANNUAL / ANNUAL (day-of-month cut anchor) ──

    private static LocalDate naturalMonthlyStart(LocalDate asOf) {
        return asOf.withDayOfMonth(1);
    }

    private static LocalDate naturalQuarterlyStart(LocalDate asOf) {
        return asOf.with(IsoFields.DAY_OF_QUARTER, 1);
    }

    private static LocalDate naturalSemiannualStart(LocalDate asOf) {
        return asOf.getMonthValue() <= 6
                ? LocalDate.of(asOf.getYear(), 1, 1)
                : LocalDate.of(asOf.getYear(), 7, 1);
    }

    private static LocalDate naturalAnnualStart(LocalDate asOf) {
        return asOf.withDayOfYear(1);
    }

    /** Day-of-month {@code anchor}, clamped to the actual length of {@code monthBase}'s month. */
    private static int clampAnchorDay(Short anchor, LocalDate monthBase) {
        return Math.min(anchor, monthBase.lengthOfMonth());
    }

    /**
     * Generic "N calendar months, rolling over on day-of-month {@code anchor}"
     * window. {@code naturalStart} is the fixed-calendar (anchor=1) period
     * start containing {@code asOf} — used both as the fallback when {@code
     * anchor == null} and as the calendar-month anchor point to shift within
     * when it isn't. Reproduces the fixed-calendar window exactly when {@code
     * anchor} is {@code null} or {@code 1}.
     */
    private static Window anchoredWindow(LocalDate asOf, Short anchor, int periodMonths, LocalDate naturalStart) {
        if (anchor == null) {
            LocalDate nextStart = naturalStart.plusMonths(periodMonths);
            return new Window(naturalStart, nextStart.minusDays(1));
        }

        int clampedDay = clampAnchorDay(anchor, naturalStart);
        LocalDate candidateStart = naturalStart.withDayOfMonth(clampedDay);
        LocalDate start;
        if (!asOf.isBefore(candidateStart)) {
            start = candidateStart;
        } else {
            LocalDate prevBase = naturalStart.minusMonths(periodMonths);
            start = prevBase.withDayOfMonth(clampAnchorDay(anchor, prevBase));
        }

        LocalDate nextBase = LocalDate.of(start.getYear(), start.getMonth(), 1).plusMonths(periodMonths);
        LocalDate nextStart = nextBase.withDayOfMonth(clampAnchorDay(anchor, nextBase));
        return new Window(start, nextStart.minusDays(1));
    }
}
