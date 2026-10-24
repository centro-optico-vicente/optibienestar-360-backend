package com.fenixcore.optibienestar360.modules.auth.dto;

import java.util.UUID;

/**
 * Admin projection of the active {@code security_policies} row for
 * {@code GET/PUT /v1/admin/security-policy}. Plain integers only — no FK or
 * enum fields, so there is nothing to resolve under the {@code _Display}
 * convention (hub ADR 0014).
 *
 * <p>{@code uuid} is {@code null} only when no active row exists yet and the
 * values shown are the in-code defaults that {@code AuthService} falls back
 * to (see {@code SecurityPolicyService#get}).
 *
 * <p>Meaning of {@code 0} per field (see {@link SecurityPolicyUpdateRequest}
 * for the accepted ranges):
 * <ul>
 *   <li>{@code daysPasswordExpires = 0}: new passwords get no expiry date.</li>
 *   <li>{@code passwordHistoryCount = 0}: the history check is disabled — a
 *       user may reuse the same password consecutively.</li>
 * </ul>
 */
public record SecurityPolicyDto(
        UUID uuid,
        int maxLoginAttempts,
        int lockoutDurationMinutes,
        int daysPasswordExpires,
        int passwordHistoryCount,
        int maxConcurrentSessions
) {}
