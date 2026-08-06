package com.fenixcore.optibienestar360.modules.notification.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One row of the persistent outbound notification queue (V28). The
 * application enqueues a row per message (welcome, payment reminder,
 * membership notice, referral reward, …); the {@code NotificationService}
 * worker polls due rows, renders the template and delivers over the channel.
 *
 * <p>{@code status} is inherited from {@link BaseEntity} as a {@code String}
 * (same pattern as {@code Membership} / {@code Payment}); callers use the
 * inner {@link NotificationStatus} enum for type-safety. The V28 CHECK
 * constraints pin the column and enforce per-state coherence
 * (SENT ⇒ sentAt, FAILED/DEAD_LETTER ⇒ lastErrorMessage,
 * attemptCount ≤ maxAttempts, DEAD_LETTER ⇒ attemptCount ≥ maxAttempts).</p>
 *
 * <p>{@code recipientUserId} is a plain {@code Long} (not a {@code @ManyToOne}
 * to {@code User}) — the recipient email is snapshotted at enqueue time and
 * the id is only a navigability hint, so the notification module stays
 * decoupled from the auth module.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "notifications")
@AttributeOverride(name = "id", column = @Column(name = "notifications_id", nullable = false, updatable = false))
public class Notification extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private Channel channel = Channel.EMAIL;

    // ─── Recipient (snapshot) ────────────────────────────────────────────────

    @Column(name = "recipient_email", nullable = false, columnDefinition = "citext")
    private String recipientEmail;

    @Column(name = "recipient_user_id")
    private Long recipientUserId;

    /** 'es' / 'en' — selects the {@code *_es.html} / {@code *_en.html} template variant. */
    @Column(name = "recipient_locale", length = 10, nullable = false)
    private String recipientLocale = "es";

    // ─── Content ─────────────────────────────────────────────────────────────

    @Column(name = "template_code", length = 80, nullable = false)
    private String templateCode;

    @Column(length = 200, nullable = false)
    private String subject;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "template_vars", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> templateVars = new HashMap<>();

    // ─── Origin / traceability ───────────────────────────────────────────────

    @Column(name = "source_module", length = 40)
    private String sourceModule;

    @Column(name = "source_entity_uuid")
    private UUID sourceEntityUuid;

    // ─── Lifecycle ───────────────────────────────────────────────────────────
    // status lives in BaseEntity (String); use NotificationStatus for type-safety.

    @Column(name = "scheduled_for", nullable = false)
    private Instant scheduledFor = Instant.now();

    // ─── Retry policy ────────────────────────────────────────────────────────

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 5;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "last_error_message", columnDefinition = "text")
    private String lastErrorMessage;

    // ─── Delivery outcome ────────────────────────────────────────────────────

    @Column(name = "sent_at")
    private Instant sentAt;

    /** Delivery channel. V1 is EMAIL-only; the V28 CHECK pins the column. */
    public enum Channel {
        EMAIL
    }

    /** Lifecycle values that may land in {@link BaseEntity#getStatus()} (V28 CHECK). */
    public enum NotificationStatus {
        PENDING, SENDING, SENT, FAILED, DEAD_LETTER
    }
}
