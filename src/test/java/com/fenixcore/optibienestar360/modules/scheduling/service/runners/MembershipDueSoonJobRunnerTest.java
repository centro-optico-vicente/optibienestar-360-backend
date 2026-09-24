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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MembershipDueSoonJobRunner} — the parameters-driven
 * {@code daysBeforeDue} (falls back to 3 with no row/key) and the new
 * promoter-notification branch (V153).
 */
@ExtendWith(MockitoExtension.class)
class MembershipDueSoonJobRunnerTest {

    @Mock private ScheduledJobRepository jobRepository;
    @Mock private MembershipRepository membershipRepository;
    @Mock private MembershipReminderEnqueuer enqueuer;

    private static final ZoneId CARACAS = ZoneId.of("America/Caracas");

    private MembershipDueSoonJobRunner sut() {
        return new MembershipDueSoonJobRunner(jobRepository, membershipRepository, enqueuer);
    }

    @Test
    void run_fallsBackToDefaultDaysBeforeDue_whenJobRowMissing() {
        LocalDate today = LocalDate.now(CARACAS);
        LocalDate dueDate = today.plusDays(3);
        when(jobRepository.findByCode(MembershipDueSoonJobRunner.CODE)).thenReturn(Optional.empty());
        when(membershipRepository.findByActiveTrueAndStatusAndNextDueDate("ACTIVE", dueDate))
                .thenReturn(List.of());

        JobRunResult result = sut().run();

        assertThat(result.success()).isTrue();
        assertThat(result.summary()).containsEntry("dueDate", dueDate.toString());
    }

    @Test
    void run_usesParametersDrivenDaysBeforeDue() {
        LocalDate today = LocalDate.now(CARACAS);
        LocalDate dueDate = today.plusDays(5);
        ScheduledJob job = new ScheduledJob();
        job.setCode(MembershipDueSoonJobRunner.CODE);
        job.setTimezone("America/Caracas");
        job.setParameters(Map.of("daysBeforeDue", 5));
        when(jobRepository.findByCode(MembershipDueSoonJobRunner.CODE)).thenReturn(Optional.of(job));
        when(membershipRepository.findByActiveTrueAndStatusAndNextDueDate("ACTIVE", dueDate))
                .thenReturn(List.of());

        JobRunResult result = sut().run();

        assertThat(result.summary()).containsEntry("dueDate", dueDate.toString());
    }

    @Test
    void run_notifiesPromoter_whenMemberHasOne() {
        LocalDate today = LocalDate.now(CARACAS);
        LocalDate dueDate = today.plusDays(3);
        when(jobRepository.findByCode(MembershipDueSoonJobRunner.CODE)).thenReturn(Optional.empty());

        Membership membership = new Membership();
        membership.setUuid(UUID.randomUUID());
        Promoter promoter = new Promoter();
        promoter.setEmail("promotor@x.com");
        Member member = new Member();
        member.setPromoter(promoter);
        membership.setMember(member);

        when(membershipRepository.findByActiveTrueAndStatusAndNextDueDate("ACTIVE", dueDate))
                .thenReturn(List.of(membership));
        when(enqueuer.enqueue(eq(membership), anyString(), anyString())).thenReturn(true);

        sut().run();

        verify(enqueuer).enqueueToRecipient(eq("promotor@x.com"), any(), eq(membership),
                eq("collection-reminder-promoter"), anyString(), eq(RecipientType.PROMOTER));
    }
}
