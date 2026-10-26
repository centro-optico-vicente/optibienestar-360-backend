package com.fenixcore.optibienestar360.modules.media.entity;

import com.fenixcore.optibienestar360.common.storage.AttachedFile;
import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * One image of an owner's gallery (hub ADR 0027). The owner is polymorphic —
 * {@code ownerTable} + {@code ownerUuid}, the same pattern as
 * {@code attached_files} — so allies, products and ally offers share the table
 * and users, members or advisors can join later without a migration.
 *
 * <p>The image is <b>either</b> an uploaded file ({@code publicKey}, in the
 * public R2 bucket) <b>or</b> an external https URL ({@code externalUrl}); the
 * DB enforces exactly one. Each owner has at most one active
 * {@link #isPrimary() primary} image — the one its {@code logo_url} column
 * mirrors for lists.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "media_items")
@AttributeOverride(name = "id", column = @Column(name = "media_items_id", nullable = false, updatable = false))
public class MediaItem extends BaseEntity {

    @Column(name = "owner_table", length = 50, nullable = false, updatable = false)
    private String ownerTable;

    @Column(name = "owner_uuid", nullable = false, updatable = false)
    private UUID ownerUuid;

    /** The stored file behind an uploaded image; {@code null} for an external URL. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attached_file_id")
    private AttachedFile attachedFile;

    @Column(name = "public_key", length = 500)
    private String publicKey;

    @Column(name = "external_url", length = 500)
    private String externalUrl;

    /** Alt text. */
    @Column(length = 200)
    private String caption;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    public boolean isUploaded() {
        return publicKey != null;
    }
}
