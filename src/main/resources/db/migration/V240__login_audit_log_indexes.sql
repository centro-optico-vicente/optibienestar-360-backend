SET search_path TO app, public;

-- ============================================================================
-- V240: index login_audit_log for the queries it actually serves.
--
-- One row per login attempt (success or failure), and the session record of
-- every successful one: it is among the fastest-growing tables. The V61 set
-- had duplicates and indexes no query uses (each one taxes every insert and
-- every session update), while the default listing sort and the sweep job had
-- no usable index. Queries served, and by which index:
--
--   findByUuid (JWT filter sid check, refresh restart recovery, logout)
--     → login_audit_log_uuid_key (the UNIQUE constraint's own index)
--   LoginSessionSweepJob: is_valid AND ACTIVE AND session_expires_at < now()
--     → idx_login_audit_active_expiry (partial: only live sessions)
--   revokeActiveSessionsOfUser (password change/reset), countBySessionStatus(ACTIVE)
--     → idx_login_audit_active_user (partial: only live sessions)
--   GET /v1/admin/audit/logins — default sort createdAt DESC
--     → idx_login_audit_created_at
--   … filtered by date range on attemptedAt / sorted by it
--     → idx_login_audit_attempted_at (V61, kept)
--   … filtered by user / email, newest first
--     → idx_login_audit_user_attempted / idx_login_audit_email_attempted
--       (the user_id one also covers the users FK)
--   … filtered by a failed result
--     → idx_login_audit_failed (partial: failures only)
--
-- The partial "live session" indexes stay tiny no matter how much history
-- accumulates: a row leaves them as soon as its session ends.
-- ============================================================================

-- Duplicates / unused -------------------------------------------------------
-- Same column as the UNIQUE constraint's index (login_audit_log_uuid_key).
DROP INDEX IF EXISTS idx_login_audit_uuid;
-- Partial copy of the uuid index: lookups by uuid already use the unique one.
DROP INDEX IF EXISTS idx_login_audit_is_valid;
-- jti is written (attachJti) but never searched; dropping it also lets that
-- update be a HOT update (no indexed column touched).
DROP INDEX IF EXISTS idx_login_audit_jti;
-- Indexed a constant (session_status WHERE session_status = 'ACTIVE'):
-- replaced by the two partial indexes below, which carry a useful key.
DROP INDEX IF EXISTS idx_login_audit_session_status;
-- Single-column versions replaced by "column + newest first" composites.
DROP INDEX IF EXISTS idx_login_audit_user_id;
DROP INDEX IF EXISTS idx_login_audit_email;

-- Live sessions ---------------------------------------------------------------
CREATE INDEX idx_login_audit_active_expiry
    ON login_audit_log (session_expires_at)
    WHERE session_status = 'ACTIVE';

CREATE INDEX idx_login_audit_active_user
    ON login_audit_log (user_id)
    WHERE session_status = 'ACTIVE';

-- Admin listing -----------------------------------------------------------------
CREATE INDEX idx_login_audit_created_at
    ON login_audit_log (created_at DESC);

CREATE INDEX idx_login_audit_user_attempted
    ON login_audit_log (user_id, attempted_at DESC);

CREATE INDEX idx_login_audit_email_attempted
    ON login_audit_log (attempted_email, attempted_at DESC);

CREATE INDEX idx_login_audit_failed
    ON login_audit_log (attempted_at DESC)
    WHERE result <> 'SUCCESS';

-- Children: data_change_audit_log already indexes its FK to this table
-- (idx_data_change_login_audit); report_audit_log did not, so deleting or
-- archiving old login rows would scan it once per row.
CREATE INDEX IF NOT EXISTS idx_report_audit_login_audit
    ON report_audit_log (login_audit_log_id);

-- Storage -----------------------------------------------------------------------
-- Rows are updated after insert (jti, then logout/expiry): leave room on each
-- page so those updates stay on the same page (HOT), and vacuum/analyze sooner
-- than the 20%/10% defaults, which on a large append-mostly table means rarely.
ALTER TABLE login_audit_log SET (
    fillfactor = 90,
    autovacuum_vacuum_scale_factor = 0.05,
    autovacuum_analyze_scale_factor = 0.02
);
