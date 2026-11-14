SET search_path TO app, public;

-- ============================================================================
-- V249: a status column for shareable links — mark, never delete.
--
-- file_download_links and report_share told a dead link apart only by
-- comparing revoked_at / expires_at against now(): nothing marked them and no
-- index could find them. Now each row carries an explicit status:
--
--   ACTIVE     usable
--   EXPIRED    past expires_at (marked by LINK_EXPIRY_SWEEP, or on the first
--              hit after expiry — the use-time check never trusts the status
--              alone, it still compares expires_at with the current time)
--   REVOKED    revoked by hand (status = REVOKED  <=>  revoked_at IS NOT NULL)
--   EXHAUSTED  max_downloads reached — marked by the same atomic UPDATE that
--              counts the last download, so the sweep never has to look for it
--              (download_count >= max_downloads cannot use an index)
--
-- Volume: both tables grow with every shared file/report.
--   * (status, expires_at) serves the sweep (status = 'ACTIVE' AND expires_at
--     < now()) and any future purge (status IN (dead…) AND expires_at < cutoff)
--     with one index.
--   * revokeAllFor looks links up by (resource_table, resource_uuid, file_key)
--     among ACTIVE ones only: a partial index that does not grow with dead links
--     replaces the full (resource_table, resource_uuid) one.
--   * file_download_links is updated on every download (download_count + 1), a
--     column no index covers: fillfactor 90 keeps those updates HOT, and a lower
--     autovacuum threshold cleans the dead tuples they leave sooner.
--
-- report_share has no code yet (V65 prepared the schema only); it gets the same
-- shape now so its first consumer starts from it.
-- ============================================================================

-- ── file_download_links ─────────────────────────────────────────────────────
ALTER TABLE file_download_links ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

UPDATE file_download_links SET status = 'REVOKED'   WHERE revoked_at IS NOT NULL;
UPDATE file_download_links SET status = 'EXHAUSTED' WHERE status = 'ACTIVE'
    AND max_downloads IS NOT NULL AND download_count >= max_downloads;
UPDATE file_download_links SET status = 'EXPIRED'   WHERE status = 'ACTIVE' AND expires_at < now();

ALTER TABLE file_download_links
    ADD CONSTRAINT chk_file_download_links_status
        CHECK (status IN ('ACTIVE', 'EXPIRED', 'REVOKED', 'EXHAUSTED')),
    ADD CONSTRAINT chk_file_download_links_revoked
        CHECK ((status = 'REVOKED') = (revoked_at IS NOT NULL));

DROP INDEX idx_file_download_links_resource;
CREATE INDEX idx_file_download_links_status_expiry ON file_download_links (status, expires_at);
CREATE INDEX idx_file_download_links_active_file
    ON file_download_links (resource_table, resource_uuid, file_key) WHERE status = 'ACTIVE';

ALTER TABLE file_download_links SET (fillfactor = 90,
    autovacuum_vacuum_scale_factor = 0.05, autovacuum_analyze_scale_factor = 0.02);

-- ── report_share ────────────────────────────────────────────────────────────
ALTER TABLE report_share ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

UPDATE report_share SET status = 'REVOKED' WHERE revoked_at IS NOT NULL;
UPDATE report_share SET status = 'EXPIRED' WHERE status = 'ACTIVE' AND expires_at < now();

ALTER TABLE report_share
    ADD CONSTRAINT chk_report_share_status CHECK (status IN ('ACTIVE', 'EXPIRED', 'REVOKED')),
    ADD CONSTRAINT chk_report_share_revoked CHECK ((status = 'REVOKED') = (revoked_at IS NOT NULL));

DROP INDEX idx_report_share_expires;
CREATE INDEX idx_report_share_status_expiry ON report_share (status, expires_at);

-- ── The sweep: marks only, so it ships enabled ─────────────────────────────
INSERT INTO scheduled_jobs (code, display_name, description, cron_expression, timezone, parameters, enabled)
VALUES (
    'LINK_EXPIRY_SWEEP',
    'Marcado de enlaces vencidos',
    'Marca como vencidos (EXPIRED) los enlaces de descarga y de reportes compartidos cuya vigencia ya pasó. No borra nada: el enlace se rechaza al usarlo aunque este job aún no lo haya marcado.',
    '0 5 * * * *',
    'America/Caracas',
    '{}'::jsonb,
    true
);
