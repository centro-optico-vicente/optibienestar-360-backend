package com.fenixcore.optibienestar360.modules.notification.service;

import com.fenixcore.optibienestar360.modules.notification.entity.Notification.Channel;
import org.springframework.stereotype.Component;

/**
 * Single choke point for "which channel does this notification go over".
 * Every new call site should resolve through here instead of hardcoding
 * {@code Channel.EMAIL} — the only real branch point today is
 * {@link #resolve}, deliberately unused by its own body.
 */
@Component
public class NotificationChannelResolver {

    /** Who the notification is going to — not every recipient type may want the same channel in the future. */
    public enum RecipientType {
        MEMBER, PROMOTER, ADMIN
    }

    /** Stub: always EMAIL — the extension point for SMS/WhatsApp per recipient/event lives here. */
    public Channel resolve(RecipientType recipientType, String eventType) {
        return Channel.EMAIL;
    }
}
