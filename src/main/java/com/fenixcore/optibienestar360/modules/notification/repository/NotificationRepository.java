package com.fenixcore.optibienestar360.modules.notification.repository;

import com.fenixcore.optibienestar360.modules.notification.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface NotificationRepository extends JpaRepository<Notification, Long>,
        JpaSpecificationExecutor<Notification> {

    Optional<Notification> findByUuid(UUID uuid);

    /**
     * The worker's due-batch: rows ready to (re)send now — {@code PENDING}
     * whose {@code scheduled_for} has arrived, plus {@code FAILED} whose
     * {@code next_retry_at} backoff has elapsed. Ordered oldest-scheduled
     * first so the queue drains FIFO; the caller passes a {@link Pageable}
     * to bound the batch size. Backed by the V28 partial indexes
     * {@code idx_notifications_pending_due} + {@code idx_notifications_retry_due}.
     */
    @Query("""
            SELECT n FROM Notification n
            WHERE n.active = true
              AND ((n.status = 'PENDING' AND n.scheduledFor <= :now)
                OR (n.status = 'FAILED' AND n.nextRetryAt IS NOT NULL AND n.nextRetryAt <= :now))
            ORDER BY n.scheduledFor ASC
            """)
    List<Notification> findDueForDispatch(@Param("now") Instant now, Pageable pageable);

    /**
     * Idempotent-enqueue pre-check ("did we already queue this template for
     * this source row?"). Backed by the V28 partial index
     * {@code idx_notifications_source (source_module, source_entity_uuid, template_code)}.
     */
    boolean existsBySourceModuleAndSourceEntityUuidAndTemplateCode(
            String sourceModule, UUID sourceEntityUuid, String templateCode);
}
