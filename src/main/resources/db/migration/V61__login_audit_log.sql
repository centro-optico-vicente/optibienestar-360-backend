SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V61: login_audit_log — every login attempt (success and failure) plus
-- session lifecycle (spec 07-audit.md §Decisiones 3, 5).
--
-- On successful login this row also acts as the session record: its `uuid`
-- travels as the `sid` claim in the access/refresh JWTs (AuthService.login()
-- inserts this row BEFORE minting tokens). `user_sessions_log` is not
-- replaced — it stays the operational record of live sessions; this table is
-- the audit/history trail plus the fast is_valid check described below.
--
-- `is_valid` is the only column read on the JwtAuthenticationFilter hot path:
--   - logout() sets is_valid=false, session_status='LOGGED_OUT', logged_out_at=now()
--   - a sweep job sets is_valid=false, session_status='EXPIRED' where
--     session_expires_at < now() (the only place that compares dates/times)
-- This is an additional layer over the existing jti blacklist
-- (TokenBlacklistService) — not a replacement for it.
-- ────────────────────────────────────────────────────────────────────────────

CREATE TABLE login_audit_log
(
    login_audit_log_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    user_id             BIGINT       REFERENCES users (users_id),
    attempted_email     VARCHAR(255) NOT NULL,
    result              VARCHAR(20)  NOT NULL
        CONSTRAINT chk_login_audit_result
            CHECK (result IN ('SUCCESS', 'FAILED_CREDENTIALS', 'FAILED_LOCKED', 'FAILED_INACTIVE')),
    roles               JSONB,
    locale              VARCHAR(10),
    ip_address          INET,
    user_agent          VARCHAR(500),
    hostname            VARCHAR(255),
    jti                 VARCHAR(36),
    failure_reason      VARCHAR(255),
    session_status      VARCHAR(20)
        CONSTRAINT chk_login_audit_session_status
            CHECK (session_status IN ('ACTIVE', 'LOGGED_OUT', 'EXPIRED', 'REVOKED')),
    session_expires_at  TIMESTAMPTZ,
    is_valid            BOOLEAN      NOT NULL DEFAULT TRUE,
    logged_out_at       TIMESTAMPTZ,
    logout_reason       VARCHAR(50),
    attempted_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_login_audit_user_id        ON login_audit_log (user_id);
CREATE INDEX idx_login_audit_email          ON login_audit_log (attempted_email);
CREATE INDEX idx_login_audit_attempted_at   ON login_audit_log (attempted_at DESC);
CREATE INDEX idx_login_audit_jti            ON login_audit_log (jti);
CREATE UNIQUE INDEX idx_login_audit_uuid    ON login_audit_log (uuid);
CREATE INDEX idx_login_audit_session_status ON login_audit_log (session_status) WHERE session_status = 'ACTIVE';
CREATE INDEX idx_login_audit_is_valid       ON login_audit_log (uuid) WHERE is_valid = TRUE;
