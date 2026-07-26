package com.fenixcore.optibienestar360.modules.notification.dto;

import java.util.Map;
import java.util.UUID;

/**
 * Internal command to enqueue one outbound notification (no HTTP surface —
 * callers are other services / job runners). Nullable fields fall back to the
 * queue defaults: {@code recipientLocale} → {@code es}, {@code templateVars}
 * → empty, {@code maxAttempts} → service default (5).
 *
 * <p>When {@code idempotent} is true and both {@code sourceModule} and
 * {@code sourceEntityUuid} are set, the service skips the enqueue if a row
 * with the same (module, entity, template) already exists — the
 * "did we already notify about X?" guard backed by the V28 source index.</p>
 */
public record NotificationEnqueueCommand(
        String recipientEmail,
        Long recipientUserId,
        String recipientLocale,
        String templateCode,
        String subject,
        Map<String, Object> templateVars,
        String sourceModule,
        UUID sourceEntityUuid,
        Integer maxAttempts,
        boolean idempotent
) {}
