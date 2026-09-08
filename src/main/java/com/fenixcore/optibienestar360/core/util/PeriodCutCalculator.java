package com.fenixcore.optibienestar360.core.util;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Generic period-clipping utility for "corte parcial + retroactivo al
 * cierre" (hub plan ".ai/plans/2026-09-07-hierarchical-commissions-plan.md"
 * §3). Given a settlement window (the period whose close triggers the
 * retroactive top-up — typically {@code MONTHLY}) and a payout-cut
 * {@link PeriodStrategies} strategy (the frequency partial payouts happen
 * at — {@code WEEKLY} in the first concrete case, but any of the 7), slices
 * the settlement window into cut sub-windows.
 *
 * <p>Deliberately strategy-agnostic on both sides: the cut strategy needn't
 * "fit evenly" inside the settlement strategy (a {@code WEEKLY} cut inside a
 * {@code MONTHLY} settlement never divides evenly — the first/last cut of
 * the month is a partial week, exactly the ISO-week-clipped-to-calendar-
 * month case from the whiteboard example). This works by walking the
 * settlement window day by day in cut-sized strides: at each step, resolve
 * the cut strategy's <em>natural</em> window containing the current pointer
 * (via {@link PeriodStrategies#window}) and clip it to the settlement
 * window's bounds — so a {@code QUARTERLY} cut inside an {@code ANNUAL}
 * settlement, or any other combination, is handled by the same loop with no
 * special-casing.</p>
 */
public final class PeriodCutCalculator {

    private PeriodCutCalculator() {}

    /** One partial-payout cut, already clipped to its containing settlement window. */
    public record Cut(LocalDate start, LocalDate end) {}

    /**
     * @param cutStrategy one of {@code DAILY|WEEKLY|BIWEEKLY|MONTHLY|QUARTERLY|SEMIANNUAL|ANNUAL}
     * @param settlementStart inclusive start of the settlement window
     * @param settlementEnd inclusive end of the settlement window (must be {@code >= settlementStart})
     * @throws IllegalArgumentException when {@code settlementEnd < settlementStart}
     */
    public static List<Cut> cuts(String cutStrategy, LocalDate settlementStart, LocalDate settlementEnd) {
        if (settlementEnd.isBefore(settlementStart)) {
            throw new IllegalArgumentException("period_cut_calculator.invalid_range");
        }
        List<Cut> result = new ArrayList<>();
        LocalDate pointer = settlementStart;
        while (!pointer.isAfter(settlementEnd)) {
            PeriodStrategies.Window natural = PeriodStrategies.window(cutStrategy, pointer);
            LocalDate cutStart = natural.start().isBefore(settlementStart) ? settlementStart : natural.start();
            LocalDate cutEnd = natural.end().isAfter(settlementEnd) ? settlementEnd : natural.end();
            result.add(new Cut(cutStart, cutEnd));
            pointer = cutEnd.plusDays(1);
        }
        return result;
    }

    /**
     * The single cut (already clipped to the settlement window) that
     * contains {@code asOf} — the "which cut am I paying right now" lookup a
     * settlement job runner uses instead of enumerating every cut of the
     * period. {@code asOf} outside {@code [settlementStart, settlementEnd]}
     * is clamped to the nearest bound.
     */
    public static Cut cutContaining(String cutStrategy, LocalDate settlementStart, LocalDate settlementEnd, LocalDate asOf) {
        LocalDate clamped = asOf.isBefore(settlementStart) ? settlementStart
                : asOf.isAfter(settlementEnd) ? settlementEnd : asOf;
        PeriodStrategies.Window natural = PeriodStrategies.window(cutStrategy, clamped);
        LocalDate cutStart = natural.start().isBefore(settlementStart) ? settlementStart : natural.start();
        LocalDate cutEnd = natural.end().isAfter(settlementEnd) ? settlementEnd : natural.end();
        return new Cut(cutStart, cutEnd);
    }
}
