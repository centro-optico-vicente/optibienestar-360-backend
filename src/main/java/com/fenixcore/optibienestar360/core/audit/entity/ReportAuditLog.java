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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Insert-only report-generation trail (V63, spec 16-audit.md §Reportes) —
 * one row per document {@code GenericDocumentController} renders, whether or
 * not the R2 upload succeeds ({@code attachedFileId} stays {@code null} on
 * upload failure; the HTTP response still returns the bytes either way).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "report_audit_log")
public class ReportAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_audit_log_id", nullable = false, updatable = false)
    private Long id;

    @Column(nullable = false, updatable = false, unique = true)
    private UUID uuid;

    @Column(name = "report_type", nullable = false, length = 80)
    private String reportType;

    @Column(name = "entity_key", length = 80)
    private String entityKey;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "entity_uuid")
    private UUID entityUuid;

    @Column(name = "entity_identifier", length = 120)
    private String entityIdentifier;

    @Column(nullable = false, length = 10)
    private String format;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters_json", columnDefinition = "jsonb")
    private Map<String, Object> parametersJson;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "login_audit_log_id")
    private Long loginAuditLogId;

    @Column(name = "attached_file_id")
    private Long attachedFileId;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void onCreate() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
        Instant now = Instant.now();
        if (generatedAt == null) {
            generatedAt = now;
        }
        createdAt = now;
    }
}
