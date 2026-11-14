package com.fenixcore.optibienestar360.modules.notification.service;

import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRoleRepository;
import com.fenixcore.optibienestar360.modules.notification.dto.NotificationEnqueueCommand;
import com.fenixcore.optibienestar360.modules.notification.dto.UserNotificationCommand;
import com.fenixcore.optibienestar360.modules.notification.entity.UserNotification;
import com.fenixcore.optibienestar360.modules.notification.entity.UserNotification.Severity;
import com.fenixcore.optibienestar360.modules.notification.repository.UserNotificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * <b>The single entry point for in-app notifications</b> (V259, hub ADR 0039).
 * Any module that wants to tell a user something inside the app builds a
 * {@link UserNotificationCommand} and calls this class; nothing else writes
 * {@code user_notifications}.
 *
 * <p><b>What it does</b></p>
 * <ol>
 *   <li><b>Resolves recipients</b> at publish time: explicit user ids, plus every
 *       active user holding one of the given role names or permission names
 *       (effective, non-expired role assignments). "Finance staff" or "ally
 *       managers" are therefore a role / permission name, not a list the caller
 *       has to maintain.</li>
 *   <li><b>Deduplicates</b>: with a {@code dedupKey}, a recipient that already has
 *       a row of the same type and key is skipped (backed by the
 *       {@code uq_user_notifications_dedup} unique index). Scheduled jobs can
 *       re-run and duplicate events are harmless.</li>
 *   <li><b>Writes one inbox row per recipient</b> (read state, link, params for
 *       the frontend i18n, fallback texts).</li>
 *   <li><b>Optionally enqueues an email</b> per new row through the existing
 *       {@link NotificationService} queue (V28) when the command carries an
 *       {@link UserNotificationCommand.EmailCopy}. The inbox and the email queue
 *       stay separate tables; this class is what keeps them in step.</li>
 * </ol>
 *
 * <p><b>Two ways to call it</b></p>
 * <ul>
 *   <li>{@link #publish} — joins the caller's transaction: the notice is
 *       committed (or rolled back) together with the business change. Invalid
 *       commands throw.</li>
 *   <li>{@link #publishAfterCommit} — waits until the caller's transaction
 *       commits, then writes in a transaction of its own, best effort: a failure
 *       is logged and never undoes or fails the business change. Use it from
 *       inside a service transaction or a synchronous event listener (this is
 *       what the registration approvals do).</li>
 * </ul>
 *
 * <p><b>Adding a new notification type</b></p>
 * <ol>
 *   <li>Pick a type code ({@code PAYMENT_APPROVED}) and a
 *       {@link UserNotification.Category}; no schema change is needed for the
 *       type.</li>
 *   <li>Add the frontend i18n keys {@code notifications.types.<TYPE>.*} and, if
 *       wanted, register the type in the frontend type registry (icon, toast).</li>
 *   <li>Resolve the fallback texts through {@code MessageSource} (rule 9 of the
 *       backend brief) and call this publisher from the source module, usually
 *       from an event listener next to the module's email notifier.</li>
 * </ol>
 * Planned users of this same base: approved / rejected payments, payment
 * receipts to finance staff (permission fan-out), commission receipts to
 * advisors, memberships about to fall due and birthdays (from
 * {@code scheduled_jobs} runners, idempotent through {@code dedupKey} and
 * short-lived through {@code expiresAt}), and "new ally confirmed" to ally
 * managers and advisors (role fan-out).
 */
@Service
@Slf4j
public class UserNotificationPublisher {

    private final UserNotificationRepository repository;
    private final UserRoleRepository userRoleRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final TransactionTemplate newTransaction;

    public UserNotificationPublisher(UserNotificationRepository repository,
                                     UserRoleRepository userRoleRepository,
                                     UserRepository userRepository,
                                     NotificationService notificationService,
                                     PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.userRoleRepository = userRoleRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Publishes the notice inside the caller's transaction (or a new one when
     * there is none). Returns how many inbox rows were created; recipients
     * skipped by dedup are not counted.
     *
     * @throws IllegalArgumentException when the command lacks a type, a
     *                                  category or a fallback title
     */
    @Transactional
    public int publish(UserNotificationCommand command) {
        validate(command);
        Instant now = Instant.now();
        Set<Long> recipients = resolveRecipients(command, now);
        if (recipients.isEmpty()) {
            log.debug("User notification {} has no recipients", command.type());
            return 0;
        }

        List<UserNotification> created = new ArrayList<>();
        for (Long userId : recipients) {
            if (command.dedupKey() != null
                    && repository.existsByRecipientUserIdAndTypeAndDedupKey(userId, command.type(), command.dedupKey())) {
                log.debug("Skipping duplicate user notification {} ({}) for user {}",
                        command.type(), command.dedupKey(), userId);
                continue;
            }
            created.add(repository.save(toEntity(command, userId)));
        }

        if (command.email() != null && !created.isEmpty()) {
            enqueueEmails(command, created);
        }
        log.debug("User notification {} published to {} of {} recipient(s)",
                command.type(), created.size(), recipients.size());
        return created.size();
    }

    /**
     * Publishes the notice once the current transaction commits, in a new
     * transaction; runs right away when no transaction is active. Never throws:
     * a failure is logged, the caller's business change is already committed.
     */
    public void publishAfterCommit(UserNotificationCommand command) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishInNewTransaction(command);
                }
            });
        } else {
            publishInNewTransaction(command);
        }
    }

    private void publishInNewTransaction(UserNotificationCommand command) {
        try {
            newTransaction.executeWithoutResult(status -> publish(command));
        } catch (RuntimeException ex) {
            log.error("Failed to publish user notification {} ({})",
                    command == null ? null : command.type(),
                    command == null ? null : command.dedupKey(), ex);
        }
    }

    // ─── Internals ────────────────────────────────────────────────────────────

    private static void validate(UserNotificationCommand command) {
        if (command == null || isBlank(command.type()) || command.category() == null
                || isBlank(command.fallbackTitle())) {
            throw new IllegalArgumentException("user_notification.command.invalid");
        }
    }

    /** Explicit ids first, then role and permission fan-out; insertion order kept, duplicates collapsed. */
    private Set<Long> resolveRecipients(UserNotificationCommand command, Instant now) {
        Set<Long> recipients = new LinkedHashSet<>();
        if (command.recipientUserIds() != null) {
            command.recipientUserIds().stream().filter(Objects::nonNull).forEach(recipients::add);
        }
        Collection<String> roles = names(command.recipientRoles());
        if (!roles.isEmpty()) {
            recipients.addAll(userRoleRepository.findEffectiveUserIdsByRoleNames(roles, now));
        }
        Collection<String> permissions = names(command.recipientPermissions());
        if (!permissions.isEmpty()) {
            recipients.addAll(userRoleRepository.findEffectiveUserIdsByPermissionNames(permissions, now));
        }
        return recipients;
    }

    private static UserNotification toEntity(UserNotificationCommand command, Long userId) {
        UserNotification n = new UserNotification();
        n.setRecipientUserId(userId);
        n.setType(command.type());
        n.setCategory(command.category());
        n.setSeverity(command.severity() != null ? command.severity() : Severity.INFO);
        if (command.params() != null) {
            n.setParams(new HashMap<>(command.params()));
        }
        n.setFallbackTitle(command.fallbackTitle());
        n.setFallbackBody(command.fallbackBody());
        n.setActionUrl(command.actionUrl());
        n.setSourceModule(command.sourceModule());
        n.setSourceEntityUuid(command.sourceEntityUuid());
        n.setDedupKey(command.dedupKey());
        n.setExpiresAt(command.expiresAt());
        return n;
    }

    /** One V28 queue row per newly created inbox row whose user has an email. */
    private void enqueueEmails(UserNotificationCommand command, List<UserNotification> created) {
        UserNotificationCommand.EmailCopy email = command.email();
        List<Long> ids = created.stream().map(UserNotification::getRecipientUserId).toList();
        for (User user : userRepository.findAllById(ids)) {
            if (isBlank(user.getEmail())) {
                continue;
            }
            String locale = user.getPerson() != null ? user.getPerson().getLocale() : null;
            notificationService.enqueue(new NotificationEnqueueCommand(
                    user.getEmail(), user.getId(), locale, email.templateCode(), email.subject(),
                    email.templateVars(), command.sourceModule(), command.sourceEntityUuid(),
                    null, false));
        }
    }

    private static Collection<String> names(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream().filter(v -> !isBlank(v)).map(String::trim).distinct().toList();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
