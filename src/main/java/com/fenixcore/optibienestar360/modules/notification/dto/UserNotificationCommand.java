package com.fenixcore.optibienestar360.modules.notification.dto;

import com.fenixcore.optibienestar360.modules.notification.entity.UserNotification.Category;
import com.fenixcore.optibienestar360.modules.notification.entity.UserNotification.Severity;
import lombok.Builder;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Internal command for {@code UserNotificationPublisher.publish} (no HTTP
 * surface). One command = one notice, fanned out to every resolved recipient.
 *
 * <p><b>Recipients</b> — the union of:</p>
 * <ul>
 *   <li>{@code recipientUserIds}: explicit {@code users.users_id} values;</li>
 *   <li>{@code recipientRoles}: role names (e.g. {@code ADMINISTRADOR}),
 *       resolved at publish time to the active users holding them;</li>
 *   <li>{@code recipientPermissions}: permission names (e.g.
 *       {@code PAYMENT_APPROVE}), resolved at publish time to the active users
 *       whose roles grant them.</li>
 * </ul>
 * A user reached by several sources gets a single row.
 *
 * <p><b>Content</b> — {@code type} is the open code the frontend keys its i18n
 * on ({@code notifications.types.<TYPE>.*}) and {@code params} the values it
 * interpolates; {@code fallbackTitle} (required) / {@code fallbackBody} are
 * shown by clients that do not know the type yet.</p>
 *
 * <p><b>Dedup</b> — with a {@code dedupKey}, a recipient that already has a row
 * of the same {@code type} and key is skipped (and gets no email copy either),
 * which makes re-runs of jobs and double events harmless.</p>
 *
 * <p>Nullable fields default to: {@code severity} → {@code INFO},
 * {@code params} → empty, no expiry, no email copy.</p>
 *
 * @param email optional copy through the V28 email queue, one per created row
 */
@Builder
public record UserNotificationCommand(
        String type,
        Category category,
        Severity severity,
        Map<String, Object> params,
        String fallbackTitle,
        String fallbackBody,
        String actionUrl,
        String sourceModule,
        UUID sourceEntityUuid,
        String dedupKey,
        Instant expiresAt,
        Set<Long> recipientUserIds,
        Set<String> recipientRoles,
        Set<String> recipientPermissions,
        EmailCopy email
) {

    /**
     * Email to enqueue in {@code NotificationService} for each recipient that
     * got a new inbox row. The recipient address and locale come from the user
     * (person locale, default {@code es}); the subject is the same for all.
     */
    public record EmailCopy(
            String templateCode,
            String subject,
            Map<String, Object> templateVars
    ) {}
}
