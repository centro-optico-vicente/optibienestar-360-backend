package com.fenixcore.optibienestar360.modules.membership.service;

import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership.LifecycleStatus;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipRepository;
import com.fenixcore.optibienestar360.modules.subsidy.service.SubsidyResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the date-driven evaluator + in-place transition, now
 * subsidy-aware (V41): a full monthly exoneration keeps the membership ACTIVE
 * past its due date, while a partial subsidy (resolver reports no full
 * exoneration) leaves the date logic untouched.
 */
@ExtendWith(MockitoExtension.class)
class MembershipStatusServiceTest {

    @Mock private MembershipRepository repository;
    @Mock private SubsidyResolver subsidyResolver;

    private MembershipStatusService service;

    @BeforeEach
    void setUp() {
        // Default: no subsidy. Declared first so a test's specific stub (declared
        // later in the method body) takes precedence over this catch-all.
        lenient().when(subsidyResolver.fullMonthlyExoneration(anyLong(), any())).thenReturn(false);
        service = new MembershipStatusService(repository, subsidyResolver);
    }

    private MembershipStatusService sut() {
        return service;
    }

    // ─── evaluate() — date driven ─────────────────────────────────────────────

    @Test
    void evaluate_returnsActive_whenTodayBeforeOrEqualNextDue() {
        Membership m = membership(LifecycleStatus.ACTIVE, LocalDate.of(2026, 6, 10), 7);

        assertThat(sut().evaluate(m, LocalDate.of(2026, 6, 9))).isEqualTo(LifecycleStatus.ACTIVE);
        assertThat(sut().evaluate(m, LocalDate.of(2026, 6, 10))).isEqualTo(LifecycleStatus.ACTIVE);
    }

    @Test
    void evaluate_returnsSuspended_whenWithinGraceWindow() {
        Membership m = membership(LifecycleStatus.ACTIVE, LocalDate.of(2026, 6, 10), 7);

        assertThat(sut().evaluate(m, LocalDate.of(2026, 6, 11))).isEqualTo(LifecycleStatus.SUSPENDED);
        assertThat(sut().evaluate(m, LocalDate.of(2026, 6, 17))).isEqualTo(LifecycleStatus.SUSPENDED);
    }

    @Test
    void evaluate_returnsExpired_whenPastGracePeriod() {
        Membership m = membership(LifecycleStatus.SUSPENDED, LocalDate.of(2026, 6, 10), 7);

        assertThat(sut().evaluate(m, LocalDate.of(2026, 6, 18))).isEqualTo(LifecycleStatus.EXPIRED);
        assertThat(sut().evaluate(m, LocalDate.of(2026, 12, 31))).isEqualTo(LifecycleStatus.EXPIRED);
    }

    @Test
    void evaluate_returnsNull_forCanceled() {
        Membership m = membership(LifecycleStatus.CANCELED, LocalDate.of(2026, 6, 10), 7);

        assertThat(sut().evaluate(m, LocalDate.of(2026, 12, 31))).isNull();
    }

    @Test
    void evaluate_returnsNull_forSoftDeleted() {
        Membership m = membership(LifecycleStatus.ACTIVE, LocalDate.of(2026, 6, 10), 7);
        m.setActive(false);

        assertThat(sut().evaluate(m, LocalDate.of(2026, 12, 31))).isNull();
    }

    @Test
    void evaluate_treatsZeroGrace_asImmediateExpiration() {
        Membership m = membership(LifecycleStatus.ACTIVE, LocalDate.of(2026, 6, 10), 0);

        assertThat(sut().evaluate(m, LocalDate.of(2026, 6, 11))).isEqualTo(LifecycleStatus.EXPIRED);
    }

    // ─── evaluate() — subsidy aware (V41 / vertical-5) ────────────────────────

    @Test
    void evaluate_fullMonthlySubsidy_keepsActive_pastGracePeriod() {
        Membership m = membership(LifecycleStatus.SUSPENDED, LocalDate.of(2026, 6, 10), 7);
        when(subsidyResolver.fullMonthlyExoneration(1L, LocalDate.of(2026, 12, 31))).thenReturn(true);

        // Way past grace, but the full exoneration short-circuits to ACTIVE.
        assertThat(sut().evaluate(m, LocalDate.of(2026, 12, 31))).isEqualTo(LifecycleStatus.ACTIVE);
    }

    @Test
    void evaluate_partialSubsidy_stillTransitions() {
        Membership m = membership(LifecycleStatus.ACTIVE, LocalDate.of(2026, 6, 10), 7);
        // Partial subsidy → resolver reports no FULL exoneration → date logic applies.
        when(subsidyResolver.fullMonthlyExoneration(1L, LocalDate.of(2026, 6, 18))).thenReturn(false);

        assertThat(sut().evaluate(m, LocalDate.of(2026, 6, 18))).isEqualTo(LifecycleStatus.EXPIRED);
    }

    // ─── applyTransition() ────────────────────────────────────────────────────

    @Test
    void applyTransition_writesStatus_andTimestampsReason_whenStateChanges() {
        Membership m = membership(LifecycleStatus.ACTIVE, LocalDate.of(2026, 6, 10), 7);

        boolean changed = sut().applyTransition(m, LocalDate.of(2026, 6, 11));

        assertThat(changed).isTrue();
        assertThat(m.getStatus()).isEqualTo(LifecycleStatus.SUSPENDED.name());
        assertThat(m.getLastStatusChangeAt()).isNotNull();
        assertThat(m.getLastStatusChangeReason()).isEqualTo("Past due date");
    }

    @Test
    void applyTransition_toActive_viaFullSubsidy_stampsSubsidyReason() {
        Membership m = membership(LifecycleStatus.SUSPENDED, LocalDate.of(2026, 6, 10), 7);
        when(subsidyResolver.fullMonthlyExoneration(1L, LocalDate.of(2026, 6, 20))).thenReturn(true);

        boolean changed = sut().applyTransition(m, LocalDate.of(2026, 6, 20));

        assertThat(changed).isTrue();
        assertThat(m.getStatus()).isEqualTo(LifecycleStatus.ACTIVE.name());
        assertThat(m.getLastStatusChangeReason()).isEqualTo("Active by full subsidy");
    }

    @Test
    void applyTransition_isNoop_whenAlreadyAtTargetStatus() {
        Membership m = membership(LifecycleStatus.SUSPENDED, LocalDate.of(2026, 6, 10), 7);

        boolean changed = sut().applyTransition(m, LocalDate.of(2026, 6, 12));

        assertThat(changed).isFalse();
        assertThat(m.getStatus()).isEqualTo(LifecycleStatus.SUSPENDED.name());
        assertThat(m.getLastStatusChangeAt()).isNull();
    }

    @Test
    void applyTransition_isNoop_forCanceledRow() {
        Membership m = membership(LifecycleStatus.CANCELED, LocalDate.of(2026, 6, 10), 7);

        boolean changed = sut().applyTransition(m, LocalDate.of(2026, 12, 31));

        assertThat(changed).isFalse();
        assertThat(m.getStatus()).isEqualTo(LifecycleStatus.CANCELED.name());
    }

    // ─── helper ─────────────────────────────────────────────────────────────

    private static Membership membership(LifecycleStatus status, LocalDate nextDueDate, int grace) {
        Member member = new Member();
        member.setId(1L);
        Membership m = new Membership();
        m.setMember(member);
        m.setActive(true);
        m.setStatus(status.name());
        m.setNextDueDate(nextDueDate);
        m.setGracePeriodDays(grace);
        return m;
    }
}
