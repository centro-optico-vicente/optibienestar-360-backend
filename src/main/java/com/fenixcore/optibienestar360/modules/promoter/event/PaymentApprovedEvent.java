package com.fenixcore.optibienestar360.modules.promoter.event;

/**
 * Published by {@code PaymentsService#applyApprovalEffects} the moment an
 * IN payment is approved (hub plan competitive-commission-rules, Fase 6
 * optional "eventos de dominio"). Consumed by {@code
 * CompetitiveRuleRealtimeEvaluationListener} to re-evaluate FIRST_TO_REACH
 * rules on {@code SALES_*}/{@code COLLECTION_*}/{@code ADVANCE_*}/{@code
 * COMMISSION_EARNED} the moment they might be crossed, instead of waiting
 * up to 15 minutes for {@code COMPETITIVE_COMMISSION_EVALUATION}. Purely a
 * latency improvement — D10 doesn't depend on this (the scheduled job keeps
 * everything correct on its own), so a listener failure never needs to roll
 * anything back.
 */
public record PaymentApprovedEvent(Long paymentId) {
}
