package com.fenixcore.optibienestar360.modules.membership.service;

import com.fenixcore.optibienestar360.modules.membership.entity.Plan;
import com.fenixcore.optibienestar360.modules.membership.entity.TermMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Plan-level term validation (V220 coherence CHECK mirrored as 422 messages). */
class PlansServiceTermTest {

    @Test
    void noReinscription_withoutTermFields_isValid() {
        assertThatCode(() -> PlansService.validateTerm(new Plan())).doesNotThrowAnyException();
    }

    @Test
    void noReinscription_withTermFields_isRejected() {
        Plan plan = new Plan();
        plan.setTermMode(TermMode.PERIOD);
        plan.setTermPeriodMonths(12);

        assertThatThrownBy(() -> PlansService.validateTerm(plan)).hasMessage("plan.term.requires_reinscription");
    }

    @Test
    void reinscription_withoutMode_isRejected() {
        Plan plan = new Plan();
        plan.setReinscriptionRequired(true);

        assertThatThrownBy(() -> PlansService.validateTerm(plan)).hasMessage("plan.term.mode_required");
    }

    @Test
    void reinscription_fixedDate_needsMonthAndDay() {
        Plan plan = new Plan();
        plan.setReinscriptionRequired(true);
        plan.setTermMode(TermMode.FIXED_DATE);
        plan.setTermFixedMonth(12);

        assertThatThrownBy(() -> PlansService.validateTerm(plan)).hasMessage("plan.term.fixed_date_required");

        plan.setTermFixedDay(1);
        assertThatCode(() -> PlansService.validateTerm(plan)).doesNotThrowAnyException();
    }

    @Test
    void reinscription_period_needsMonths_andNoFixedDate() {
        Plan plan = new Plan();
        plan.setReinscriptionRequired(true);
        plan.setTermMode(TermMode.PERIOD);

        assertThatThrownBy(() -> PlansService.validateTerm(plan)).hasMessage("plan.term.period_required");

        plan.setTermPeriodMonths(6);
        assertThatCode(() -> PlansService.validateTerm(plan)).doesNotThrowAnyException();

        plan.setTermFixedDay(1);
        plan.setTermFixedMonth(1);
        assertThatThrownBy(() -> PlansService.validateTerm(plan)).hasMessage("plan.term.fixed_date_not_allowed");
    }
}
