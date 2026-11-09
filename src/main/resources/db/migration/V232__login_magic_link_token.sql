SET search_path TO app, public;

-- ============================================================
-- V232: single-use access link ("magic link") token (hub ADR 0037 §4).
--
-- Same storage shape as the password reset token (SHA-256 of the raw token,
-- never the raw value) but in its own columns, so a LOGIN token can never be
-- redeemed as a password reset and vice versa. Cleared on first use.
-- ============================================================

ALTER TABLE users
    ADD COLUMN login_token_hash       VARCHAR(255),
    ADD COLUMN login_token_expires_at TIMESTAMPTZ;

COMMENT ON COLUMN users.login_token_hash IS
    'SHA-256 of the single-use access-link token (15 min TTL); NULL when none is pending.';
COMMENT ON COLUMN users.login_token_expires_at IS
    'Expiry of login_token_hash; the link is also invalidated by its first use.';

CREATE INDEX idx_users_login_token_hash ON users (login_token_hash) WHERE login_token_hash IS NOT NULL;
