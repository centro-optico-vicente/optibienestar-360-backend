package com.fenixcore.optibienestar360.modules.payment.event;

/**
 * Published by {@code PaymentsService#reject} when an IN payment is rejected,
 * inside the review transaction. Consumed by
 * {@code ProvisionalActivationRevoker} to take back a membership activated on
 * a reported payment (hub ADR 0037 §1, {@code ON_PAYMENT_REPORTED}).
 */
public record PaymentRejectedEvent(Long paymentId) {
}
