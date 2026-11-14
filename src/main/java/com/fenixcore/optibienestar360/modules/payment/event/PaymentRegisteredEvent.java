package com.fenixcore.optibienestar360.modules.payment.event;

/**
 * Published by {@code PaymentsService} when an IN collection is registered
 * for review (a non-draft registration, or a draft submitted to PENDING),
 * inside that transaction. Consumed after commit by
 * {@code PromotionRedemptionListener} to confirm a campaign slot waiting for
 * {@code ON_PAYMENT_REGISTERED} (hub ADR 0038 §5).
 */
public record PaymentRegisteredEvent(Long paymentId) {
}
