package com.fenixcore.optibienestar360.modules.notification.service;

import com.fenixcore.optibienestar360.common.service.EmailService;
import com.fenixcore.optibienestar360.modules.notification.dto.NotificationDispatchResult;
import com.fenixcore.optibienestar360.modules.notification.dto.NotificationEnqueueCommand;
import com.fenixcore.optibienestar360.modules.notification.entity.Notification;
import com.fenixcore.optibienestar360.modules.notification.entity.Notification.NotificationStatus;
import com.fenixcore.optibienestar360.modules.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.mail.MailSendException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NotificationService} (V28 queue): enqueue idempotency,
 * and the dispatch outcomes — SENT on success, FAILED with backoff when
 * retries remain, DEAD_LETTER when exhausted — plus the backoff curve.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository repository;
    @Mock private EmailService emailService;

    private NotificationService sut() {
        return new NotificationService(repository, emailService);
    }

    // ─── enqueue ────────────────────────────────────────────────────────────

    @Test
    void enqueue_persistsPendingRow() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<Notification> saved = sut().enqueue(cmd(false));

        assertThat(saved).isPresent();
        Notification n = saved.get();
        assertThat(n.getStatus()).isEqualTo(NotificationStatus.PENDING.name());
        assertThat(n.getRecipientEmail()).isEqualTo("a@b.com");
        assertThat(n.getTemplateCode()).isEqualTo("welcome");
    }

    @Test
    void enqueue_idempotent_skipsWhenAlreadyQueued() {
        when(repository.existsBySourceModuleAndSourceEntityUuidAndTemplateCode(
                eq("member"), any(), eq("welcome"))).thenReturn(true);

        assertThat(sut().enqueue(cmd(true))).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test
    void enqueue_idempotent_persistsWhenAbsent() {
        when(repository.existsBySourceModuleAndSourceEntityUuidAndTemplateCode(
                eq("member"), any(), eq("welcome"))).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(sut().enqueue(cmd(true))).isPresent();
        verify(repository).save(any());
    }

    // ─── dispatch ───────────────────────────────────────────────────────────

    @Test
    void dispatchDue_sendsSuccessfully_marksSent() {
        Notification n = pending(0, 5);
        when(repository.findDueForDispatch(any(), any(Pageable.class))).thenReturn(List.of(n));
        doNothing().when(emailService).sendTemplatedSync(anyString(), anyString(), anyString(), any(), any());

        NotificationDispatchResult result = sut().dispatchDue(50);

        assertThat(result.sent()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(n.getStatus()).isEqualTo(NotificationStatus.SENT.name());
        assertThat(n.getSentAt()).isNotNull();
        assertThat(n.getAttemptCount()).isEqualTo(1);
        assertThat(n.getLastErrorMessage()).isNull();
    }

    @Test
    void dispatchDue_failureWithRetriesLeft_marksFailedWithBackoff() {
        Notification n = pending(0, 5);
        when(repository.findDueForDispatch(any(), any(Pageable.class))).thenReturn(List.of(n));
        doThrow(new MailSendException("smtp down"))
                .when(emailService).sendTemplatedSync(anyString(), anyString(), anyString(), any(), any());

        NotificationDispatchResult result = sut().dispatchDue(50);

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.deadLettered()).isZero();
        assertThat(n.getStatus()).isEqualTo(NotificationStatus.FAILED.name());
        assertThat(n.getAttemptCount()).isEqualTo(1);
        assertThat(n.getNextRetryAt()).isNotNull();
        assertThat(n.getLastErrorMessage()).contains("smtp down");
    }

    @Test
    void dispatchDue_failureOnLastAttempt_deadLetters() {
        Notification n = pending(4, 5);   // one attempt left
        when(repository.findDueForDispatch(any(), any(Pageable.class))).thenReturn(List.of(n));
        doThrow(new MailSendException("still down"))
                .when(emailService).sendTemplatedSync(anyString(), anyString(), anyString(), any(), any());

        NotificationDispatchResult result = sut().dispatchDue(50);

        assertThat(result.deadLettered()).isEqualTo(1);
        assertThat(n.getStatus()).isEqualTo(NotificationStatus.DEAD_LETTER.name());
        assertThat(n.getAttemptCount()).isEqualTo(5);
        assertThat(n.getNextRetryAt()).isNull();
        assertThat(n.getLastErrorMessage()).isNotNull();
    }

    // ─── backoff ──────────────────────────────────────────────────────────────

    @Test
    void backoff_isExponential_clampedToOneHour() {
        assertThat(NotificationService.backoff(1)).isEqualTo(Duration.ofSeconds(60));
        assertThat(NotificationService.backoff(2)).isEqualTo(Duration.ofSeconds(120));
        assertThat(NotificationService.backoff(3)).isEqualTo(Duration.ofSeconds(240));
        assertThat(NotificationService.backoff(10)).isEqualTo(Duration.ofHours(1));   // clamped
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private NotificationEnqueueCommand cmd(boolean idempotent) {
        return new NotificationEnqueueCommand("a@b.com", null, "es", "welcome", "Welcome",
                Map.of("fullName", "Ana"), "member", UUID.randomUUID(), null, idempotent);
    }

    private Notification pending(int attemptCount, int maxAttempts) {
        Notification n = new Notification();
        n.setUuid(UUID.randomUUID());
        n.setRecipientEmail("a@b.com");
        n.setRecipientLocale("es");
        n.setTemplateCode("payment-reminder");
        n.setSubject("Reminder");
        n.setStatus(NotificationStatus.PENDING.name());
        n.setAttemptCount(attemptCount);
        n.setMaxAttempts(maxAttempts);
        return n;
    }
}
