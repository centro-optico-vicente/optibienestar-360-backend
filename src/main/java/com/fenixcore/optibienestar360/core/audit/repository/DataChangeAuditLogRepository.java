package com.fenixcore.optibienestar360.core.audit.repository;

import com.fenixcore.optibienestar360.core.audit.entity.DataChangeAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DataChangeAuditLogRepository
        extends JpaRepository<DataChangeAuditLog, Long>, JpaSpecificationExecutor<DataChangeAuditLog> {

    Page<DataChangeAuditLog> findByEntityKeyAndEntityUuidOrderByOccurredAtDesc(
            String entityKey, UUID entityUuid, Pageable pageable);

    Page<DataChangeAuditLog> findByEntityKeyOrderByOccurredAtDesc(String entityKey, Pageable pageable);

    /**
     * The earliest row for a record — typically its CREATE, but falls back
     * gracefully to whatever is oldest if the record predates auditing being
     * turned on for its {@code entity_key}. Backs the "when was this created"
     * pinned lookup that stays useful no matter which page of a long history
     * (500+ rows) the client is currently viewing.
     */
    Optional<DataChangeAuditLog> findFirstByEntityKeyAndEntityUuidOrderByOccurredAtAsc(
            String entityKey, UUID entityUuid);
}
