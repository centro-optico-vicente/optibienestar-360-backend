package com.fenixcore.optibienestar360.core.audit;

import com.fenixcore.optibienestar360.common.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Deletes from R2 the generated reports (V252: {@code attached_files} with
 * visibility {@code TEMPORARY}, category {@code GENERATED_REPORT}) uploaded
 * before a cutoff, and <b>marks</b> their rows ({@code is_active = false},
 * {@code status = 'PURGED'}). No row is deleted: {@code report_audit_log} keeps
 * its row and its {@code attached_file_id}, so the audit still says what was
 * generated, by whom and when; only the stored copy is gone. Any still-active
 * download link to a purged file is revoked in the same step.
 *
 * <p>Works oldest first, {@value #BATCH_SIZE} files per round, walking
 * {@code idx_attached_files_temporary_stored} with a keyset cursor
 * ({@code uploaded_at, attached_files_id}). Each round deletes its objects with one
 * R2 {@code DeleteObjects} request and marks only the rows whose object R2
 * confirmed deleted. A failed object keeps its row active and is retried on the
 * next run; the cursor moves past it, so one bad object never stalls the run.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeneratedReportPurgeService {

    static final int BATCH_SIZE = 500;
    static final String CATEGORY = "GENERATED_REPORT";

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectProvider<StorageService> storageProvider;

    public record PurgeResult(int purged, int failed, boolean storageAvailable) {}

    private record StoredReport(long id, UUID ownerUuid, String fileKey, Instant uploadedAt) {}

    public PurgeResult purgeUploadedBefore(Instant cutoff) {
        StorageService storage = storageProvider.getIfAvailable();
        if (storage == null) {
            log.warn("Generated report purge skipped: R2 storage is disabled");
            return new PurgeResult(0, 0, false);
        }
        int purged = 0;
        int failed = 0;
        Instant afterUploadedAt = Instant.EPOCH;
        long afterId = 0;
        while (true) {
            List<StoredReport> batch = nextBatch(cutoff, afterUploadedAt, afterId);
            if (batch.isEmpty()) {
                break;
            }
            StoredReport last = batch.getLast();
            afterUploadedAt = last.uploadedAt();
            afterId = last.id();

            Set<String> notDeleted;
            try {
                notDeleted = storage.deleteAll(storage.getBucket(), batch.stream().map(StoredReport::fileKey).toList());
            } catch (RuntimeException ex) {
                log.warn("Generated report purge: R2 delete request failed for {} file(s); they stay for the next run",
                        batch.size(), ex);
                failed += batch.size();
                continue;
            }
            List<StoredReport> deleted = batch.stream().filter(r -> !notDeleted.contains(r.fileKey())).toList();
            markPurged(deleted);
            purged += deleted.size();
            failed += batch.size() - deleted.size();
            if (batch.size() < BATCH_SIZE) {
                break;
            }
        }
        if (purged > 0 || failed > 0) {
            log.info("Generated report purge: {} file(s) deleted from R2 and marked PURGED, {} failed (uploaded before {})",
                    purged, failed, cutoff);
        }
        return new PurgeResult(purged, failed, true);
    }

    private List<StoredReport> nextBatch(Instant cutoff, Instant afterUploadedAt, long afterId) {
        // Predicate matches idx_attached_files_temporary_stored (visibility + is_active),
        // ordered by its columns, so each round is an index range scan.
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("cutoff", Timestamp.from(cutoff))
                .addValue("category", CATEGORY)
                .addValue("batch", BATCH_SIZE)
                .addValue("afterUploadedAt", Timestamp.from(afterUploadedAt))
                .addValue("afterId", afterId);
        return jdbc.query("""
                SELECT attached_files_id, owner_uuid, file_key, uploaded_at
                  FROM attached_files
                 WHERE visibility = 'TEMPORARY' AND is_active
                   AND category = :category
                   AND uploaded_at < :cutoff
                   AND (uploaded_at, attached_files_id) > (:afterUploadedAt, :afterId)
                 ORDER BY uploaded_at, attached_files_id
                 LIMIT :batch
                """, params, (rs, i) -> new StoredReport(
                        rs.getLong("attached_files_id"),
                        rs.getObject("owner_uuid", UUID.class),
                        rs.getString("file_key"),
                        rs.getTimestamp("uploaded_at").toInstant()));
    }

    private void markPurged(List<StoredReport> deleted) {
        if (deleted.isEmpty()) {
            return;
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("ids", deleted.stream().map(StoredReport::id).toList())
                .addValue("ownerUuids", deleted.stream().map(StoredReport::ownerUuid).distinct().toList())
                .addValue("keys", deleted.stream().map(StoredReport::fileKey).toList());
        jdbc.update("""
                UPDATE attached_files SET is_active = false, status = 'PURGED'
                 WHERE attached_files_id IN (:ids)
                """, params);
        // A link to a file that no longer exists would only answer 302 → 404 from R2.
        jdbc.update("""
                UPDATE file_download_links SET status = 'REVOKED', revoked_at = now()
                 WHERE resource_table = 'report_audit_log'
                   AND resource_uuid IN (:ownerUuids)
                   AND file_key IN (:keys)
                   AND status = 'ACTIVE'
                """, params);
    }
}
