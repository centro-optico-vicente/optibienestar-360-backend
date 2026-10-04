package com.fenixcore.optibienestar360.modules.promotion.event;

import com.fenixcore.optibienestar360.modules.promoter.event.PaymentApprovedEvent;
import com.fenixcore.optibienestar360.modules.promotion.service.ReferrerRewardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Grants the referrer reward after a payment approval commits; best-effort, never fails the approval. */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReferrerRewardListener {

    private final ReferrerRewardService referrerRewardService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentApproved(PaymentApprovedEvent event) {
        try {
            referrerRewardService.grantFor(event.paymentId());
        } catch (RuntimeException ex) {
            log.error("Referrer reward failed for payment {}", event.paymentId(), ex);
        }
    }
}
