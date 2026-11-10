package com.fenixcore.optibienestar360.modules.onboarding.service;

import com.fenixcore.optibienestar360.modules.onboarding.dto.OnboardingPolicyDto;
import com.fenixcore.optibienestar360.modules.onboarding.dto.OnboardingPolicyUpdateRequest;
import com.fenixcore.optibienestar360.modules.onboarding.entity.OnboardingPolicy;
import com.fenixcore.optibienestar360.modules.onboarding.entity.OnboardingPolicy.AllyApprovalMode;
import com.fenixcore.optibienestar360.modules.onboarding.entity.OnboardingPolicy.AllyPublishMode;
import com.fenixcore.optibienestar360.modules.onboarding.entity.OnboardingPolicy.MemberActivationMode;
import com.fenixcore.optibienestar360.modules.onboarding.entity.OnboardingPolicy.MemberApprovalMode;
import com.fenixcore.optibienestar360.modules.onboarding.repository.OnboardingPolicyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link OnboardingPolicyService}: defaults equal today's behavior, read-only
 * modes while a self-registration is off, and the decisions the flows ask for
 * (hub ADR 0037 §1).
 */
@ExtendWith(MockitoExtension.class)
class OnboardingPolicyServiceTest {

    @Mock private OnboardingPolicyRepository repository;

    private OnboardingPolicyService service() {
        return new OnboardingPolicyService(repository);
    }

    private OnboardingPolicy active(OnboardingPolicy p) {
        p.setUuid(UUID.randomUUID());
        when(repository.findFirstByActiveTrue()).thenReturn(Optional.of(p));
        return p;
    }

    private static OnboardingPolicyUpdateRequest request(boolean member, boolean ally, MemberApprovalMode approval,
                                                         MemberActivationMode activation, AllyApprovalMode allyApproval,
                                                         AllyPublishMode publish) {
        return new OnboardingPolicyUpdateRequest(member, ally, approval, activation, allyApproval, publish, 15, 30);
    }

    private static OnboardingPolicyUpdateRequest retention(Integer memberDays, Integer allyDays) {
        return new OnboardingPolicyUpdateRequest(true, true, MemberApprovalMode.MANUAL,
                MemberActivationMode.ON_PAYMENT_APPROVED, AllyApprovalMode.MANUAL_WITH_AGREEMENT, AllyPublishMode.MANUAL,
                memberDays, allyDays);
    }

    @Test
    void withoutARow_theDefaultsReproduceTodaysBehavior() {
        when(repository.findFirstByActiveTrue()).thenReturn(Optional.empty());
        OnboardingPolicyService service = service();

        OnboardingPolicyDto dto = service.get();

        assertThat(dto.uuid()).isNull();
        assertThat(dto.allowMemberSelfRegistration()).isTrue();
        assertThat(dto.allowAllySelfRegistration()).isTrue();
        assertThat(dto.memberApprovalMode()).isEqualTo(MemberApprovalMode.MANUAL);
        assertThat(dto.memberActivationMode()).isEqualTo(MemberActivationMode.ON_PAYMENT_APPROVED);
        assertThat(dto.allyApprovalMode()).isEqualTo(AllyApprovalMode.MANUAL_WITH_AGREEMENT);
        assertThat(dto.allyPublishMode()).isEqualTo(AllyPublishMode.MANUAL);
        assertThat(service.autoApprovesMember(true)).isFalse();
        assertThat(service.activatesMembershipOnApproval(true)).isFalse();
        assertThat(service.autoApprovesAlly()).isFalse();
        assertThat(service.publishesAllyOnApproval()).isFalse();
        assertThatCode(service::requireMemberSelfRegistration).doesNotThrowAnyException();
        assertThatCode(service::requireAllySelfRegistration).doesNotThrowAnyException();
    }

    @Test
    void update_replacesEveryField() {
        OnboardingPolicy policy = active(new OnboardingPolicy());
        when(repository.save(any(OnboardingPolicy.class))).thenAnswer(inv -> inv.getArgument(0));

        OnboardingPolicyDto dto = service().update(request(true, true, MemberApprovalMode.AUTO_ON_SUBMIT,
                MemberActivationMode.IMMEDIATE, AllyApprovalMode.AUTO_ON_SUBMIT, AllyPublishMode.ON_APPROVAL));

        assertThat(dto.memberApprovalMode()).isEqualTo(MemberApprovalMode.AUTO_ON_SUBMIT);
        assertThat(policy.getMemberActivationMode()).isEqualTo(MemberActivationMode.IMMEDIATE);
        assertThat(policy.getAllyApprovalMode()).isEqualTo(AllyApprovalMode.AUTO_ON_SUBMIT);
        assertThat(policy.getAllyPublishMode()).isEqualTo(AllyPublishMode.ON_APPROVAL);
    }

    @Test
    void draftRetention_defaultsTo15And30Days_andIsNotInThePublicView() {
        when(repository.findFirstByActiveTrue()).thenReturn(Optional.empty());
        OnboardingPolicyService service = service();

        assertThat(service.memberDraftRetentionDays()).isEqualTo(15);
        assertThat(service.allyDraftRetentionDays()).isEqualTo(30);
        assertThat(service.get().memberDraftRetentionDays()).isEqualTo(15);
        assertThat(service.get().allyDraftRetentionDays()).isEqualTo(30);
        assertThat(service.publicView().toString()).doesNotContain("etention");
    }

    @Test
    void update_storesTheDraftRetention_andZeroMeansNeverClean() {
        OnboardingPolicy policy = active(new OnboardingPolicy());
        when(repository.save(any(OnboardingPolicy.class))).thenAnswer(inv -> inv.getArgument(0));

        OnboardingPolicyDto dto = service().update(retention(0, 365));

        assertThat(policy.getMemberDraftRetentionDays()).isZero();
        assertThat(policy.getAllyDraftRetentionDays()).isEqualTo(365);
        assertThat(dto.memberDraftRetentionDays()).isZero();
        assertThat(dto.allyDraftRetentionDays()).isEqualTo(365);
    }

