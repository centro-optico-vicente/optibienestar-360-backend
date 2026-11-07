package com.fenixcore.optibienestar360.modules.onboarding.event;

import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership.LifecycleStatus;
import com.fenixcore.optibienestar360.modules.onboarding.service.OnboardingPolicyService;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import com.fenixcore.optibienestar360.modules.payment.event.PaymentRejectedEvent;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentRepository;
import com.fenixcore.optibienestar360.modules.promotion.service.PromotionLifecycle;
import com.fenixcore.optibienestar360.modules.validator.service.ValidatorCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** {@link ProvisionalActivationRevoker}: a rejected payment takes back an ON_PAYMENT_REPORTED activation (hub ADR 0037). */
@ExtendWith(MockitoExtension.class)
class ProvisionalActivationRevokerTest {

    @Mock private OnboardingPolicyService onboardingPolicyService;
    @Mock private PaymentRepository paymentRepository;
    @Mock private PromotionLifecycle promotionLifecycle;
    @Mock private ValidatorCacheService validatorCacheService;

    private ProvisionalActivationRevoker revoker;
    private Payment payment;
    private Membership membership;
    private Member member;

    @BeforeEach
    void setUp() {
        revoker = new ProvisionalActivationRevoker(onboardingPolicyService, paymentRepository, promotionLifecycle,
                validatorCacheService);
        member = new Member();
        member.setId(5L);
        membership = new Membership();
        membership.setUuid(UUID.randomUUID());
        membership.setMember(member);
        membership.setStatus(LifecycleStatus.ACTIVE.name());
        payment = new Payment();
        payment.setId(40L);
        payment.setUuid(UUID.randomUUID());
        payment.setMembership(membership);
    }

    @Test
    void withTheDefaultPolicy_nothingChanges() {
        revoker.onPaymentRejected(new PaymentRejectedEvent(40L));

        assertThat(membership.getStatus()).isEqualTo(LifecycleStatus.ACTIVE.name());
        verifyNoInteractions(paymentRepository, promotionLifecycle, validatorCacheService);
    }

    @Test
    void onPaymentReported_withNoApprovedPayment_suspendsTheMembership() {
        when(onboardingPolicyService.suspendsOnRejectedPayment()).thenReturn(true);
        when(paymentRepository.findById(40L)).thenReturn(Optional.of(payment));
        when(paymentRepository.countApprovedByMemberId(5L)).thenReturn(0L);

        revoker.onPaymentRejected(new PaymentRejectedEvent(40L));

        assertThat(membership.getStatus()).isEqualTo(LifecycleStatus.SUSPENDED.name());
        assertThat(membership.getLastStatusChangeReason()).isEqualTo(ProvisionalActivationRevoker.REASON);
        verify(promotionLifecycle).onStatusChanged(membership, LifecycleStatus.SUSPENDED);
        verify(validatorCacheService).evictForMembership(membership);
    }

    @Test
    void aConfirmedMember_keepsTheMembershipActive() {
        member.setConfirmedAt(Instant.parse("2026-10-01T12:00:00Z"));
        when(onboardingPolicyService.suspendsOnRejectedPayment()).thenReturn(true);
        when(paymentRepository.findById(40L)).thenReturn(Optional.of(payment));

        revoker.onPaymentRejected(new PaymentRejectedEvent(40L));

        assertThat(membership.getStatus()).isEqualTo(LifecycleStatus.ACTIVE.name());
        verify(promotionLifecycle, never()).onStatusChanged(any(), any());
    }

    @Test
    void aMemberWithAnApprovedPayment_keepsTheMembershipActive() {
        when(onboardingPolicyService.suspendsOnRejectedPayment()).thenReturn(true);
        when(paymentRepository.findById(40L)).thenReturn(Optional.of(payment));
        when(paymentRepository.countApprovedByMemberId(5L)).thenReturn(1L);

        revoker.onPaymentRejected(new PaymentRejectedEvent(40L));

        assertThat(membership.getStatus()).isEqualTo(LifecycleStatus.ACTIVE.name());
    }
}
