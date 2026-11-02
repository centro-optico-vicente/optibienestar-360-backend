package com.fenixcore.optibienestar360.modules.membership.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenixcore.optibienestar360.core.audit.AuditContextResolver;
import com.fenixcore.optibienestar360.core.audit.DataChangeAuditWriter;
import com.fenixcore.optibienestar360.core.audit.EntityConfigService;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.membership.dto.PlanTermApplyRequest;
import com.fenixcore.optibienestar360.modules.membership.dto.PlanTermApplyResultDto;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership.LifecycleStatus;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan;
import com.fenixcore.optibienestar360.modules.membership.entity.TermMode;
import com.fenixcore.optibienestar360.modules.membership.mapper.MembershipMapper;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipRepository;
import com.fenixcore.optibienestar360.modules.membership.repository.PlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Compliance of memberships with the plan's term rule and bulk application (hub ADR 0034). */
@ExtendWith(MockitoExtension.class)
class MembershipTermComplianceServiceTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Caracas"));

    @Mock private PlanRepository planRepository;
    @Mock private MembershipRepository membershipRepository;
    @Mock private MembershipMapper membershipMapper;
    @Mock private EntityConfigService entityConfigService;
    @Mock private AuditContextResolver auditContextResolver;
    @Mock private DataChangeAuditWriter dataChangeAuditWriter;

    private MembershipTermComplianceService service;
    private Plan plan;

    @BeforeEach
    void setUp() {
        service = new MembershipTermComplianceService(planRepository, membershipRepository, membershipMapper,
                entityConfigService, auditContextResolver, dataChangeAuditWriter, new ObjectMapper());
        plan = plan(true, MembershipTermRule.fixedDate(12, 1));
    }

    // ─── Compliance rule ────────────────────────────────────────────────────

    @Test
    void complies_sameRule_isTrue() {
        Membership m = membership(plan, MembershipTermRule.fixedDate(12, 1), LifecycleStatus.ACTIVE, TODAY.plusDays(30));

        assertThat(MembershipTermComplianceService.complies(m, plan)).isTrue();
    }

    @Test
    void complies_overriddenExpiryWithSameRule_stillComplies() {
        Membership m = membership(plan, MembershipTermRule.fixedDate(12, 1), LifecycleStatus.ACTIVE, TODAY.plusDays(400));

        assertThat(MembershipTermComplianceService.complies(m, plan)).isTrue();
    }

    @Test
    void complies_openEndedMembershipOfReinscriptionPlan_differs() {
        Membership m = membership(plan, null, LifecycleStatus.ACTIVE, null);

        assertThat(MembershipTermComplianceService.complies(m, plan)).isFalse();
    }

    @Test
    void complies_differentFixedDate_differs() {
        Membership m = membership(plan, MembershipTermRule.fixedDate(11, 1), LifecycleStatus.ACTIVE, TODAY.plusDays(30));

        assertThat(MembershipTermComplianceService.complies(m, plan)).isFalse();
    }

    @Test
    void complies_differentMode_differs() {
        Membership m = membership(plan, MembershipTermRule.period(12), LifecycleStatus.ACTIVE, TODAY.plusDays(30));

        assertThat(MembershipTermComplianceService.complies(m, plan)).isFalse();
    }

    @Test
    void complies_differentPeriodMonths_differs() {
        Plan periodPlan = plan(true, MembershipTermRule.period(12));
        Membership m = membership(periodPlan, MembershipTermRule.period(6), LifecycleStatus.ACTIVE, TODAY.plusDays(30));

        assertThat(MembershipTermComplianceService.complies(m, periodPlan)).isFalse();
    }

    @Test
    void complies_planWithoutReinscription_openEndedMembershipComplies() {
        Plan open = plan(false, null);
        Membership m = membership(open, null, LifecycleStatus.ACTIVE, null);

        assertThat(MembershipTermComplianceService.complies(m, open)).isTrue();
    }

    @Test
    void isLive_canceledOrInactiveAreExcluded() {
        Membership active = membership(plan, null, LifecycleStatus.ACTIVE, null);
        Membership suspended = membership(plan, null, LifecycleStatus.SUSPENDED, null);
        Membership expired = membership(plan, null, LifecycleStatus.EXPIRED, null);
        Membership canceled = membership(plan, null, LifecycleStatus.CANCELED, null);
        Membership inactive = membership(plan, null, LifecycleStatus.ACTIVE, null);
        inactive.setActive(false);

        assertThat(MembershipTermComplianceService.isLive(active)).isTrue();
        assertThat(MembershipTermComplianceService.isLive(suspended)).isTrue();
        assertThat(MembershipTermComplianceService.isLive(expired)).isTrue();
        assertThat(MembershipTermComplianceService.isLive(canceled)).isFalse();
        assertThat(MembershipTermComplianceService.isLive(inactive)).isFalse();
    }

    // ─── Apply ──────────────────────────────────────────────────────────────

    @Test
    void apply_selected_copiesRuleAndKeepsInscriptionDueSince() {
        when(planRepository.findByUuid(plan.getUuid())).thenReturn(Optional.of(plan));
        Membership m = membership(plan, null, LifecycleStatus.ACTIVE, null);
        m.setReinscriptionRequired(false);
        when(membershipRepository.findAll(anySpec())).thenReturn(List.of(m));
        LocalDate expiresAt = TODAY.plusMonths(3);

        PlanTermApplyResultDto result = service.apply(plan.getUuid(), new PlanTermApplyRequest(List.of(m.getUuid()), expiresAt));

        assertThat(result).isEqualTo(new PlanTermApplyResultDto(1, 0));
        assertThat(m.isReinscriptionRequired()).isTrue();
        assertThat(m.getTermMode()).isEqualTo(TermMode.FIXED_DATE);
        assertThat(m.getTermFixedMonth()).isEqualTo(12);
        assertThat(m.getTermFixedDay()).isEqualTo(1);
        assertThat(m.getTermPeriodMonths()).isNull();
        assertThat(m.getTermStartedAt()).isEqualTo(TODAY);
        assertThat(m.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(m.getInscriptionDueSince()).isNull();
        verify(membershipRepository).saveAll(List.of(m));
    }

    @Test
    void apply_leavesInscriptionDueSinceUntouched() {
        when(planRepository.findByUuid(plan.getUuid())).thenReturn(Optional.of(plan));
        Membership m = membership(plan, MembershipTermRule.period(12), LifecycleStatus.SUSPENDED, TODAY.plusDays(5));
        LocalDate dueSince = TODAY.minusDays(20);
        m.setInscriptionDueSince(dueSince);
        when(membershipRepository.findAll(anySpec())).thenReturn(List.of(m));

        service.apply(plan.getUuid(), new PlanTermApplyRequest(List.of(m.getUuid()), TODAY.plusMonths(6)));

        assertThat(m.getInscriptionDueSince()).isEqualTo(dueSince);
        assertThat(m.getTermMode()).isEqualTo(TermMode.FIXED_DATE);
        assertThat(m.getTermPeriodMonths()).isNull();
    }

    @Test
    void apply_selected_skipsCanceledForeignPlanAndUnknown() {
        when(planRepository.findByUuid(plan.getUuid())).thenReturn(Optional.of(plan));
        Membership ok = membership(plan, null, LifecycleStatus.ACTIVE, null);
        Membership canceled = membership(plan, null, LifecycleStatus.CANCELED, null);
        Plan other = plan(true, MembershipTermRule.period(12));
        other.setId(99L);
        Membership foreign = membership(other, null, LifecycleStatus.ACTIVE, null);
        UUID unknown = UUID.randomUUID();
        when(membershipRepository.findAll(anySpec())).thenReturn(List.of(ok, canceled, foreign));

        PlanTermApplyResultDto result = service.apply(plan.getUuid(), new PlanTermApplyRequest(
                List.of(ok.getUuid(), canceled.getUuid(), foreign.getUuid(), unknown), TODAY.plusMonths(2)));

        assertThat(result).isEqualTo(new PlanTermApplyResultDto(1, 3));
        assertThat(ok.getExpiresAt()).isEqualTo(TODAY.plusMonths(2));
        assertThat(canceled.getExpiresAt()).isNull();
        assertThat(canceled.isReinscriptionRequired()).isFalse();
        assertThat(foreign.getTermMode()).isNull();
    }

    @Test
    void apply_noUuids_appliesToAllThatDiffer() {
        when(planRepository.findByUuid(plan.getUuid())).thenReturn(Optional.of(plan));
        Membership a = membership(plan, null, LifecycleStatus.ACTIVE, null);
        Membership b = membership(plan, MembershipTermRule.period(6), LifecycleStatus.EXPIRED, TODAY.plusDays(9));
        when(membershipRepository.findAll(anySpec())).thenReturn(List.of(a, b));

        PlanTermApplyResultDto emptyList = service.apply(plan.getUuid(), new PlanTermApplyRequest(List.of(), TODAY.plusMonths(1)));
        PlanTermApplyResultDto nullList = service.apply(plan.getUuid(), new PlanTermApplyRequest(null, TODAY.plusMonths(1)));

        assertThat(emptyList).isEqualTo(new PlanTermApplyResultDto(2, 0));
        assertThat(nullList).isEqualTo(new PlanTermApplyResultDto(2, 0));
        assertThat(b.getTermMode()).isEqualTo(TermMode.FIXED_DATE);
        assertThat(b.getTermFixedMonth()).isEqualTo(12);
    }

    @Test
    void apply_planWithoutReinscription_isRejected() {
        Plan open = plan(false, null);
        when(planRepository.findByUuid(open.getUuid())).thenReturn(Optional.of(open));

        assertThatThrownBy(() -> service.apply(open.getUuid(), new PlanTermApplyRequest(null, TODAY.plusMonths(1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("membership.term.apply_plan_not_reinscription");
        verify(membershipRepository, never()).saveAll(any());
    }

    @Test
    void apply_expiryNotAfterToday_isRejected() {
        when(planRepository.findByUuid(plan.getUuid())).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> service.apply(plan.getUuid(), new PlanTermApplyRequest(null, TODAY)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("membership.term.apply_expires_not_future");
        assertThatThrownBy(() -> service.apply(plan.getUuid(), new PlanTermApplyRequest(null, TODAY.minusDays(1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("membership.term.apply_expires_not_future");
        verify(membershipRepository, never()).saveAll(any());
    }

    // ─── Fixtures ───────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static Specification<Membership> anySpec() {
        return any(Specification.class);
    }

    private static Plan plan(boolean reinscription, MembershipTermRule rule) {
        Plan p = new Plan();
        p.setId(1L);
        p.setUuid(UUID.randomUUID());
        p.setReinscriptionRequired(reinscription);
        if (rule != null) {
            p.setTermMode(rule.mode());
            p.setTermFixedMonth(rule.fixedMonth());
            p.setTermFixedDay(rule.fixedDay());
            p.setTermPeriodMonths(rule.periodMonths());
        }
        return p;
    }

    /** A live membership of {@code plan} with the given snapshot rule (null = open-ended). */
    private static Membership membership(Plan plan, MembershipTermRule rule, LifecycleStatus status, LocalDate expiresAt) {
        Member member = new Member();
        member.setId(1L);
        Membership m = new Membership();
        m.setUuid(UUID.randomUUID());
        m.setMember(member);
        m.setPlan(plan);
        m.setActive(true);
        m.setStatus(status.name());
        m.setEnrolledAt(LocalDate.of(2025, 1, 1));
        m.setExpiresAt(expiresAt);
        if (rule != null) {
            m.setReinscriptionRequired(true);
            m.setTermMode(rule.mode());
            m.setTermFixedMonth(rule.fixedMonth());
            m.setTermFixedDay(rule.fixedDay());
            m.setTermPeriodMonths(rule.periodMonths());
            m.setTermStartedAt(LocalDate.of(2025, 1, 1));
        }
        return m;
    }
}
