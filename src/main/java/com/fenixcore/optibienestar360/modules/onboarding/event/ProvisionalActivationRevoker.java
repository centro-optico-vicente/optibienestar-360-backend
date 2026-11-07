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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Takes back a provisional activation (hub ADR 0037 §1): with
 * {@code member_activation_mode = ON_PAYMENT_REPORTED} a membership starts
 * ACTIVE on the strength of a reported payment; if that payment is rejected
 * and the member has never had a payment approved, the membership goes back
 * to SUSPENDED until a valid one is approved.
 *
 * <p>Runs synchronously inside the rejection transaction. The policy is read
 * at rejection time, so switching the mode later only affects rejections from
 * then on.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProvisionalActivationRevoker {

    static final String REASON = "Payment rejected before the first approved payment";

    private final OnboardingPolicyService onboardingPolicyService;
    private final PaymentRepository paymentRepository;
    private final PromotionLifecycle promotionLifecycle;
    private final ValidatorCacheService validatorCacheService;

    @EventListener
    public void onPaymentRejected(PaymentRejectedEvent event) {
        if (!onboardingPolicyService.suspendsOnRejectedPayment()) {
            return;
        }
        Payment payment = paymentRepository.findById(event.paymentId()).orElse(null);
        if (payment == null || payment.getMembership() == null) {
            return;
        }
        Membership membership = payment.getMembership();
        Member member = membership.getMember();
        if (!LifecycleStatus.ACTIVE.name().equals(membership.getStatus())
                || member.getConfirmedAt() != null
                || paymentRepository.countApprovedByMemberId(member.getId()) > 0) {
            return;
        }
        membership.setStatus(LifecycleStatus.SUSPENDED.name());
        membership.setLastStatusChangeAt(Instant.now());
        membership.setLastStatusChangeReason(REASON);
        promotionLifecycle.onStatusChanged(membership, LifecycleStatus.SUSPENDED);
        validatorCacheService.evictForMembership(membership);
        log.info("Membership {} re-suspended: payment {} rejected before any approved payment",
                membership.getUuid(), payment.getUuid());
    }
}
