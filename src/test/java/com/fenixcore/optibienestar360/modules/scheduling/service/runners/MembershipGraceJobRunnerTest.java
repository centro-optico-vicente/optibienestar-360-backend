package com.fenixcore.optibienestar360.modules.scheduling.service.runners;

import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipRepository;
import com.fenixcore.optibienestar360.modules.notification.service.NotificationChannelResolver.RecipientType;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.scheduling.entity.ScheduledJob;
import com.fenixcore.optibienestar360.modules.scheduling.repository.ScheduledJobRepository;
import com.fenixcore.optibienestar360.modules.scheduling.service.JobRunResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MembershipGraceJobRunner} — the per-membership
 * "nudge day" filter ({@code nextDueDate + max(1, grace - 3)}) that fires the
 * payment-overdue notice only a few days before grace expiry.
 */
@ExtendWith(MockitoExtension.class)
class MembershipGraceJobRunnerTest {

    @Mock private ScheduledJobRepository jobRepository;
    @Mock private MembershipRepository membershipRepository;
    @Mock private MembershipReminderEnqueuer enqueuer;

    private MembershipGraceJobRunner sut() {
        return new MembershipGraceJobRunner(jobRepository, membershipRepository, enqueuer);
    }

    private static final ZoneId CARACAS = ZoneId.of("America/Caracas");

    @Test
    void run_notifiesOnlyMembershipsWhoseNudgeDayIsToday() {
        LocalDate today = LocalDate.now(CARACAS);
        // grace 7 → offset max(1, 7-3)=4 → nudge day = nextDueDate + 4
        Membership onNudgeDay = suspended(today.minusDays(4), 7);   // nudge day == today
        Membership notYet = suspended(today.minusDays(1), 7);       // nudge day == today+3

        when(jobRepository.findByCode(MembershipGraceJobRunner.CODE)).thenReturn(Optional.empty());
        when(membershipRepository.findByActiveTrueAndStatus("SUSPENDED"))
                .thenReturn(List.of(onNudgeDay, notYet));
        when(enqueuer.enqueue(eq(onNudgeDay), anyString(), anyString())).thenReturn(true);

        JobRunResult result = sut().run();

        assertThat(result.success()).isTrue();
        assertThat(result.summary()).containsEntry("notified", 1);
        verify(enqueuer).enqueue(eq(onNudgeDay), eq("payment-overdue"), anyString());
        verify(enqueuer, never()).enqueue(eq(notYet), any(), any());
    }

    @Test
    void run_usesParametersDrivenDaysBeforeGraceEnd_insteadOfHardcodedDefault() {
        LocalDate today = LocalDate.now(CARACAS);
        // grace 7, daysBeforeGraceEnd=1 (from parameters) → offset max(1, 7-1)=6 → nudge day = nextDueDate + 6
        Membership onNudgeDay = suspended(today.minusDays(6), 7);

        ScheduledJob job = new ScheduledJob();
        job.setCode(MembershipGraceJobRunner.CODE);
        job.setParameters(Map.of("daysBeforeGraceEnd", 1, "daysBeforeAdvisorNotify", 1));
        when(jobRepository.findByCode(MembershipGraceJobRunner.CODE)).thenReturn(Optional.of(job));
        when(membershipRepository.findByActiveTrueAndStatus("SUSPENDED")).thenReturn(List.of(onNudgeDay));
        when(enqueuer.enqueue(eq(onNudgeDay), anyString(), anyString())).thenReturn(true);

        JobRunResult result = sut().run();

        assertThat(result.summary()).containsEntry("notified", 1);
        verify(enqueuer).enqueue(eq(onNudgeDay), eq("payment-overdue"), anyString());
    }

    @Test
    void run_notifiesPromoter_onAdvisorNotifyDay() {
        LocalDate today = LocalDate.now(CARACAS);
        // grace 7, default daysBeforeAdvisorNotify=3 → offset 4 → notify day = nextDueDate + 4
        Membership onNotifyDay = suspended(today.minusDays(4), 7);
        Promoter promoter = new Promoter();
        promoter.setEmail("promotor@x.com");
        Member member = new Member();
        member.setPromoter(promoter);
        onNotifyDay.setMember(member);

        when(jobRepository.findByCode(MembershipGraceJobRunner.CODE)).thenReturn(Optional.empty());
        when(membershipRepository.findByActiveTrueAndStatus("SUSPENDED")).thenReturn(List.of(onNotifyDay));

        sut().run();

        verify(enqueuer).enqueueToRecipient(eq("promotor@x.com"), any(), eq(onNotifyDay),
                eq("collection-reminder-promoter"), anyString(), eq(RecipientType.PROMOTER));
    }

    private Membership suspended(LocalDate nextDueDate, int gracePeriodDays) {
        Membership m = new Membership();
        m.setUuid(UUID.randomUUID());
        m.setStatus(Membership.LifecycleStatus.SUSPENDED.name());
        m.setNextDueDate(nextDueDate);
        m.setGracePeriodDays(gracePeriodDays);
        return m;
    }
}
