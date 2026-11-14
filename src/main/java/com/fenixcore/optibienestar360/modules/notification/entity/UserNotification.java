package com.fenixcore.optibienestar360.modules.notification.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One row of the in-app notification inbox (V259, hub ADR 0039): a notice
 * addressed to one user, with read state and an optional link. Written only by
 * {@code UserNotificationPublisher}; read and marked as read by the user through
 * {@code GET/POST /v1/me/notifications}.
 *
 * <p>Not to be confused with {@link Notification} (V28), the outbound email
 * queue. The publisher may write both for the same event.</p>
 *
 * <p>{@code recipientUserId} is a plain {@code Long} (not a {@code @ManyToOne}
 * to {@code User}), same as {@link Notification}, so the notification module
 * stays decoupled from the auth entity graph.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "user_notifications")
@AttributeOverride(name = "id", column = @Column(name = "user_notifications_id", nullable = false, updatable = false))
public class UserNotification extends BaseEntity {

    @Column(name = "recipient_user_id", nullable = false)
    private Long recipientUserId;

    // ─── Classification ──────────────────────────────────────────────────────

    /** Open type code (e.g. {@code REGISTRATION_APPROVED}); no DB CHECK. */
    @Column(name = "type", length = 80, nullable = false)
    private String type;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 30, nullable = false)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 20, nullable = false)
    private Severity severity = Severity.INFO;

    // ─── Content ─────────────────────────────────────────────────────────────

    /** Values the frontend interpolates into {@code notifications.types.<TYPE>.*}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "params", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> params = new HashMap<>();

    @Column(name = "fallback_title", length = 200, nullable = false)
    private String fallbackTitle;

    @Column(name = "fallback_body", columnDefinition = "text")
    private String fallbackBody;

    /** Internal frontend route (e.g. {@code /allies}); optional. */
    @Column(name = "action_url", length = 500)
    private String actionUrl;

    // ─── Origin / traceability ───────────────────────────────────────────────

    @Column(name = "source_module", length = 40)
    private String sourceModule;

    @Column(name = "source_entity_uuid")
    private UUID sourceEntityUuid;

    /** Idempotency key per (recipient, type); {@code null} = no dedup. */
    @Column(name = "dedup_key", length = 200)
    private String dedupKey;

    // ─── Inbox state ─────────────────────────────────────────────────────────

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    /** Inbox grouping (V259 CHECK). Also the unit of future per-user preferences. */
    public enum Category {
        ACCOUNT, PAYMENTS, COMMISSIONS, MEMBERSHIP, NETWORK, CELEBRATIONS
    }

    /** Visual tone of the notice (V259 CHECK). */
    public enum Severity {
        INFO, SUCCESS, WARNING, ERROR
    }
}
