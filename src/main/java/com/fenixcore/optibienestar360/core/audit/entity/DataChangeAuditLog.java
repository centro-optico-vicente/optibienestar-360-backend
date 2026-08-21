package com.fenixcore.optibienestar360.core.audit.entity;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Insert-only create/update/delete trail captured by {@code DataChangeAuditAspect}
 * (V62, spec 16-audit.md §Aspecto AOP). Never updated after insert.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "data_change_audit_log")
public class DataChangeAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "data_change_audit_log_id", nullable = false, updatable = false)
    private Long id;

    @Column(nullable = false, updatable = false, unique = true)
    private UUID uuid;

    @Column(name = "entity_key", nullable = false, length = 80)
    private String entityKey;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "entity_uuid")
    private UUID entityUuid;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuditAction action;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_json", columnDefinition = "jsonb")
    private Map<String, Object> beforeJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_json", columnDefinition = "jsonb")
    private Map<String, Object> afterJson;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "login_audit_log_id")
    private Long loginAuditLogId;

    @Column(name = "request_method", length = 10)
    private String requestMethod;

    @Column(name = "request_path", length = 255)
    private String requestPath;

    @Column(name = "restored_from_id")
    private Long restoredFromId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void onCreate() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
        Instant now = Instant.now();
        if (occurredAt == null) {
            occurredAt = now;
        }
        createdAt = now;
    }
}
