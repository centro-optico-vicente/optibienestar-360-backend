package com.fenixcore.optibienestar360.common.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * Marks shareable links whose validity has passed as {@code EXPIRED} (V249). It
 * marks, never deletes: the row stays as the record of the link.
 *
 * <p>Only expiry needs a sweep. Revocation is marked when it happens, and the cap
 * ({@code EXHAUSTED}) by the same UPDATE that counts the last download, so the
 * non-indexable {@code download_count >= max_downloads} is never searched for.
 * Nor is the sweep needed for correctness: a link past {@code expires_at} is
 * already rejected at use time. It keeps the status truthful so lists and
 * reports can filter by it through an index.</p>
 *
 * <p>Each batch picks its rows through {@code (status, expires_at)} with
 * {@code LIMIT}, in its own statement (auto-commit), so a large backlog never
 * holds a long transaction or long row locks.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LinkExpirySweepService {

    static final int BATCH_SIZE = 5_000;

    private final NamedParameterJdbcTemplate jdbc;

    public int expireDownloadLinks(Instant now) {
        return expire("file_download_links", """
                UPDATE file_download_links SET status = 'EXPIRED'
                 WHERE file_download_links_id IN (
                     SELECT file_download_links_id FROM file_download_links
                      WHERE status = 'ACTIVE' AND expires_at < :now
                      LIMIT :batch)
                """, now);
    }

    public int expireReportShares(Instant now) {
        return expire("report_share", """
                UPDATE report_share SET status = 'EXPIRED'
                 WHERE report_share_id IN (
                     SELECT report_share_id FROM report_share
                      WHERE status = 'ACTIVE' AND expires_at < :now
                      LIMIT :batch)
                """, now);
    }

    private int expire(String table, String sql, Instant now) {
        MapSqlParameterSource params = new MapSqlParameterSource("now", Timestamp.from(now))
                .addValue("batch", BATCH_SIZE);
        int total = 0;
        int marked;
        do {
            marked = jdbc.update(sql, params);
            total += marked;
        } while (marked == BATCH_SIZE);
        if (total > 0) {
            log.info("Link expiry sweep: {} row(s) of {} marked EXPIRED", total, table);
        }
        return total;
    }
}
