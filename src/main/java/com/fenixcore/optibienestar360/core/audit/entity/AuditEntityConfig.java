package com.fenixcore.optibienestar360.core.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import jakarta.persistence.EntityListeners;

import java.time.Instant;
import java.util.UUID;

/**
 * Per-entity audit toggle (V60) — the {@code entity_key} must match the
 * {@code entity} attribute of {@link com.fenixcore.optibienestar360.core.audit.Auditable}
 * on the corresponding service method. Read/cached by {@link com.fenixcore.optibienestar360.core.audit.AuditEntityConfigService}.
 *
 * <p>Doesn't extend {@link com.fenixcore.optibienestar360.core.entity.BaseAuditEntity} —
 * this table has no {@code is_active}/{@code status} columns (spec 16-audit.md).</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "audit_entity_config")
@EntityListeners(AuditingEntityListener.class)
public class AuditEntityConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_entity_config_id", nullable = false, updatable = false)
    private Long id;

    @Column(nullable = false, updatable = false, unique = true)
    private UUID uuid;

    @Column(name = "entity_key", nullable = false, unique = true, length = 80)
    private String entityKey;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "table_name", length = 120)
    private String tableName;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "audit_create", nullable = false)
    private boolean auditCreate = true;

    @Column(name = "audit_update", nullable = false)
    private boolean auditUpdate = true;

    @Column(name = "audit_delete", nullable = false)
    private boolean auditDelete = true;

    @Column(name = "audit_report", nullable = false)
    private boolean auditReport = true;

    @Column(name = "capture_before_after", nullable = false)
    private boolean captureBeforeAfter = true;

    @Column(columnDefinition = "text")
    private String notes;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private UUID updatedBy;

    @PrePersist
    private void assignUuid() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
    }
}
