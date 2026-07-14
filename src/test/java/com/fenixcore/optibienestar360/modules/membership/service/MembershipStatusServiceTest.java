package com.fenixcore.optibienestar360.modules.membership.service;

import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership.LifecycleStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the pure-function evaluator and the in-place transition.
 * The batch query path is exercised by the full integration suite via
 * {@code contextLoads} once the daily job lands.
 */
class MembershipStatusServiceTest {

    private final MembershipStatusService service = new MembershipStatusService(null);

    // ─── evaluate() ─────────────────────────────────────────────────────────

    @Test
    void evaluate_returnsActive_whenTodayBeforeOrEqualNextDue() {
        Membership m = membership(LifecycleStatus.ACTIVE, LocalDate.of(2026, 6, 10), 7);

        assertThat(service.evaluate(m, LocalDate.of(2026, 6, 9))).isEqualTo(LifecycleStatus.ACTIVE);
        assertThat(service.evaluate(m, LocalDate.of(2026, 6, 10))).isEqualTo(LifecycleStatus.ACTIVE);
    }

    @Test
    void evaluate_returnsSuspended_whenWithinGraceWindow() {
        Membership m = membership(LifecycleStatus.ACTIVE, LocalDate.of(2026, 6, 10), 7);

        // Day after due → suspended (still inside grace)
        assertThat(service.evaluate(m, LocalDate.of(2026, 6, 11))).isEqualTo(LifecycleStatus.SUSPENDED);
        // Last day of grace (due + 7) → still suspended
        assertThat(service.evaluate(m, LocalDate.of(2026, 6, 17))).isEqualTo(LifecycleStatus.SUSPENDED);
    }

    @Test
    void evaluate_returnsExpired_whenPastGracePeriod() {
        Membership m = membership(LifecycleStatus.SUSPENDED, LocalDate.of(2026, 6, 10), 7);

        // Day after grace cutoff → expired
        assertThat(service.evaluate(m, LocalDate.of(2026, 6, 18))).isEqualTo(LifecycleStatus.EXPIRED);
        assertThat(service.evaluate(m, LocalDate.of(2026, 12, 31))).isEqualTo(LifecycleStatus.EXPIRED);
    }

    @Test
    void evaluate_returnsNull_forCanceled() {
        Membership m = membership(LifecycleStatus.CANCELED, LocalDate.of(2026, 6, 10), 7);

        assertThat(service.evaluate(m, LocalDate.of(2026, 12, 31))).isNull();
    }

    @Test
    void evaluate_returnsNull_forSoftDeleted() {
        Membership m = membership(LifecycleStatus.ACTIVE, LocalDate.of(2026, 6, 10), 7);
        m.setActive(false);

        assertThat(service.evaluate(m, LocalDate.of(2026, 12, 31))).isNull();
    }

    @Test
    void evaluate_treatsZeroGrace_asImmediateExpiration() {
        Membership m = membership(LifecycleStatus.ACTIVE, LocalDate.of(2026, 6, 10), 0);

        // Day after due with no grace → directly expired
        assertThat(service.evaluate(m, LocalDate.of(2026, 6, 11))).isEqualTo(LifecycleStatus.EXPIRED);
    }

    // ─── applyTransition() ──────────────────────────────────────────────────

    @Test
    void applyTransition_writesStatus_andTimestampsReason_whenStateChanges() {
        Membership m = membership(LifecycleStatus.ACTIVE, LocalDate.of(2026, 6, 10), 7);

        boolean changed = service.applyTransition(m, LocalDate.of(2026, 6, 11));

        assertThat(changed).isTrue();
        assertThat(m.getStatus()).isEqualTo(LifecycleStatus.SUSPENDED.name());
        assertThat(m.getLastStatusChangeAt()).isNotNull();
        assertThat(m.getLastStatusChangeReason()).isEqualTo("Past due date");
    }

    @Test
    void applyTransition_isNoop_whenAlreadyAtTargetStatus() {
        Membership m = membership(LifecycleStatus.SUSPENDED, LocalDate.of(2026, 6, 10), 7);

        boolean changed = service.applyTransition(m, LocalDate.of(2026, 6, 12));

        assertThat(changed).isFalse();
        assertThat(m.getStatus()).isEqualTo(LifecycleStatus.SUSPENDED.name());
        // Did not stamp a fresh transition record
        assertThat(m.getLastStatusChangeAt()).isNull();
    }

    @Test
    void applyTransition_isNoop_forCanceledRow() {
        Membership m = membership(LifecycleStatus.CANCELED, LocalDate.of(2026, 6, 10), 7);

        boolean changed = service.applyTransition(m, LocalDate.of(2026, 12, 31));

        assertThat(changed).isFalse();
        assertThat(m.getStatus()).isEqualTo(LifecycleStatus.CANCELED.name());
    }

    // ─── helper ─────────────────────────────────────────────────────────────

    private static Membership membership(LifecycleStatus status, LocalDate nextDueDate, int grace) {
        Membership m = new Membership();
        m.setActive(true);
        m.setStatus(status.name());
        m.setNextDueDate(nextDueDate);
        m.setGracePeriodDays(grace);
        return m;
    }
}
