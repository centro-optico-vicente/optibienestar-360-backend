package com.fenixcore.optibienestar360.modules.auth.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Full replacement of the active security policy ({@code PUT
 * /v1/admin/security-policy}). Every field is required. The lower bounds are
 * mirrored by CHECK constraints on {@code security_policies} (V191):
 *
 * <ul>
 *   <li>{@code maxLoginAttempts >= 1} — {@code AuthService} locks the account
 *       once {@code failedAttempts >= maxLoginAttempts}; {@code 0} would lock
 *       on the very first failure, so it is not a "disabled" value.</li>
 *   <li>{@code lockoutDurationMinutes >= 1} — {@code 0} would set
 *       {@code lockedUntil = now}, silently turning the lockout off.</li>
 *   <li>{@code daysPasswordExpires >= 0} — {@code 0} means new passwords are
 *       stored with no expiry date ({@code AuthService} already treats
 *       {@code <= 0} as "never expires").</li>
 *   <li>{@code passwordHistoryCount >= 0} — {@code 0} disables the history
 *       check, so the same password can be reused consecutively.</li>
 *   <li>{@code maxConcurrentSessions >= 1} — a cap of zero sessions makes no
 *       sense. Note: the value is stored but not enforced at login yet.</li>
 * </ul>
 */
public record SecurityPolicyUpdateRequest(
        @NotNull @Min(1) Integer maxLoginAttempts,
        @NotNull @Min(1) Integer lockoutDurationMinutes,
        @NotNull @Min(0) Integer daysPasswordExpires,
        @NotNull @Min(0) Integer passwordHistoryCount,
        @NotNull @Min(1) Integer maxConcurrentSessions
) {}
