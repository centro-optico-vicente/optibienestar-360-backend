package com.fenixcore.optibienestar360.modules.notification.repository;

import com.fenixcore.optibienestar360.modules.notification.entity.UserNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** In-app inbox rows (V259). Every read is scoped by {@code recipientUserId}. */
@Transactional(readOnly = true)
public interface UserNotificationRepository extends JpaRepository<UserNotification, Long>,
        JpaSpecificationExecutor<UserNotification> {

    /** One of the caller's own rows; another user's uuid resolves to empty (→ 404). */
    Optional<UserNotification> findByUuidAndRecipientUserIdAndActiveTrue(UUID uuid, Long recipientUserId);

    /** Dedup pre-check of the publisher, backed by {@code uq_user_notifications_dedup}. */
    boolean existsByRecipientUserIdAndTypeAndDedupKey(Long recipientUserId, String type, String dedupKey);

    /**
     * Unread, non-expired rows of one user — the cheap count the frontend
     * polls. Backed by {@code idx_user_notifications_inbox}.
     */
    @Query("""
            SELECT COUNT(n) FROM UserNotification n
            WHERE n.recipientUserId = :userId
              AND n.active = true
              AND n.readAt IS NULL
              AND (n.expiresAt IS NULL OR n.expiresAt > :now)
            """)
    long countUnread(@Param("userId") Long userId, @Param("now") Instant now);

    /**
     * Marks every unread row of one user as read. Bulk update: the
     * {@code updated_at} trigger keeps the audit column current.
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE UserNotification n SET n.readAt = :now
            WHERE n.recipientUserId = :userId
              AND n.active = true
              AND n.readAt IS NULL
            """)
    int markAllRead(@Param("userId") Long userId, @Param("now") Instant now);
}
