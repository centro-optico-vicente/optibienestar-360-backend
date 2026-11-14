package com.fenixcore.optibienestar360.common.storage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface FileDownloadLinkRepository extends JpaRepository<FileDownloadLink, Long> {

    /** Served by the UNIQUE index on {@code token}. */
    Optional<FileDownloadLink> findByToken(UUID token);

    /** Still-usable links to one exact file — served by the partial {@code idx_file_download_links_active_file} (V249). */
    List<FileDownloadLink> findByResourceTableAndResourceUuidAndFileKeyAndStatus(
            String resourceTable, UUID resourceUuid, String fileKey, DownloadLinkStatus status);

    /**
     * Counts one download atomically, in a single statement: it only succeeds while
     * the link is ACTIVE, inside its validity and under its cap, and the download
     * that reaches the cap marks the link EXHAUSTED in the same UPDATE. Two
     * concurrent hits can therefore never both take the last download — a
     * read-modify-write of {@code download_count} could.
     *
     * @return 1 when the download was granted, 0 when the link is no longer usable
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query(value = """
            UPDATE file_download_links
               SET download_count = download_count + 1,
                   status = CASE WHEN max_downloads IS NOT NULL AND download_count + 1 >= max_downloads
                                 THEN 'EXHAUSTED' ELSE status END
             WHERE file_download_links_id = :id
               AND status = 'ACTIVE'
               AND expires_at >= :now
               AND (max_downloads IS NULL OR download_count < max_downloads)
            """, nativeQuery = true)
    int claimDownload(@Param("id") Long id, @Param("now") Instant now);

    /** Marks one ACTIVE link EXPIRED — on the first hit after its expiry, before the sweep gets to it. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query(value = """
            UPDATE file_download_links SET status = 'EXPIRED'
             WHERE file_download_links_id = :id AND status = 'ACTIVE' AND expires_at < :now
            """, nativeQuery = true)
    int markExpired(@Param("id") Long id, @Param("now") Instant now);
}
