package com.fenixcore.optibienestar360.modules.membership.service;

import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.member.repository.BeneficiaryRepository;
import com.fenixcore.optibienestar360.modules.membership.dto.MembershipTermUpdateRequest;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership.LifecycleStatus;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan;
import com.fenixcore.optibienestar360.modules.membership.entity.TermMode;
import com.fenixcore.optibienestar360.modules.membership.mapper.MembershipMapper;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipRepository;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import com.fenixcore.optibienestar360.modules.promotion.service.PromotionLifecycle;
import com.fenixcore.optibienestar360.modules.subsidy.service.SubsidyResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Term snapshot at enrollment, daily renewal, status hold and clearing on an inscription payment
 * (hub ADR 0034).
 */
@ExtendWith(MockitoExtension.class)
class MembershipTermRenewalServiceTest {

    @Mock private MembershipRepository repository;
    @Mock private BeneficiaryRepository beneficiaryRepository;
    @Mock private MembershipMapper mapper;
    @Mock private PromotionLifecycle promotionLifecycle;
    @Mock private SubsidyResolver subsidyResolver;

    private MembershipStatusService statusService;
    private MembershipTermRenewalService service;

    @BeforeEach
    void setUp() {
        lenient().when(subsidyResolver.fullMonthlyExoneration(anyLong(), any())).thenReturn(false);
        statusService = new MembershipStatusService(repository, subsidyResolver, promotionLifecycle);
        service = new MembershipTermRenewalService(repository, statusService, beneficiaryRepository, mapper, promotionLifecycle);
    }

    // ─── Snapshot at enrollment ─────────────────────────────────────────────

    @Test
    void snapshot_planWithoutReinscription_isOpenEnded() {
        Membership m = new Membership();

        MembershipTermRenewalService.snapshotTerm(m, plan(false, null), LocalDate.of(2026, 11, 1), null, null);

        assertThat(m.isReinscriptionRequired()).isFalse();
        assertThat(m.getExpiresAt()).isNull();
        assertThat(m.getTermStartedAt()).isNull();
        assertThat(m.getTermMode()).isNull();
    }

    @Test
    void snapshot_fixedDatePlan_computesNextOccurrenceAndCopiesTheRule() {
        Membership m = new Membership();

        MembershipTermRenewalService.snapshotTerm(m, plan(true, MembershipTermRule.fixedDate(12, 1)),
                LocalDate.of(2026, 12, 15), null, null);

        assertThat(m.isReinscriptionRequired()).isTrue();
        assertThat(m.getTermMode()).isEqualTo(TermMode.FIXED_DATE);
        assertThat(m.getTermFixedMonth()).isEqualTo(12);
        assertThat(m.getTermFixedDay()).isEqualTo(1);
        assertThat(m.getTermPeriodMonths()).isNull();
        assertThat(m.getTermStartedAt()).isEqualTo(LocalDate.of(2026, 12, 15));
        assertThat(m.getExpiresAt()).isEqualTo(LocalDate.of(2027, 12, 1));
    }

    @Test
    void snapshot_periodPlan_addsMonthsToEnrollment() {
        Membership m = new Membership();

        MembershipTermRenewalService.snapshotTerm(m, plan(true, MembershipTermRule.period(6)),
                LocalDate.of(2026, 11, 1), null, null);

        assertThat(m.getExpiresAt()).isEqualTo(LocalDate.of(2027, 5, 1));
        assertThat(m.getTermPeriodMonths()).isEqualTo(6);
    }

    @Test
    void snapshot_expiryOverrideWinsOverTheRule() {
        Membership m = new Membership();

        MembershipTermRenewalService.snapshotTerm(m, plan(true, MembershipTermRule.period(12)),
                LocalDate.of(2026, 11, 1), LocalDate.of(2027, 3, 1), null);

        assertThat(m.getExpiresAt()).isEqualTo(LocalDate.of(2027, 3, 1));
        assertThat(m.getTermPeriodMonths()).isEqualTo(12);
    }

