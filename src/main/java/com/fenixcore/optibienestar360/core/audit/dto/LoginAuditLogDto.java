package com.fenixcore.optibienestar360.core.audit.dto;

import com.fenixcore.optibienestar360.core.audit.LoginAuditResult;
import com.fenixcore.optibienestar360.core.audit.LoginSessionStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read model for one {@code login_audit_log} row (GET /v1/admin/audit/logins).
 * {@code userId} (internal BIGINT) never crosses the API boundary (ADR 0006)
 * — {@code userUuid} is resolved from it at read time.
 */
public record LoginAuditLogDto(
	UUID uuid,
	UUID userUuid,
	String attemptedEmail,
	LoginAuditResult result,
	String result_Display,
	List<String> roles,
	String locale,
	String ipAddress,
	String userAgent,
	String hostname,
	String failureReason,
	LoginSessionStatus sessionStatus,
	Instant sessionExpiresAt,
	boolean valid,
	Instant loggedOutAt,
	String logoutReason,
	Instant attemptedAt
) {}
