package com.fenixcore.optibienestar360.modules.affiliation.service;

import com.fenixcore.optibienestar360.modules.membership.entity.Plan;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan.PlanType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Which plans a single person may self-enroll in through {@code POST /v1/public/affiliation-requests}.
 */
class AffiliationRequestServicePlanRuleTest {

    private static Plan plan(PlanType type, Integer minMembers, boolean active, boolean published) {
        Plan plan = new Plan();
        plan.setType(type);
        plan.setMinMembers(minMembers);
        plan.setActive(active);
        plan.setPublished(published);
        return plan;
    }

    @Test
    void acceptsPublishedIndividualAndFamilyPlans() {
        assertThatCode(() -> AffiliationRequestService.requireSelfServicePlan(plan(PlanType.INDIVIDUAL, null, true, true)))
                .doesNotThrowAnyException();
        assertThatCode(() -> AffiliationRequestService.requireSelfServicePlan(plan(PlanType.FAMILY, 1, true, true)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingInactiveOrUnpublishedPlans() {
        assertThatThrownBy(() -> AffiliationRequestService.requireSelfServicePlan(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("affiliation.plan.not_available");
        assertThatThrownBy(() -> AffiliationRequestService.requireSelfServicePlan(plan(PlanType.FAMILY, null, false, true)))
                .hasMessage("affiliation.plan.not_available");
        assertThatThrownBy(() -> AffiliationRequestService.requireSelfServicePlan(plan(PlanType.FAMILY, null, true, false)))
                .hasMessage("affiliation.plan.not_available");
    }

    @Test
    void rejectsCorporateAndMinimumHeadcountPlans() {
        assertThatThrownBy(() -> AffiliationRequestService.requireSelfServicePlan(plan(PlanType.CORPORATE, 20, true, true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("affiliation.plan.group_only");
        assertThatThrownBy(() -> AffiliationRequestService.requireSelfServicePlan(plan(PlanType.CORPORATE, null, true, true)))
                .hasMessage("affiliation.plan.group_only");
        assertThatThrownBy(() -> AffiliationRequestService.requireSelfServicePlan(plan(PlanType.FAMILY, 5, true, true)))
                .hasMessage("affiliation.plan.group_only");
    }
}
