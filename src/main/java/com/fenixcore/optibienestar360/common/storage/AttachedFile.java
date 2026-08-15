package com.fenixcore.optibienestar360.common.storage;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Generic, polymorphic file attachment — spec {@code .ai/specs/08-storage-r2.md}
 * §4. Any table can attach files to one of its rows via
 * {@code (ownerTable, ownerUuid)} instead of a bespoke entity+table per
 * domain. Referential integrity for the owner is enforced at the
 * application layer (the controller of each domain passes a fixed
 * {@code ownerTable}, never one taken from the request).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "attached_files")
@AttributeOverride(name = "id", column = @Column(name = "attached_files_id", nullable = false, updatable = false))
public class AttachedFile extends BaseEntity {

    @Column(name = "owner_table", length = 50, nullable = false)
    private String ownerTable;

    @Column(name = "owner_uuid", nullable = false)
    private UUID ownerUuid;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", length = 20, nullable = false)
    private FileVisibility visibility;

    @Column(name = "category", length = 50, nullable = false)
    private String category;

    @Column(name = "file_key", length = 500, nullable = false)
    private String fileKey;

    @Column(name = "file_name", length = 255, nullable = false)
    private String fileName;

    @Column(name = "mime_type", length = 100, nullable = false)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by")
    private User uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    /** Business-level ACL, orthogonal to {@link #visibility} (a storage concern) — spec §4.2. */
    @Column(name = "shared", nullable = false)
    private boolean shared = false;

    @Column(name = "published_at")
    private Instant publishedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "published_by")
    private User publishedBy;
}
