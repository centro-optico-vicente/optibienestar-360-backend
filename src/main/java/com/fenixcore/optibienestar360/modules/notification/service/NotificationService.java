package com.fenixcore.optibienestar360.modules.notification.service;

import com.fenixcore.optibienestar360.common.service.EmailService;
import com.fenixcore.optibienestar360.modules.notification.dto.NotificationDispatchResult;
import com.fenixcore.optibienestar360.modules.notification.dto.NotificationEnqueueCommand;
import com.fenixcore.optibienestar360.modules.notification.entity.Notification;
import com.fenixcore.optibienestar360.modules.notification.entity.Notification.NotificationStatus;
import com.fenixcore.optibienestar360.modules.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The persistent notification queue (V28). Two responsibilities:
 *
 * <ul>
 *   <li><b>enqueue</b> — other services / job runners push a
 *       {@link NotificationEnqueueCommand}; a row lands in {@code PENDING}
 *       (optionally deduped by source).</li>
 *   <li><b>dispatchDue</b> — the {@code NotificationDispatchJobRunner} calls
 *       this each worker pass; it claims the due batch, renders + sends each
 *       row synchronously via {@link EmailService#sendTemplatedSync}, and
 *       records the outcome (SENT / FAILED-with-backoff / DEAD_LETTER).</li>
 * </ul>
 *
 * <p>Retry policy: exponential backoff {@code 60s × 2^(attempt-1)} clamped to
 * one hour, up to the row's {@code maxAttempts}; the last attempt that still
 * fails moves the row to {@code DEAD_LETTER} for admin triage. Each row's
 * send is wrapped so one bad recipient never aborts the batch.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private static final Duration BASE_BACKOFF = Duration.ofSeconds(60);
    private static final Duration MAX_BACKOFF = Duration.ofHours(1);
    private static final int MAX_ERROR_LENGTH = 2000;

    private final NotificationRepository repository;
    private final EmailService emailService;

    // ─── Enqueue ──────────────────────────────────────────────────────────────

    /**
     * Enqueues one notification. Returns the persisted row, or empty when an
     * idempotent enqueue found an existing (module, entity, template) row.
     */
    @Transactional
    public Optional<Notification> enqueue(NotificationEnqueueCommand cmd) {
        if (cmd.idempotent() && cmd.sourceModule() != null && cmd.sourceEntityUuid() != null
                && repository.existsBySourceModuleAndSourceEntityUuidAndTemplateCode(
                        cmd.sourceModule(), cmd.sourceEntityUuid(), cmd.templateCode())) {
            log.debug("Skipping duplicate notification {} for {}/{}",
                    cmd.templateCode(), cmd.sourceModule(), cmd.sourceEntityUuid());
            return Optional.empty();
        }

        Notification n = new Notification();
        n.setRecipientEmail(cmd.recipientEmail());
        n.setRecipientUserId(cmd.recipientUserId());
        if (cmd.recipientLocale() != null && !cmd.recipientLocale().isBlank()) {
            n.setRecipientLocale(cmd.recipientLocale());
        }
        n.setTemplateCode(cmd.templateCode());
        n.setSubject(cmd.subject());
        if (cmd.templateVars() != null) {
            n.setTemplateVars(new HashMap<>(cmd.templateVars()));
        }
        n.setSourceModule(cmd.sourceModule());
        n.setSourceEntityUuid(cmd.sourceEntityUuid());
        if (cmd.maxAttempts() != null && cmd.maxAttempts() >= 1) {
            n.setMaxAttempts(cmd.maxAttempts());
        }
        n.setStatus(NotificationStatus.PENDING.name());
        n.setScheduledFor(Instant.now());
        return Optional.of(repository.save(n));
    }

    // ─── Dispatch (worker) ─────────────────────────────────────────────────────

    /**
     * Sends the next due batch. Runs in one transaction so every row's final
     * state flushes on commit; per-row failures are caught so a single bad
     * recipient does not roll back or abort the batch.
     */
    @Transactional
    public NotificationDispatchResult dispatchDue(int batchSize) {
        List<Notification> due = repository.findDueForDispatch(Instant.now(), PageRequest.of(0, batchSize));
        int sent = 0;
        int failed = 0;
        int deadLettered = 0;
        for (Notification n : due) {
            switch (attemptSend(n)) {
                case SENT -> sent++;
                case FAILED -> failed++;
                case DEAD_LETTER -> deadLettered++;
                default -> { /* attemptSend only returns the three terminal outcomes */ }
            }
        }
        if (!due.isEmpty()) {
            log.info("Notification dispatch: processed={} sent={} failed={} deadLettered={}",
                    due.size(), sent, failed, deadLettered);
        }
        return new NotificationDispatchResult(due.size(), sent, failed, deadLettered);
    }

    private NotificationStatus attemptSend(Notification n) {
        n.setAttemptCount(n.getAttemptCount() + 1);
        n.setLastAttemptAt(Instant.now());
        try {
            emailService.sendTemplatedSync(n.getRecipientEmail(), n.getSubject(),
                    n.getTemplateCode(), localeOf(n.getRecipientLocale()), n.getTemplateVars());
            n.setStatus(NotificationStatus.SENT.name());
            n.setSentAt(Instant.now());
            n.setNextRetryAt(null);
            n.setLastErrorMessage(null);
            return NotificationStatus.SENT;
        } catch (RuntimeException ex) {
            String message = ex.getMessage();
            n.setLastErrorMessage(truncate(message != null ? message : ex.getClass().getSimpleName()));
            if (n.getAttemptCount() >= n.getMaxAttempts()) {
                n.setStatus(NotificationStatus.DEAD_LETTER.name());
                n.setNextRetryAt(null);
                log.warn("Notification {} dead-lettered after {} attempts: {}",
                        n.getUuid(), n.getAttemptCount(), n.getLastErrorMessage());
                return NotificationStatus.DEAD_LETTER;
            }
            n.setStatus(NotificationStatus.FAILED.name());
            n.setNextRetryAt(Instant.now().plus(backoff(n.getAttemptCount())));
            return NotificationStatus.FAILED;
        }
    }

    /** Exponential backoff: {@code 60s × 2^(attempt-1)}, clamped to one hour. */
    static Duration backoff(int attemptCount) {
        int exponent = Math.max(0, attemptCount - 1);
        double seconds = BASE_BACKOFF.toSeconds() * Math.pow(2, exponent);
        if (seconds >= MAX_BACKOFF.toSeconds()) {
            return MAX_BACKOFF;
        }
        return Duration.ofSeconds((long) seconds);
    }

    private static Locale localeOf(String tag) {
        if (tag == null || tag.isBlank()) return Locale.forLanguageTag("es");
        try {
            return Locale.forLanguageTag(tag);
        } catch (RuntimeException ex) {
            return Locale.forLanguageTag("es");
        }
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= MAX_ERROR_LENGTH ? s : s.substring(0, MAX_ERROR_LENGTH);
    }
}
