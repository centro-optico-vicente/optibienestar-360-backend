package com.fenixcore.optibienestar360.modules.onboarding.service;

import com.fenixcore.optibienestar360.modules.affiliation.entity.AffiliationRequest;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType.Segment;
import com.fenixcore.optibienestar360.modules.catalog.entity.City;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan;
import com.fenixcore.optibienestar360.modules.onboarding.dto.RegistrationProgressDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The progress and "show the completion portal" rules (hub ADR 0037 §3). */
class RegistrationProgressServiceTest {

    private static AffiliationRequest request(String status) {
        AffiliationRequest r = new AffiliationRequest();
        r.setStatus(status);
        return r;
    }

    private static AffiliationRequest completeSteps(AffiliationRequest r) {
        r.setBirthDate(LocalDate.of(1985, 3, 15));
        r.setPlan(new Plan());
        r.setTermsAcceptedAt(Instant.now());
        return r;
    }

    private static boolean done(RegistrationProgressDto p, String key) {
        return p.steps().stream().filter(s -> s.key().equals(key)).findFirst().orElseThrow().done();
    }

    @Test
    void aFreshDraft_hasNothingDone_andKeepsThePortalOpen() {
        RegistrationProgressDto p = RegistrationProgressService.memberProgress(request("DRAFT"));

        assertThat(p.kind()).isEqualTo("MEMBER");
        assertThat(p.steps()).extracting(s -> s.key()).containsExactly("personal", "plan", "payment", "family");
        assertThat(p.steps()).filteredOn(s -> s.essential()).extracting(s -> s.key()).containsExactly("personal", "plan");
        assertThat(p.percent()).isZero();
        assertThat(p.submitted()).isFalse();
        assertThat(p.showCompletionPortal()).isTrue();
    }

    @Test
    void essentialsDone_countAsProgress() {
        RegistrationProgressDto p = RegistrationProgressService.memberProgress(completeSteps(request("DRAFT")));

        assertThat(done(p, "personal")).isTrue();
        assertThat(done(p, "plan")).isTrue();
        assertThat(done(p, "payment")).isFalse();
        assertThat(p.percent()).isEqualTo(50);
    }

    @Test
    void autoApproval_doesNotHideThePortalWhileOptionalStepsAreMissing() {
        // Policy approval has no reviewer: the registration is APPROVED but not confirmed by an operator.
        AffiliationRequest r = completeSteps(request("APPROVED"));

        RegistrationProgressDto p = RegistrationProgressService.memberProgress(r);

        assertThat(p.submitted()).isTrue();
        assertThat(p.confirmedByOperator()).isFalse();
        assertThat(p.showCompletionPortal()).isTrue();
    }

    @Test
    void aManualApproval_hidesThePortal() {
        AffiliationRequest r = completeSteps(request("APPROVED"));
        r.setReviewedBy(new User());

        RegistrationProgressDto p = RegistrationProgressService.memberProgress(r);

        assertThat(p.confirmedByOperator()).isTrue();
        assertThat(p.showCompletionPortal()).isFalse();
    }

    @Test
    void everythingDoneAndSubmitted_hidesThePortal_butEverythingDoneAsDraftDoesNot() {
        AffiliationRequest all = completeSteps(request("DRAFT"));
        all.setPaymentMethod(new com.fenixcore.optibienestar360.modules.payment.entity.PaymentMethod());
        all.setFamily(List.of(java.util.Map.of("firstName", "Luis")));

        assertThat(RegistrationProgressService.memberProgress(all).percent()).isEqualTo(100);
        assertThat(RegistrationProgressService.memberProgress(all).showCompletionPortal()).isTrue();

        all.setStatus("PENDING");
        assertThat(RegistrationProgressService.memberProgress(all).showCompletionPortal()).isFalse();
    }

    @Test
    void aRejectedRegistration_hidesThePortal() {
        assertThat(RegistrationProgressService.memberProgress(request("REJECTED")).showCompletionPortal()).isFalse();
    }

    // ─── Ally ───────────────────────────────────────────────────────────────

    private static Ally ally(Segment segment) {
        Ally a = new Ally();
        a.setSegment(segment);
        a.setRegistrationStatus(Ally.RegistrationStatus.PENDING);
        a.setRegistrationChannel(Ally.RegistrationChannel.SELF_SERVICE);
        return a;
    }

    @Test
    void anAllyAfterStepOne_hasNoEssentialDone() {
        RegistrationProgressDto p = RegistrationProgressService.allyProgress(ally(Segment.B2B), false);

        assertThat(p.kind()).isEqualTo("ALLY");
        assertThat(p.steps()).extracting(s -> s.key()).containsExactly("location", "fiscal", "profile");
        assertThat(p.percent()).isZero();
        assertThat(p.submitted()).isFalse();
        assertThat(p.showCompletionPortal()).isTrue();
    }

    @Test
    void aCommercialAlly_needsTypeAndRif_aProfessionalNeedsTypeAndProfession() {
        Ally b2b = ally(Segment.B2B);
        b2b.setCity(new City());
        b2b.getAllyTypes().add(new AllyType());
        assertThat(done(RegistrationProgressService.allyProgress(b2b, false), "fiscal")).isFalse();
        b2b.setTaxDocumentType("J");
        b2b.setTaxDocumentNumber("123456789");
        RegistrationProgressDto p = RegistrationProgressService.allyProgress(b2b, false);
        assertThat(done(p, "fiscal")).isTrue();
        assertThat(done(p, "location")).isTrue();

        Ally b2p = ally(Segment.B2P);
        b2p.getAllyTypes().add(new AllyType());
        assertThat(done(RegistrationProgressService.allyProgress(b2p, false), "fiscal")).isFalse();
        b2p.getProfessions().add(new com.fenixcore.optibienestar360.modules.catalog.entity.Profession());
        assertThat(done(RegistrationProgressService.allyProgress(b2p, false), "fiscal")).isTrue();
    }

    @Test
    void anAllyApprovedByAnOperator_hidesThePortal_theOneApprovedByThePolicyDoesNot() {
        Ally a = ally(Segment.B2B);
        a.setRegistrationStatus(Ally.RegistrationStatus.APPROVED);
        a.setRegistrationSubmittedAt(Instant.now());

        assertThat(RegistrationProgressService.allyProgress(a, false).showCompletionPortal()).isTrue();

        a.setApprovedBy(UUID.randomUUID());
        RegistrationProgressDto p = RegistrationProgressService.allyProgress(a, false);
        assertThat(p.confirmedByOperator()).isTrue();
        assertThat(p.showCompletionPortal()).isFalse();
    }
}
