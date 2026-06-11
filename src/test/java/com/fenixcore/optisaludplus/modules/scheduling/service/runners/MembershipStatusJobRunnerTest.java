package com.fenixcore.optisaludplus.modules.scheduling.service.runners;

import com.fenixcore.optisaludplus.common.service.EmailService;
import com.fenixcore.optisaludplus.modules.member.entity.Member;
import com.fenixcore.optisaludplus.modules.membership.entity.Membership;
import com.fenixcore.optisaludplus.modules.membership.entity.Membership.LifecycleStatus;
import com.fenixcore.optisaludplus.modules.membership.entity.Plan;
import com.fenixcore.optisaludplus.modules.membership.repository.MembershipRepository;
import com.fenixcore.optisaludplus.modules.membership.service.MembershipStatusService;
import com.fenixcore.optisaludplus.modules.person.entity.Person;
import com.fenixcore.optisaludplus.modules.scheduling.entity.ScheduledJob;
import com.fenixcore.optisaludplus.modules.scheduling.repository.ScheduledJobRepository;
import com.fenixcore.optisaludplus.modules.scheduling.service.JobRunResult;
import com.fenixcore.optisaludplus.modules.validator.service.ValidatorCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.context.support.StaticMessageSource;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the first scheduled-jobs runner. Mocks the dependencies
 * and verifies the per-transition email firing + summary shape. Schema
 * concerns and the actual cron-driven dispatch are covered by integration
 * tests in CI against real Postgres.
 */
@ExtendWith(MockitoExtension.class)
class MembershipStatusJobRunnerTest {

    @Mock private ScheduledJobRepository jobRepository;
    @Mock private MembershipRepository membershipRepository;
    @Mock private MembershipStatusService statusService;
    @Mock private EmailService emailService;
    @Mock private ValidatorCacheService validatorCacheService;

    private MessageSource messageSource;

    private MembershipStatusJobRunner runner;

    @BeforeEach
    void setUp() {
        StaticMessageSource ms = new StaticMessageSource();
        ms.addMessage("email.membership.suspended.subject", Locale.forLanguageTag("es"), "Suspendida");
        ms.addMessage("email.membership.suspended.subject", Locale.forLanguageTag("en"), "Suspended");
        ms.addMessage("email.membership.expired.subject",   Locale.forLanguageTag("es"), "Expirada");
        ms.addMessage("email.membership.expired.subject",   Locale.forLanguageTag("en"), "Expired");
        messageSource = ms;

        runner = new MembershipStatusJobRunner(
                jobRepository, membershipRepository, statusService,
                emailService, messageSource, validatorCacheService);
    }

    @Test
    void code_returnsConstant() {
        assertThat(runner.code()).isEqualTo("MEMBERSHIP_STATUS_SWEEP");
    }

    @Test
    void run_evaluates_transitions_and_emails_each_changed_membership() {
        ScheduledJob jobRow = new ScheduledJob();
        jobRow.setTimezone("America/Caracas");
        when(jobRepository.findByCode("MEMBERSHIP_STATUS_SWEEP")).thenReturn(Optional.of(jobRow));

        Membership willSuspend = membershipFixture(LifecycleStatus.ACTIVE, "ana@example.com", "es");
        Membership willExpire  = membershipFixture(LifecycleStatus.SUSPENDED, "bob@example.com", "en");
        Membership alreadyAtTarget = membershipFixture(LifecycleStatus.SUSPENDED, "carol@example.com", "es");

        when(membershipRepository.findStatusEvaluationCandidates(any()))
                .thenReturn(List.of(willSuspend, willExpire, alreadyAtTarget));

        when(statusService.evaluate(eq(willSuspend), any())).thenReturn(LifecycleStatus.SUSPENDED);
        when(statusService.evaluate(eq(willExpire),  any())).thenReturn(LifecycleStatus.EXPIRED);
        when(statusService.evaluate(eq(alreadyAtTarget), any())).thenReturn(LifecycleStatus.SUSPENDED);

        JobRunResult result = runner.run();

        assertThat(result.success()).isTrue();
        assertThat(result.summary()).containsEntry("scanned", 3);
        assertThat(result.summary()).containsEntry("suspended", 1);
        assertThat(result.summary()).containsEntry("expired", 1);
        assertThat(result.summary()).containsEntry("skipped", 1);
        assertThat(result.summary()).doesNotContainKey("notificationFailures");

        // Only the changed memberships got transitioned + emailed
        verify(statusService).applyTransition(eq(willSuspend), any());
        verify(statusService).applyTransition(eq(willExpire), any());
        verify(statusService, never()).applyTransition(eq(alreadyAtTarget), any());

        verify(emailService).sendTemplated(
                eq("ana@example.com"), eq("Suspendida"), eq("membership-suspended"),
                eq(Locale.forLanguageTag("es")), anyMap());
        verify(emailService).sendTemplated(
                eq("bob@example.com"), eq("Expired"), eq("membership-expired"),
                eq(Locale.forLanguageTag("en")), anyMap());
        verify(emailService, times(2)).sendTemplated(any(), any(), any(), any(), any());
    }