    @Test
    void update_withARetentionOutOfRange_isRefused() {
        active(new OnboardingPolicy());
        OnboardingPolicyService service = service();

        assertThatThrownBy(() -> service.update(retention(-1, 30)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("onboarding_policy.draft_retention.invalid");
        assertThatThrownBy(() -> service.update(retention(15, 366)))
                .hasMessage("onboarding_policy.draft_retention.invalid");
        verify(repository, never()).save(any());
    }

    @Test
    void update_switchingMemberRegistrationOff_keepsItsModesReadOnly() {
        active(new OnboardingPolicy());

        assertThatThrownBy(() -> service().update(request(false, true, MemberApprovalMode.AUTO_ON_SUBMIT,
                MemberActivationMode.ON_PAYMENT_APPROVED, AllyApprovalMode.MANUAL_WITH_AGREEMENT, AllyPublishMode.MANUAL)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("onboarding_policy.member_modes.read_only");
        verify(repository, never()).save(any());
    }

    @Test
    void update_withAllyRegistrationOff_rejectsChangingItsModes_butAcceptsTheSameValues() {
        OnboardingPolicy off = new OnboardingPolicy();
        off.setAllowAllySelfRegistration(false);
        active(off);
        when(repository.save(any(OnboardingPolicy.class))).thenAnswer(inv -> inv.getArgument(0));
        OnboardingPolicyService service = service();

        assertThatThrownBy(() -> service.update(request(true, false, MemberApprovalMode.MANUAL,
                MemberActivationMode.ON_PAYMENT_APPROVED, AllyApprovalMode.MANUAL_WITH_AGREEMENT, AllyPublishMode.ON_APPROVAL)))
                .hasMessage("onboarding_policy.ally_modes.read_only");

        OnboardingPolicyDto dto = service.update(request(true, false, MemberApprovalMode.AUTO_ON_SUBMIT,
                MemberActivationMode.ON_PAYMENT_APPROVED, AllyApprovalMode.MANUAL_WITH_AGREEMENT, AllyPublishMode.MANUAL));
        assertThat(dto.memberApprovalMode()).isEqualTo(MemberApprovalMode.AUTO_ON_SUBMIT);
        assertThat(dto.allowAllySelfRegistration()).isFalse();
    }

    @Test
    void update_switchingRegistrationBackOn_makesTheModesEditableInTheSameRequest() {
        OnboardingPolicy off = new OnboardingPolicy();
        off.setAllowMemberSelfRegistration(false);
        active(off);
        when(repository.save(any(OnboardingPolicy.class))).thenAnswer(inv -> inv.getArgument(0));

        OnboardingPolicyDto dto = service().update(request(true, true, MemberApprovalMode.AUTO_IF_PAYMENT_REPORTED,
                MemberActivationMode.ON_PAYMENT_REPORTED, AllyApprovalMode.MANUAL_WITH_AGREEMENT, AllyPublishMode.MANUAL));

        assertThat(dto.allowMemberSelfRegistration()).isTrue();
        assertThat(dto.memberApprovalMode()).isEqualTo(MemberApprovalMode.AUTO_IF_PAYMENT_REPORTED);
    }

    @Test
    void requireSelfRegistration_whenOff_isASelfRegistrationDisabled403() {
        OnboardingPolicy off = new OnboardingPolicy();
        off.setAllowMemberSelfRegistration(false);
        off.setAllowAllySelfRegistration(false);
        active(off);
        OnboardingPolicyService service = service();

        assertThatThrownBy(service::requireMemberSelfRegistration)
                .isInstanceOf(SelfRegistrationDisabledException.class)
                .hasMessage("onboarding.member_self_registration.disabled")
                .satisfies(ex -> assertThat(((SelfRegistrationDisabledException) ex).getProperties())
                        .containsEntry("code", SelfRegistrationDisabledException.CODE));
        assertThatThrownBy(service::requireAllySelfRegistration)
                .hasMessage("onboarding.ally_self_registration.disabled");
    }

    @Test
    void autoApprovesMember_ifPaymentReported_dependsOnThePayment() {
        OnboardingPolicy p = new OnboardingPolicy();
        p.setMemberApprovalMode(MemberApprovalMode.AUTO_IF_PAYMENT_REPORTED);
        active(p);
        OnboardingPolicyService service = service();

        assertThat(service.autoApprovesMember(true)).isTrue();
        assertThat(service.autoApprovesMember(false)).isFalse();
    }

    @Test
    void activation_onPaymentReported_dependsOnThePayment_andSuspendsOnRejection() {
        OnboardingPolicy p = new OnboardingPolicy();
        p.setMemberActivationMode(MemberActivationMode.ON_PAYMENT_REPORTED);
        active(p);
        OnboardingPolicyService service = service();

        assertThat(service.activatesMembershipOnApproval(true)).isTrue();
        assertThat(service.activatesMembershipOnApproval(false)).isFalse();
        assertThat(service.suspendsOnRejectedPayment()).isTrue();
    }

    @Test
    void activation_immediate_activatesWithoutPayment_andNeverSuspendsOnRejection() {
        OnboardingPolicy p = new OnboardingPolicy();
        p.setMemberActivationMode(MemberActivationMode.IMMEDIATE);
        active(p);
        OnboardingPolicyService service = service();

        assertThat(service.activatesMembershipOnApproval(false)).isTrue();
        assertThat(service.suspendsOnRejectedPayment()).isFalse();
    }
}
