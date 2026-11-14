package com.fenixcore.optibienestar360.modules.promotion.event;

import com.fenixcore.optibienestar360.modules.payment.event.PaymentRegisteredEvent;
import com.fenixcore.optibienestar360.modules.payment.event.PaymentRejectedEvent;
import com.fenixcore.optibienestar360.modules.promoter.event.PaymentApprovedEvent;
import com.fenixcore.optibienestar360.modules.promotion.service.PromotionRedemptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Confirms PENDING campaign slots when the payment their campaign waits for
 * is registered or approved (hub ADR 0038 §5), and lets the deadline a
 * rejected payment froze run again — expiring the slot at once when it
 * already passed (V260, §9). Runs after the payment commits in its own
 * transaction; best-effort, it never fails the payment.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PromotionRedemptionListener {

    private final PromotionRedemptionService redemptionService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentRegistered(PaymentRegisteredEvent event) {
        try {
            redemptionService.onPaymentRegistered(event.paymentId());
        } catch (RuntimeException ex) {
            log.error("Promotion slot confirmation (registered) failed for payment {}", event.paymentId(), ex);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentApproved(PaymentApprovedEvent event) {
        try {
            redemptionService.onPaymentApproved(event.paymentId());
        } catch (RuntimeException ex) {
            log.error("Promotion slot confirmation (approved) failed for payment {}", event.paymentId(), ex);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentRejected(PaymentRejectedEvent event) {
        try {
            redemptionService.onPaymentRejected(event.paymentId());
        } catch (RuntimeException ex) {
            log.error("Promotion slot deadline (rejected) failed for payment {}", event.paymentId(), ex);
        }
    }
}
