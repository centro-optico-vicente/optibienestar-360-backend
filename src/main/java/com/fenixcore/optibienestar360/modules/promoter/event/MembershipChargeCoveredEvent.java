package com.fenixcore.optibienestar360.modules.promoter.event;

/**
 * Published by {@code MembershipChargeService#applyPayment} the moment a
 * {@code MembershipCharge} first transitions to {@code COVERED} (hub plan
 * competitive-commission-rules, Fase 6 optional). Consumed by {@code
 * CompetitiveRuleRealtimeEvaluationListener} to re-evaluate FIRST_TO_REACH
 * rules on {@code OVERDUE_SETTLED_COUNT}/{@code OVERDUE_SETTLED_AMOUNT}
 * immediately, instead of waiting for the scheduled job. See {@link
 * PaymentApprovedEvent}'s Javadoc for why this is purely a latency
 * improvement.
 */
public record MembershipChargeCoveredEvent(Long membershipChargeId) {
}