    @Test
    void snapshot_expiryNotAfterEnrollment_isRejected() {
        assertThatThrownBy(() -> MembershipTermRenewalService.snapshotTerm(new Membership(),
                plan(true, MembershipTermRule.period(12)), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1), null))
                .hasMessage("membership.term.expires_before_enrolled");
    }

    @Test
    void snapshot_overrideFalse_onPlanWithRule_keepsMembershipOpenEnded() {
        Membership m = new Membership();

        MembershipTermRenewalService.snapshotTerm(m, plan(true, MembershipTermRule.period(12)),
                LocalDate.of(2026, 11, 1), null, false);

        assertThat(m.isReinscriptionRequired()).isFalse();
        assertThat(m.getExpiresAt()).isNull();
        assertThat(m.getTermMode()).isNull();
    }

    @Test
    void snapshot_overrideTrue_onPlanWithoutRule_requiresExplicitExpiry_thenRenewsEveryTwelveMonths() {
        Plan plain = plan(false, null);
        assertThatThrownBy(() -> MembershipTermRenewalService.snapshotTerm(new Membership(), plain,
                LocalDate.of(2026, 11, 1), null, true)).hasMessage("membership.term.expires_required");

        Membership m = new Membership();
        MembershipTermRenewalService.snapshotTerm(m, plain, LocalDate.of(2026, 11, 1), LocalDate.of(2027, 1, 15), true);

        assertThat(m.isReinscriptionRequired()).isTrue();
        assertThat(m.getTermMode()).isEqualTo(TermMode.PERIOD);
        assertThat(m.getTermPeriodMonths()).isEqualTo(12);
        assertThat(m.getExpiresAt()).isEqualTo(LocalDate.of(2027, 1, 15));
    }

    // ─── Renewal ────────────────────────────────────────────────────────────

    @Test
    void renew_fixedDate_startsNewTermAtOldExpiry_addsTwelveMonths_setsDueAndSuspends() {
        Membership m = termMembership(MembershipTermRule.fixedDate(12, 1), LocalDate.of(2026, 12, 1), LifecycleStatus.ACTIVE);
        when(repository.findTermRenewalCandidates(LocalDate.of(2026, 12, 1))).thenReturn(List.of(m));

        var result = service.renewDueTerms(LocalDate.of(2026, 12, 1));

        assertThat(result.renewed()).isEqualTo(1);
        assertThat(result.suspended()).isEqualTo(1);
        assertThat(m.getTermStartedAt()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(m.getExpiresAt()).isEqualTo(LocalDate.of(2027, 12, 1));
        assertThat(m.getInscriptionDueSince()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(m.getStatus()).isEqualTo("SUSPENDED");
        verify(promotionLifecycle).onStatusChanged(m, LifecycleStatus.SUSPENDED);
    }

    @Test
    void renew_period_addsItsMonths() {
        Membership m = termMembership(MembershipTermRule.period(3), LocalDate.of(2026, 11, 1), LifecycleStatus.ACTIVE);
        when(repository.findTermRenewalCandidates(LocalDate.of(2026, 11, 2))).thenReturn(List.of(m));

        service.renewDueTerms(LocalDate.of(2026, 11, 2));

        assertThat(m.getTermStartedAt()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(m.getExpiresAt()).isEqualTo(LocalDate.of(2027, 2, 1));
        assertThat(m.getInscriptionDueSince()).isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    void renew_isIdempotent_oncePastTheNewExpiryNothingIsDue() {
        Membership m = termMembership(MembershipTermRule.period(12), LocalDate.of(2026, 11, 1), LifecycleStatus.ACTIVE);
        when(repository.findTermRenewalCandidates(LocalDate.of(2026, 11, 1))).thenReturn(List.of(m));

        service.renewDueTerms(LocalDate.of(2026, 11, 1));
        LocalDate expiry = m.getExpiresAt();
        // Second run the same day: the repository query (expires_at <= today) no longer returns the row.
        when(repository.findTermRenewalCandidates(LocalDate.of(2026, 11, 1))).thenReturn(List.of());
        var second = service.renewDueTerms(LocalDate.of(2026, 11, 1));

        assertThat(second.renewed()).isZero();
        assertThat(m.getExpiresAt()).isEqualTo(expiry);
        assertThat(expiry).isEqualTo(LocalDate.of(2027, 11, 1));
    }

    @Test
    void renew_rollsForwardSeveralMissedTerms_keepingTheFirstDueDate() {
        Membership m = termMembership(MembershipTermRule.period(12), LocalDate.of(2025, 1, 1), LifecycleStatus.ACTIVE);
        when(repository.findTermRenewalCandidates(LocalDate.of(2026, 6, 1))).thenReturn(List.of(m));

        service.renewDueTerms(LocalDate.of(2026, 6, 1));

        assertThat(m.getTermStartedAt()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(m.getExpiresAt()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(m.getInscriptionDueSince()).isEqualTo(LocalDate.of(2025, 1, 1));
    }

    @Test
    void renew_ignoresMembershipsWithoutARule() {
        Membership open = new Membership();
        open.setExpiresAt(LocalDate.of(2026, 1, 1));
        open.setStatus("ACTIVE");
        when(repository.findTermRenewalCandidates(LocalDate.of(2026, 11, 1))).thenReturn(List.of(open));

        var result = service.renewDueTerms(LocalDate.of(2026, 11, 1));

        assertThat(result.renewed()).isZero();
        assertThat(open.getExpiresAt()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(open.getInscriptionDueSince()).isNull();
    }

    // ─── Status hold + clearing ─────────────────────────────────────────────

    @Test
    void sweep_keepsSuspended_whileInscriptionIsDue_evenWithPaidMonthlyFees() {
        Membership m = termMembership(MembershipTermRule.period(12), LocalDate.of(2027, 11, 1), LifecycleStatus.SUSPENDED);
        m.setInscriptionDueSince(LocalDate.of(2026, 11, 1));
        m.setNextDueDate(LocalDate.of(2027, 1, 1));

        assertThat(statusService.evaluate(m, LocalDate.of(2026, 12, 1))).isEqualTo(LifecycleStatus.SUSPENDED);
        assertThat(statusService.applyTransition(m, LocalDate.of(2026, 12, 1))).isFalse();
        assertThat(m.getStatus()).isEqualTo("SUSPENDED");
    }

    @Test
    void inscriptionPayment_clearsDueAndReevaluatesToActive() {
        Membership m = termMembership(MembershipTermRule.period(12), LocalDate.of(2027, 11, 1), LifecycleStatus.SUSPENDED);
        m.setInscriptionDueSince(LocalDate.of(2026, 11, 1));
        m.setNextDueDate(LocalDate.now().plusDays(10));
        Payment payment = inscriptionPayment(m, 5L);
        when(beneficiaryRepository.existsByInscriptionPaymentId(5L)).thenReturn(false);

        boolean cleared = service.onInscriptionPaymentApproved(payment);

        assertThat(cleared).isTrue();
        assertThat(m.getInscriptionDueSince()).isNull();
        assertThat(m.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void inscriptionPayment_ofAnExtraBeneficiary_doesNotClearDue() {
        Membership m = termMembership(MembershipTermRule.period(12), LocalDate.of(2027, 11, 1), LifecycleStatus.SUSPENDED);
        m.setInscriptionDueSince(LocalDate.of(2026, 11, 1));
        Payment payment = inscriptionPayment(m, 6L);
        when(beneficiaryRepository.existsByInscriptionPaymentId(6L)).thenReturn(true);

        assertThat(service.onInscriptionPaymentApproved(payment)).isFalse();
        assertThat(m.getInscriptionDueSince()).isNotNull();
    }

    @Test
    void inscriptionPayment_belowTheInscriptionFee_doesNotClearDue() {
        Membership m = termMembership(MembershipTermRule.period(12), LocalDate.of(2027, 11, 1), LifecycleStatus.SUSPENDED);
        m.setInscriptionDueSince(LocalDate.of(2026, 11, 1));
        m.setInscriptionFee(new BigDecimal("25.00"));
        Payment payment = inscriptionPayment(m, 9L);
        payment.setAmount(new BigDecimal("10.00"));
        when(beneficiaryRepository.existsByInscriptionPaymentId(9L)).thenReturn(false);

        assertThat(service.onInscriptionPaymentApproved(payment)).isFalse();
        assertThat(m.getInscriptionDueSince()).isNotNull();
        assertThat(m.getStatus()).isEqualTo("SUSPENDED");
    }

    @Test
    void inscriptionPayment_netOfDiscountBelowTheFee_doesNotClearDue() {
        Membership m = termMembership(MembershipTermRule.period(12), LocalDate.of(2027, 11, 1), LifecycleStatus.SUSPENDED);
        m.setInscriptionDueSince(LocalDate.of(2026, 11, 1));
        m.setInscriptionFee(new BigDecimal("25.00"));
        Payment payment = inscriptionPayment(m, 10L);
        payment.setAmount(new BigDecimal("25.00"));
        payment.setDiscountAmount(new BigDecimal("5.00"));
        when(beneficiaryRepository.existsByInscriptionPaymentId(10L)).thenReturn(false);

        assertThat(service.onInscriptionPaymentApproved(payment)).isFalse();
        assertThat(m.getInscriptionDueSince()).isNotNull();
    }

    @Test
    void inscriptionPayment_coveringTheFee_clearsDue() {
        Membership m = termMembership(MembershipTermRule.period(12), LocalDate.of(2027, 11, 1), LifecycleStatus.SUSPENDED);
        m.setInscriptionDueSince(LocalDate.of(2026, 11, 1));
        m.setNextDueDate(LocalDate.now().plusDays(10));
        m.setInscriptionFee(new BigDecimal("25.00"));
        Payment payment = inscriptionPayment(m, 11L);
        payment.setAmount(new BigDecimal("25.00"));
        when(beneficiaryRepository.existsByInscriptionPaymentId(11L)).thenReturn(false);

        assertThat(service.onInscriptionPaymentApproved(payment)).isTrue();
        assertThat(m.getInscriptionDueSince()).isNull();
    }

    @Test
    void nonInscriptionPayment_orNothingDue_isANoOp() {
        Membership m = termMembership(MembershipTermRule.period(12), LocalDate.of(2027, 11, 1), LifecycleStatus.ACTIVE);
        Payment monthly = inscriptionPayment(m, 7L);
        monthly.setInscription(false);
        assertThat(service.onInscriptionPaymentApproved(monthly)).isFalse();

        // Inscription payment but no pending re-inscription (the original enrollment inscription).
        assertThat(service.onInscriptionPaymentApproved(inscriptionPayment(m, 8L))).isFalse();
    }

    // ─── Admin edit ─────────────────────────────────────────────────────────

    @Test
    void updateTerm_changesExpiry_keepingTheRule() {
        Membership m = termMembership(MembershipTermRule.period(12), LocalDate.of(2027, 11, 1), LifecycleStatus.ACTIVE);
        when(repository.findByUuid(m.getUuid())).thenReturn(Optional.of(m));

        service.updateTerm(m.getUuid(), new MembershipTermUpdateRequest(null, LocalDate.of(2027, 6, 1)));

        assertThat(m.getExpiresAt()).isEqualTo(LocalDate.of(2027, 6, 1));
        assertThat(m.isReinscriptionRequired()).isTrue();
    }

    @Test
    void updateTerm_turningReinscriptionOff_clearsTheRuleAndPendingDue_andReevaluates() {
        Membership m = termMembership(MembershipTermRule.period(12), LocalDate.of(2027, 11, 1), LifecycleStatus.SUSPENDED);
        m.setInscriptionDueSince(LocalDate.of(2026, 11, 1));
        m.setNextDueDate(LocalDate.now().plusDays(10));
        when(repository.findByUuid(m.getUuid())).thenReturn(Optional.of(m));

        service.updateTerm(m.getUuid(), new MembershipTermUpdateRequest(false, null));

        assertThat(m.isReinscriptionRequired()).isFalse();
        assertThat(m.getExpiresAt()).isNull();
        assertThat(m.getInscriptionDueSince()).isNull();
        assertThat(m.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void updateTerm_turningReinscriptionOn_withoutRuleOrExpiry_isRejected() {
        Membership m = termMembership(null, null, LifecycleStatus.ACTIVE);
        m.setPlan(plan(false, null));
        when(repository.findByUuid(m.getUuid())).thenReturn(Optional.of(m));

        assertThatThrownBy(() -> service.updateTerm(m.getUuid(), new MembershipTermUpdateRequest(true, null)))
                .hasMessage("membership.term.expires_required");
    }

    @Test
    void updateTerm_expiryNotAfterTermStart_isRejected() {
        Membership m = termMembership(MembershipTermRule.period(12), LocalDate.of(2027, 11, 1), LifecycleStatus.ACTIVE);
        when(repository.findByUuid(m.getUuid())).thenReturn(Optional.of(m));

        assertThatThrownBy(() -> service.updateTerm(m.getUuid(),
                new MembershipTermUpdateRequest(null, m.getTermStartedAt())))
                .hasMessage("membership.term.expires_before_enrolled");
    }

    // ─── Fixtures ───────────────────────────────────────────────────────────

    private static Plan plan(boolean reinscription, MembershipTermRule rule) {
        Plan plan = new Plan();
        plan.setReinscriptionRequired(reinscription);
        if (rule != null) {
            plan.setTermMode(rule.mode());
            plan.setTermFixedMonth(rule.fixedMonth());
            plan.setTermFixedDay(rule.fixedDay());
            plan.setTermPeriodMonths(rule.periodMonths());
        }
        return plan;
    }

    /** A live membership with the rule snapshot and {@code expiresAt}; a null rule gives an open-ended one. */
    private static Membership termMembership(MembershipTermRule rule, LocalDate expiresAt, LifecycleStatus status) {
        Member member = new Member();
        member.setId(1L);
        Membership m = new Membership();
        m.setUuid(UUID.randomUUID());
        m.setMember(member);
        m.setActive(true);
        m.setStatus(status.name());
        m.setEnrolledAt(LocalDate.of(2025, 1, 1));
        m.setNextDueDate(LocalDate.of(2099, 1, 1));
        m.setGracePeriodDays(7);
        m.setExpiresAt(expiresAt);
        if (rule != null) {
            m.setReinscriptionRequired(true);
            m.setTermMode(rule.mode());
            m.setTermFixedMonth(rule.fixedMonth());
            m.setTermFixedDay(rule.fixedDay());
            m.setTermPeriodMonths(rule.periodMonths());
            m.setTermStartedAt(expiresAt == null ? null : rule.mode() == TermMode.PERIOD
                    ? expiresAt.minusMonths(rule.periodMonths()) : expiresAt.minusMonths(12));
        }
        return m;
    }

    private static Payment inscriptionPayment(Membership m, Long id) {
        Payment p = new Payment();
        p.setId(id);
        p.setMembership(m);
        p.setInscription(true);
        return p;
    }
}
