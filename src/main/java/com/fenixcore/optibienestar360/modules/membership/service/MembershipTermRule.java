package com.fenixcore.optibienestar360.modules.membership.service;

import com.fenixcore.optibienestar360.modules.membership.entity.TermMode;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * The term-expiry rule of a plan / membership snapshot, plus the date arithmetic (hub ADR 0034).
 *
 * <p>The fixed day is limited to 1-28 (enforced by the V220 CHECK and by {@link #validate}), so the
 * date exists in every month and no clamping is needed.</p>
 */
public record MembershipTermRule(TermMode mode, Integer fixedMonth, Integer fixedDay, Integer periodMonths) {

    public static final int MAX_FIXED_DAY = 28;
    /** Period used when a re-inscription override is set on a plan that defines no rule. */
    public static final int DEFAULT_PERIOD_MONTHS = 12;

    public static MembershipTermRule of(com.fenixcore.optibienestar360.modules.membership.entity.Plan plan) {
        return plan.isReinscriptionRequired() && plan.getTermMode() != null
                ? new MembershipTermRule(plan.getTermMode(), plan.getTermFixedMonth(), plan.getTermFixedDay(), plan.getTermPeriodMonths())
                : null;
    }

    public static MembershipTermRule of(com.fenixcore.optibienestar360.modules.membership.entity.Membership membership) {
        return membership.isReinscriptionRequired() && membership.getTermMode() != null
                ? new MembershipTermRule(membership.getTermMode(), membership.getTermFixedMonth(),
                        membership.getTermFixedDay(), membership.getTermPeriodMonths())
                : null;
    }

    public static MembershipTermRule period(int months) {
        return new MembershipTermRule(TermMode.PERIOD, null, null, months);
    }

    public static MembershipTermRule fixedDate(int month, int day) {
        return new MembershipTermRule(TermMode.FIXED_DATE, month, day, null);
    }

    /**
     * Checks the rule is coherent; throws {@link IllegalArgumentException} with the message key otherwise.
     * A {@code null} mode is valid only when no other field is set.
     */
    public static void validate(TermMode mode, Integer month, Integer day, Integer months) {
        if (mode == null) {
            if (month != null || day != null || months != null) {
                throw new IllegalArgumentException("plan.term.mode_required");
            }
            return;
        }
        if (mode == TermMode.FIXED_DATE) {
            if (month == null || day == null) {
                throw new IllegalArgumentException("plan.term.fixed_date_required");
            }
            if (months != null) {
                throw new IllegalArgumentException("plan.term.period_not_allowed");
            }
            if (month < 1 || month > 12 || day < 1 || day > MAX_FIXED_DAY) {
                throw new IllegalArgumentException("plan.term.fixed_date_invalid");
            }
        } else {
            if (months == null || months < 1) {
                throw new IllegalArgumentException("plan.term.period_required");
            }
            if (month != null || day != null) {
                throw new IllegalArgumentException("plan.term.fixed_date_not_allowed");
            }
        }
    }

    /**
     * First expiry for an enrollment: FIXED_DATE → next occurrence of month/day strictly after
     * {@code enrolledAt}; PERIOD → {@code enrolledAt} + N months.
     */
    public LocalDate firstExpiry(LocalDate enrolledAt) {
        if (mode == TermMode.PERIOD) {
            return enrolledAt.plusMonths(periodMonths);
        }
        LocalDate thisYear = YearMonth.of(enrolledAt.getYear(), fixedMonth).atDay(fixedDay);
        return thisYear.isAfter(enrolledAt) ? thisYear : thisYear.plusYears(1);
    }

    /** Expiry of the term that starts at {@code termStart}: FIXED_DATE → +12 months; PERIOD → +N months. */
    public LocalDate nextExpiry(LocalDate termStart) {
        return termStart.plusMonths(mode == TermMode.PERIOD ? periodMonths : 12);
    }
}
