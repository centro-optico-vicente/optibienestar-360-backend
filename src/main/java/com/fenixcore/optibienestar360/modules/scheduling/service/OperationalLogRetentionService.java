package com.fenixcore.optibienestar360.modules.scheduling.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * Purges old rows of the operational log tables: data kept only to operate the
 * system day to day, never as an audit trail.
 *
 * <ul>
 *   <li>{@code notifications}: finished outbox rows ({@code SENT}, {@code DEAD_LETTER}).
 *       Rows still pending or retrying are never touched.</li>
 *   <li>{@code scheduled_job_runs}: finished job runs. A {@code RUNNING} row is the
 *       concurrency guard, so it is never touched.</li>
 *   <li>{@code file_download_links}: dead links (status EXPIRED, REVOKED or EXHAUSTED, V249)
 *       whose validity ended before the cutoff — found through
 *       {@code idx_file_download_links_status_expiry}.</li>
 * </ul>
 *
 * <p>Out of scope on purpose: the audit trail ({@code data_change_audit_log},
 * {@code login_audit_log}, {@code report_audit_log},
 * {@code medical_record_access_log}, review logs). How long to keep it is a
 * legal/compliance decision, not an operational one.</p>
 *
 * <p>Deletes run in batches of {@value #BATCH_SIZE} rows, each in its own
 * statement (auto-commit), so a large first purge never holds a long
 * transaction or long locks. No table is referenced by a foreign key.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationalLogRetentionService {

    static final int BATCH_SIZE = 5_000;

    private final NamedParameterJdbcTemplate jdbc;

    public int purgeNotifications(Instant cutoff) {
        return purge("notifications", """
                DELETE FROM notifications WHERE notifications_id IN (
                    SELECT notifications_id FROM notifications
                    WHERE status IN ('SENT', 'DEAD_LETTER')
                      AND COALESCE(sent_at, last_attempt_at, created_at) < :cutoff
                    LIMIT :batch)
                """, cutoff);
    }

    public int purgeJobRuns(Instant cutoff) {
        return purge("scheduled_job_runs", """
                DELETE FROM scheduled_job_runs WHERE scheduled_job_runs_id IN (
                    SELECT scheduled_job_runs_id FROM scheduled_job_runs
                    WHERE outcome <> 'RUNNING' AND started_at < :cutoff
                    LIMIT :batch)
                """, cutoff);
    }

    public int purgeDownloadLinks(Instant cutoff) {
        return purge("file_download_links", """
                DELETE FROM file_download_links WHERE file_download_links_id IN (
                    SELECT file_download_links_id FROM file_download_links
                    WHERE status IN ('EXPIRED', 'REVOKED', 'EXHAUSTED') AND expires_at < :cutoff
                    LIMIT :batch)
                """, cutoff);
    }

    private int purge(String table, String sql, Instant cutoff) {
        MapSqlParameterSource params = new MapSqlParameterSource("cutoff", Timestamp.from(cutoff))
                .addValue("batch", BATCH_SIZE);
        int total = 0;
        int deleted;
        do {
            deleted = jdbc.update(sql, params);
            total += deleted;
        } while (deleted == BATCH_SIZE);
        log.info("Operational log retention: {} row(s) purged from {} (older than {})", total, table, cutoff);
        return total;
    }
}
