package com.fenixcore.optibienestar360.core.audit;

/** Matches {@code chk_login_audit_session_status} (V61) — only set for {@code SUCCESS} rows. */
public enum LoginSessionStatus {
    ACTIVE,
    LOGGED_OUT,
    EXPIRED,
    REVOKED
}
