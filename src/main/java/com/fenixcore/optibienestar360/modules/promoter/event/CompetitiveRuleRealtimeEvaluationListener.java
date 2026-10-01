package com.fenixcore.optibienestar360.modules.promoter.event;

import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.CompetitionType;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.CompetitiveMetric;
import com.fenixcore.optibienestar360.modules.promoter.repository.CompetitiveCommissionRuleRepository;
import com.fenixcore.optibienestar360.modules.promoter.service.CompetitiveCommissionEvaluationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Fase 6 (hub plan competitive-commission-rules) optional "eventos de
 * dominio": re-evaluates FIRST_TO_REACH rules the moment their metric might
 * have just been crossed, instead of waiting up to 15 minutes for {@code
 * COMPETITIVE_COMMISSION_EVALUATION}. RANKING rules are untouched here —
 * they only ever settle at period close (D7), so a mid-period nudge buys
 * nothing.
 *
 * <p>{@code AFTER_COMMIT}: runs only once the triggering transaction (a
 * payment approval, a new member, a settled charge) has actually committed —
 * evaluating against data that might still roll back would be wrong. Every
 * rule is evaluated independently and a failure is logged, never
 * propagated: this is a latency improvement only (D10 doesn't depend on
 * it), so it must never turn into a reason the triggering action fails.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CompetitiveRuleRealtimeEvaluationListener {

    private static final Set<CompetitiveMetric> PAYMENT_METRICS = EnumSet.of(
            CompetitiveMetric.SALES_COUNT, CompetitiveMetric.SALES_AMOUNT,
            CompetitiveMetric.COLLECTION_COUNT, CompetitiveMetric.COLLECTION_AMOUNT,
            CompetitiveMetric.ADVANCE_COUNT, CompetitiveMetric.ADVANCE_AMOUNT,
            CompetitiveMetric.COMMISSION_EARNED);

    private static final Set<CompetitiveMetric> ENROLLMENT_METRICS = EnumSet.of(CompetitiveMetric.NEW_SUBSCRIBERS);

    private static final Set<CompetitiveMetric> CHARGE_COVERED_METRICS = EnumSet.of(
            CompetitiveMetric.OVERDUE_SETTLED_COUNT, CompetitiveMetric.OVERDUE_SETTLED_AMOUNT);

    private final CompetitiveCommissionRuleRepository ruleRepository;
    private final CompetitiveCommissionEvaluationService evaluationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentApproved(PaymentApprovedEvent event) {
        evaluateMatchingRules(PAYMENT_METRICS, "payment " + event.paymentId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMembershipEnrolled(MembershipEnrolledEvent event) {
        evaluateMatchingRules(ENROLLMENT_METRICS, "member " + event.memberId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMembershipChargeCovered(MembershipChargeCoveredEvent event) {
        evaluateMatchingRules(CHARGE_COVERED_METRICS, "membership charge " + event.membershipChargeId());
    }

    private void evaluateMatchingRules(Set<CompetitiveMetric> metrics, String trigger) {
        List<CompetitiveCommissionRule> rules = ruleRepository.findByActiveTrue().stream()
                .filter(r -> r.getCompetitionType() == CompetitionType.FIRST_TO_REACH)
                .filter(r -> metrics.contains(r.getMetric()))
                .toList();
        for (CompetitiveCommissionRule rule : rules) {
            try {
                evaluationService.evaluateRule(rule.getUuid(), LocalDate.now(), false);
            } catch (RuntimeException ex) {
                log.warn("Real-time FIRST_TO_REACH evaluation failed for rule {} (triggered by {}): {}",
                        rule.getUuid(), trigger, ex.getMessage(), ex);
            }
        }
    }
}
