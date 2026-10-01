package com.fenixcore.optibienestar360.modules.promoter.event;

/**
 * Published by {@code MembersService#create} the moment a new {@link
 * com.fenixcore.optibienestar360.modules.member.entity.Member} is persisted
 * (hub plan competitive-commission-rules, Fase 6 optional). Consumed by
 * {@code CompetitiveRuleRealtimeEvaluationListener} to re-evaluate
 * FIRST_TO_REACH rules on {@code NEW_SUBSCRIBERS} immediately, instead of
 * waiting for the scheduled job. See {@link PaymentApprovedEvent}'s Javadoc
 * for why this is purely a latency improvement.
 */
public record MembershipEnrolledEvent(Long memberId) {
}