    @Test
    void run_skips_canceled_via_null_evaluate() {
        when(jobRepository.findByCode(any())).thenReturn(Optional.empty()); // falls back to default zone
        Membership canceled = membershipFixture(LifecycleStatus.CANCELED, "x@example.com", "es");
        when(membershipRepository.findStatusEvaluationCandidates(any()))
                .thenReturn(List.of(canceled));
        when(statusService.evaluate(eq(canceled), any())).thenReturn(null);

        JobRunResult result = runner.run();

        assertThat(result.success()).isTrue();
        assertThat(result.summary()).containsEntry("scanned", 1);
        assertThat(result.summary()).containsEntry("suspended", 0);
        assertThat(result.summary()).containsEntry("expired", 0);
        assertThat(result.summary()).containsEntry("skipped", 1);

        verify(statusService, never()).applyTransition(any(), any());
        verify(emailService, never()).sendTemplated(any(), any(), any(), any(), any());
    }

    @Test
    void run_captures_email_failures_without_aborting_sweep() {
        when(jobRepository.findByCode(any())).thenReturn(Optional.empty());
        Membership a = membershipFixture(LifecycleStatus.ACTIVE, "a@example.com", "es");
        Membership b = membershipFixture(LifecycleStatus.ACTIVE, "b@example.com", "es");
        when(membershipRepository.findStatusEvaluationCandidates(any()))
                .thenReturn(List.of(a, b));
        when(statusService.evaluate(any(), any())).thenReturn(LifecycleStatus.SUSPENDED);

        // First email throws; second succeeds. Runner must still report
        // both transitions and capture the failure detail in summary.
        org.mockito.Mockito.doThrow(new RuntimeException("smtp down"))
                .when(emailService).sendTemplated(eq("a@example.com"),
                        any(), any(), any(), any());

        JobRunResult result = runner.run();

        assertThat(result.success()).isTrue();
        assertThat(result.summary()).containsEntry("suspended", 2);
        @SuppressWarnings("unchecked")
        Map<String, String> failures = (Map<String, String>) result.summary().get("notificationFailures");
        assertThat(failures).hasSize(1);
        assertThat(failures.values().iterator().next()).contains("smtp down");

        verify(statusService, times(2)).applyTransition(any(), any());
        verify(emailService, times(2)).sendTemplated(any(), any(), any(), any(), any());
    }

    @Test
    void run_skips_email_when_member_has_no_address() {
        when(jobRepository.findByCode(any())).thenReturn(Optional.empty());
        Membership noEmail = membershipFixture(LifecycleStatus.ACTIVE, null, "es");
        when(membershipRepository.findStatusEvaluationCandidates(any())).thenReturn(List.of(noEmail));
        when(statusService.evaluate(any(), any())).thenReturn(LifecycleStatus.SUSPENDED);

        JobRunResult result = runner.run();

        assertThat(result.success()).isTrue();
        assertThat(result.summary()).containsEntry("suspended", 1);
        assertThat(result.summary()).doesNotContainKey("notificationFailures");
        verify(emailService, never()).sendTemplated(any(), any(), any(), any(), any());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private static Membership membershipFixture(LifecycleStatus currentStatus, String email, String localeTag) {
        Person person = new Person();
        person.setUuid(UUID.randomUUID());
        person.setFirstName("Test");
        person.setLastName("Person");
        person.setFullName("Test Person");
        person.setEmail(email);
        person.setLocale(localeTag);

        Member member = new Member();
        member.setUuid(UUID.randomUUID());
        member.setPerson(person);

        Plan plan = new Plan();
        plan.setUuid(UUID.randomUUID());
        plan.setCode("INDIVIDUAL");
        plan.setName("Plan Individual");

        Membership membership = new Membership();
        membership.setUuid(UUID.randomUUID());
        membership.setMember(member);
        membership.setPlan(plan);
        membership.setStatus(currentStatus.name());
        membership.setNextDueDate(LocalDate.of(2026, 1, 1));
        membership.setGracePeriodDays(7);
        return membership;
    }
}
