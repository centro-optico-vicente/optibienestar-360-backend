SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V113: active-role sessions (role-switch feature).
--
-- `login_audit_log.roles` (V61) is a snapshot of ALL roles assigned at login
-- time — it can't tell which one was the session's active role once a user
-- can hold more than one. `active_role_id` records that specifically, for
-- both a normal login (active role = default/fallback role) and a
-- role-switch (active role = the target of the switch).
--
-- `user_sessions_log.logout_reason` (V5) has a restrictive CHECK that a
-- role-switch closing a session (reason 'role_switch') would violate —
-- widen it rather than reusing an existing unrelated reason.
-- ────────────────────────────────────────────────────────────────────────────

ALTER TABLE login_audit_log
    ADD COLUMN active_role_id BIGINT REFERENCES roles (roles_id);

ALTER TABLE user_sessions_log
    DROP CONSTRAINT IF EXISTS user_sessions_log_logout_reason_check;

ALTER TABLE user_sessions_log
    ADD CONSTRAINT user_sessions_log_logout_reason_check
        CHECK (logout_reason IN ('user_logout', 'token_expired', 'forced', 'role_switch'));
