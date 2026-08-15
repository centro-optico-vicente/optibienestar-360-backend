package com.fenixcore.optibienestar360.common.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Configurable extension/size policy per {@link FileVisibility} scope —
 * migration V55, spec §2.1. One row per visibility; {@code mode} decides
 * whether {@code extensions} is an allowlist or a denylist, so an admin can
 * flip a scope's policy with an UPDATE, no schema change.
 *
 * <p>No {@code uuid}/audit columns — this is a small, internally-managed
 * configuration table, not a business record; {@code visibility} is its own
 * natural key.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "file_type_policies")
public class FileTypePolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "file_type_policies_id", nullable = false, updatable = false)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", length = 20, nullable = false, unique = true)
    private FileVisibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", length = 10, nullable = false)
    private Mode mode;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "extensions", nullable = false)
    private List<String> extensions;

    @Column(name = "max_size_bytes", nullable = false)
    private Long maxSizeBytes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    public enum Mode {
        ALLOWLIST, DENYLIST
    }
}
