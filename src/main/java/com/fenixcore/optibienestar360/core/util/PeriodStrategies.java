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
 *   <tr><th>Strategy</th><th>Window containing {@code asOf}</th></tr>
 *   <tr><td>DAILY</td><td>the calendar day</td></tr>
 *   <tr><td>WEEKLY</td><td>Monday–Sunday</td></tr>
 *   <tr><td>BIWEEKLY</td><td>1st–15th / 16th–end of month</td></tr>
 *   <tr><td>MONTHLY</td><td>the calendar month</td></tr>
 *   <tr><td>QUARTERLY</td><td>the calendar quarter</td></tr>
 *   <tr><td>SEMIANNUAL</td><td>Jan–Jun / Jul–Dec</td></tr>
 *   <tr><td>ANNUAL</td><td>the calendar year</td></tr>
 * </table>
 */
public final class PeriodStrategies {

    private PeriodStrategies() {}

    /** Inclusive calendar bounds of a period. */
    public record Window(LocalDate start, LocalDate end) {}

    /**
     * The window of the given strategy that contains {@code asOf}.
     *
     * @param strategy one of DAILY/WEEKLY/BIWEEKLY/MONTHLY/QUARTERLY/SEMIANNUAL/ANNUAL
     * @throws IllegalArgumentException on an unknown strategy
     */
    public static Window window(String strategy, LocalDate asOf) {
        return switch (strategy) {
            case "DAILY" -> new Window(asOf, asOf);
            case "WEEKLY" -> new Window(
                    asOf.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                    asOf.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)));
            case "BIWEEKLY" -> asOf.getDayOfMonth() <= 15
                    ? new Window(asOf.withDayOfMonth(1), asOf.withDayOfMonth(15))
                    : new Window(asOf.withDayOfMonth(16), asOf.with(TemporalAdjusters.lastDayOfMonth()));
            case "MONTHLY" -> new Window(
                    asOf.withDayOfMonth(1), asOf.with(TemporalAdjusters.lastDayOfMonth()));
            case "QUARTERLY" -> {
                LocalDate start = asOf.with(IsoFields.DAY_OF_QUARTER, 1);
                yield new Window(start, start.plusMonths(3).minusDays(1));
            }
            case "SEMIANNUAL" -> asOf.getMonthValue() <= 6
                    ? new Window(asOf.withDayOfYear(1), LocalDate.of(asOf.getYear(), 6, 30))
                    : new Window(LocalDate.of(asOf.getYear(), 7, 1),
                            asOf.with(TemporalAdjusters.lastDayOfYear()));
            case "ANNUAL" -> new Window(
                    asOf.withDayOfYear(1), asOf.with(TemporalAdjusters.lastDayOfYear()));
            default -> throw new IllegalArgumentException("Unknown period strategy: " + strategy);
        };
    }
}
