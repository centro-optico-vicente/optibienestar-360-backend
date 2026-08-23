package com.fenixcore.optibienestar360.core.audit;

/** Matches {@code chk_login_audit_result} (V61). */
public enum LoginAuditResult {
    SUCCESS,
    FAILED_CREDENTIALS,
    FAILED_LOCKED,
    FAILED_INACTIVE
}
