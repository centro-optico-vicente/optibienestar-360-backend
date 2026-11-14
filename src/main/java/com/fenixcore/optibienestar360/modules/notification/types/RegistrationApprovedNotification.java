package com.fenixcore.optibienestar360.modules.notification.types;

import com.fenixcore.optibienestar360.modules.notification.dto.UserNotificationCommand;
import com.fenixcore.optibienestar360.modules.notification.entity.UserNotification.Category;
import com.fenixcore.optibienestar360.modules.notification.entity.UserNotification.Severity;
import com.fenixcore.optibienestar360.modules.notification.service.UserNotificationPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Type {@code REGISTRATION_APPROVED} (first in-app notification type, hub ADR
 * 0039): staff approved the user's own registration — an ally self-registration
 * (hub ADR 0036) or an affiliation request. Category {@code ACCOUNT}, severity
 * {@code SUCCESS}.
 *
 * <ul>
 *   <li>{@code params}: {@code {"kind": "ALLY"|"MEMBER", "name": <ally name / member full name>}}</li>
 *   <li>{@code actionUrl}: {@code /allies} or {@code /members} (the portal home)</li>
 *   <li>{@code dedupKey}: {@code registration-approved:<entityUuid>} — approving
 *       the same registration twice creates no second notice</li>
 *   <li>fallback texts: {@code user_notification.registration_approved.*} in
 *       the applicant's locale ({@code es} by default)</li>
 * </ul>
 *
 * The approval emails are unchanged; this only adds the inbox notice.
 */
@Component
@RequiredArgsConstructor
public class RegistrationApprovedNotification {

    public static final String TYPE = "REGISTRATION_APPROVED";
    static final String DEDUP_PREFIX = "registration-approved:";

    /** What was approved; travels as {@code params.kind}. */
    public enum Kind {
        ALLY("/allies", "ally"),
        MEMBER("/members", "member");

        private final String actionUrl;
        private final String messageSuffix;

        Kind(String actionUrl, String messageSuffix) {
            this.actionUrl = actionUrl;
            this.messageSuffix = messageSuffix;
        }

        public String actionUrl() {
            return actionUrl;
        }
    }

    private final UserNotificationPublisher publisher;
    private final MessageSource messageSource;

    /**
     * Publishes the notice to {@code recipientUserIds} once the current
     * transaction commits (best effort, see
     * {@link UserNotificationPublisher#publishAfterCommit}). No-op without
     * recipients.
     */
    public void publishAfterCommit(Kind kind, UUID entityUuid, String name, Collection<Long> recipientUserIds,
                                   String sourceModule, String locale) {
        UserNotificationCommand command = command(kind, entityUuid, name, recipientUserIds, sourceModule, locale);
        if (command.recipientUserIds().isEmpty()) {
            return;
        }
        publisher.publishAfterCommit(command);
    }

    UserNotificationCommand command(Kind kind, UUID entityUuid, String name, Collection<Long> recipientUserIds,
                                    String sourceModule, String locale) {
        Locale loc = Locale.forLanguageTag(locale == null || locale.isBlank() ? "es" : locale);
        String safeName = name == null ? "" : name.trim();
        Set<Long> recipients = new LinkedHashSet<>();
        if (recipientUserIds != null) {
            recipientUserIds.stream().filter(Objects::nonNull).forEach(recipients::add);
        }
        return UserNotificationCommand.builder()
                .type(TYPE)
                .category(Category.ACCOUNT)
                .severity(Severity.SUCCESS)
                .params(Map.of("kind", kind.name(), "name", safeName))
                .fallbackTitle(messageSource.getMessage("user_notification.registration_approved.title",
                        null, "¡Ya eres parte de OptiBienestar!", loc))
                .fallbackBody(messageSource.getMessage(
                        "user_notification.registration_approved.body." + kind.messageSuffix,
                        new Object[]{safeName}, "Tu solicitud fue aprobada.", loc))
                .actionUrl(kind.actionUrl())
                .sourceModule(sourceModule)
                .sourceEntityUuid(entityUuid)
                .dedupKey(DEDUP_PREFIX + entityUuid)
                .recipientUserIds(recipients)
                .build();
    }
}
