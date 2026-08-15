package com.fenixcore.optibienestar360.common.storage;

import com.fenixcore.optibienestar360.modules.auth.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Shareable download link with its own business-level expiration (can be
 * days), decoupled from the short R2 presigned-URL TTL (minutes, capped at
 * 7 days by R2 itself) — spec §6, migration V56.
 *
 * <p>{@code token} is what gets emailed/shown, never the presigned URL
 * itself: {@code GET /v1/files/links/{token}} mints a fresh short-lived
 * presigned URL on every hit for as long as this row is still valid.</p>
 *
 * <p>No {@code uuid}/audit superclass — {@code token} is this row's own
 * external identifier.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "file_download_links")
public class FileDownloadLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "file_download_links_id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "token", nullable = false, updatable = false, unique = true)
    private UUID token;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", length = 20, nullable = false)
    private FileVisibility visibility;

    @Column(name = "resource_table", length = 50, nullable = false)
    private String resourceTable;

    @Column(name = "resource_uuid", nullable = false)
    private UUID resourceUuid;

    @Column(name = "file_key", length = 500, nullable = false)
    private String fileKey;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "max_downloads")
    private Integer maxDownloads;

    @Column(name = "download_count", nullable = false)
    private int downloadCount = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public boolean isValid() {
        if (revokedAt != null) return false;
        if (Instant.now().isAfter(expiresAt)) return false;
        return maxDownloads == null || downloadCount < maxDownloads;
    }
}
