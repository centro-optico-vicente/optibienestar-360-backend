package com.fenixcore.optibienestar360.modules.membership.service;

import com.fenixcore.optibienestar360.modules.membership.entity.TermMode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Date arithmetic and validation of the term rule (hub ADR 0034). */
class MembershipTermRuleTest {

    @Test
    void firstExpiry_fixedDate_beforeEnrollmentInTheYear_rollsToNextYear() {
        var rule = MembershipTermRule.fixedDate(12, 1);

        assertThat(rule.firstExpiry(LocalDate.of(2026, 12, 15))).isEqualTo(LocalDate.of(2027, 12, 1));
    }

    @Test
    void firstExpiry_fixedDate_afterEnrollmentInTheYear_staysInTheYear() {
        var rule = MembershipTermRule.fixedDate(12, 1);

        assertThat(rule.firstExpiry(LocalDate.of(2026, 11, 1))).isEqualTo(LocalDate.of(2026, 12, 1));
    }

    @Test
    void firstExpiry_fixedDate_onTheSameDay_isStrictlyAfter_soNextYear() {
        var rule = MembershipTermRule.fixedDate(12, 1);

        assertThat(rule.firstExpiry(LocalDate.of(2026, 12, 1))).isEqualTo(LocalDate.of(2027, 12, 1));
    }

    @Test
    void firstExpiry_fixedDate_day28ExistsInFebruary() {
        var rule = MembershipTermRule.fixedDate(2, 28);

        assertThat(rule.firstExpiry(LocalDate.of(2027, 1, 31))).isEqualTo(LocalDate.of(2027, 2, 28));
        assertThat(rule.firstExpiry(LocalDate.of(2027, 2, 28))).isEqualTo(LocalDate.of(2028, 2, 28));
    }

    @Test
    void firstExpiry_period_addsCalendarMonthsToEnrollment() {
        assertThat(MembershipTermRule.period(12).firstExpiry(LocalDate.of(2026, 11, 1))).isEqualTo(LocalDate.of(2027, 11, 1));
        assertThat(MembershipTermRule.period(3).firstExpiry(LocalDate.of(2026, 11, 30))).isEqualTo(LocalDate.of(2027, 2, 28));
    }

    @Test
    void nextExpiry_fixedDateAddsTwelveMonths_periodAddsItsMonths() {
        assertThat(MembershipTermRule.fixedDate(12, 1).nextExpiry(LocalDate.of(2026, 12, 1))).isEqualTo(LocalDate.of(2027, 12, 1));
        assertThat(MembershipTermRule.period(6).nextExpiry(LocalDate.of(2026, 12, 1))).isEqualTo(LocalDate.of(2027, 6, 1));
    }

    @Test
    void validate_acceptsCoherentRules() {
        MembershipTermRule.validate(null, null, null, null);
        MembershipTermRule.validate(TermMode.FIXED_DATE, 12, 28, null);
        MembershipTermRule.validate(TermMode.PERIOD, null, null, 6);
    }

    @Test
    void validate_rejectsIncoherentRules() {
        assertThatThrownBy(() -> MembershipTermRule.validate(null, 12, 1, null)).hasMessage("plan.term.mode_required");
        assertThatThrownBy(() -> MembershipTermRule.validate(TermMode.FIXED_DATE, 12, null, null)).hasMessage("plan.term.fixed_date_required");
        assertThatThrownBy(() -> MembershipTermRule.validate(TermMode.FIXED_DATE, 12, 29, null)).hasMessage("plan.term.fixed_date_invalid");
        assertThatThrownBy(() -> MembershipTermRule.validate(TermMode.FIXED_DATE, 13, 1, null)).hasMessage("plan.term.fixed_date_invalid");
        assertThatThrownBy(() -> MembershipTermRule.validate(TermMode.FIXED_DATE, 12, 1, 6)).hasMessage("plan.term.period_not_allowed");
        assertThatThrownBy(() -> MembershipTermRule.validate(TermMode.PERIOD, null, null, null)).hasMessage("plan.term.period_required");
        assertThatThrownBy(() -> MembershipTermRule.validate(TermMode.PERIOD, null, null, 0)).hasMessage("plan.term.period_required");
        assertThatThrownBy(() -> MembershipTermRule.validate(TermMode.PERIOD, 12, 1, 6)).hasMessage("plan.term.fixed_date_not_allowed");
    }
}
