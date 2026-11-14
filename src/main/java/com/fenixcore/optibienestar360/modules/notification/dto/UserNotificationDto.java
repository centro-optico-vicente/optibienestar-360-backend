package com.fenixcore.optibienestar360.modules.notification.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.modules.notification.entity.UserNotification.Category;
import com.fenixcore.optibienestar360.modules.notification.entity.UserNotification.Severity;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One inbox row as the user sees it ({@code GET /v1/me/notifications}).
 * {@code title} / {@code body} are the stored fallback texts; the frontend
 * prefers its own i18n text for {@code type} filled with {@code params}.
 *
 * @param read convenience flag, {@code readAt != null}
 */
public record UserNotificationDto(
        UUID uuid,
        String type,
        Category category,
        Severity severity,
        Map<String, Object> params,
        String title,
        String body,
        String actionUrl,
        @Display(Display.Kind.DATETIME) Instant createdAt,
        Instant readAt,
        boolean read
) {}
