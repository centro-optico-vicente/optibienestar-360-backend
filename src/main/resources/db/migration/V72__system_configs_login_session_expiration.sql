SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V72: login_session_expiration_days — how long a login_audit_log session
-- (the sid claim) stays ACTIVE before LoginSessionSweepJob expires it,
-- configurable without redeploy (spec 16-audit.md §Login). Independent from
-- jwt.refresh-expiration-days (app property, controls the JWT's own TTL) —
-- this controls the server-side session row's session_expires_at, which is
-- what actually gets checked on every request via is_valid.
-- ────────────────────────────────────────────────────────────────────────────

ALTER TABLE system_configs
	ADD COLUMN login_session_expiration_days INT NOT NULL DEFAULT 30
		CONSTRAINT chk_system_configs_login_session_expiration_days CHECK (login_session_expiration_days > 0);
