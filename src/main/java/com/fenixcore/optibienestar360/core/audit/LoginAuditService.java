package com.fenixcore.optibienestar360.core.audit;

import com.fenixcore.optibienestar360.core.audit.entity.LoginAuditLog;
import com.fenixcore.optibienestar360.core.audit.repository.LoginAuditLogRepository;
import com.fenixcore.optibienestar360.modules.system.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Writer + hot-path reader for {@code login_audit_log} (V61, spec
 * 16-audit.md §Login). Called directly from {@code AuthService} — not
 * AOP-driven, since login isn't a per-entity CRUD method.
 *
 * <p>Fail-safe (Decisión 6): a write failure here is logged and swallowed,
 * never blocks login/logout. {@link #isSessionValid} is fail-open too — an
 * unresolvable {@code sid} (row missing, DB/cache error) doesn't reject the
 * request; the {@code jti} blacklist in {@code TokenBlacklistService}
 * remains the independent security guarantee (spec §Login).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAuditService {

    private final LoginAuditLogRepository loginAuditLogRepository;
    private final SystemConfigService systemConfigService;
    private final LoginSessionValidityCache loginSessionValidityCache;

    /** Failed attempt (bad credentials / locked / inactive account) — no session, no {@code sid}. */
    @Transactional
    public void recordFailure(String attemptedEmail, Long userId, LoginAuditResult result, String failureReason,
                               String ipAddress, String userAgent, String hostname) {
        if (!systemConfigService.isLoginAuditEnabled()) {
            return;
        }
        try {
            LoginAuditLog entry = new LoginAuditLog();
            entry.setUuid(UUID.randomUUID());
            entry.setUserId(userId);
            entry.setAttemptedEmail(attemptedEmail);
            entry.setResult(result);
            entry.setFailureReason(failureReason);
            entry.setIpAddress(ipAddress);
            entry.setUserAgent(userAgent);
            entry.setHostname(hostname);
            entry.setValid(false);
            loginAuditLogRepository.save(entry);
        } catch (Exception ex) {
            log.warn("Failed to record login failure for '{}' — login flow unaffected", attemptedEmail, ex);
        }
    }

    /**
     * Successful login — inserted BEFORE the JWT is generated (spec §Login)
     * so its {@code uuid} can be embedded as the {@code sid} claim.
     *
     * @return the new row's {@code uuid} to use as {@code sid}, or empty when
     *         {@code login_audit_enabled=false} — the caller must then issue
     *         the JWT with no {@code sid} claim (session tracking opted out).
     */
    @Transactional
    public Optional<UUID> startSession(Long userId, String email, List<String> roles, String locale,
                                        String ipAddress, String userAgent, String hostname,
                                        int refreshExpirationDays) {
        if (!systemConfigService.isLoginAuditEnabled()) {
            return Optional.empty();
        }
        try {
            LoginAuditLog entry = new LoginAuditLog();
            entry.setUuid(UUID.randomUUID());
            entry.setUserId(userId);
            entry.setAttemptedEmail(email);
            entry.setResult(LoginAuditResult.SUCCESS);
            entry.setRoles(roles);
            entry.setLocale(locale);
            entry.setIpAddress(ipAddress);
            entry.setUserAgent(userAgent);
            entry.setHostname(hostname);
            entry.setSessionStatus(LoginSessionStatus.ACTIVE);
            entry.setSessionExpiresAt(Instant.now().plus(refreshExpirationDays, ChronoUnit.DAYS));
            entry.setValid(true);
            loginAuditLogRepository.save(entry);
            return Optional.of(entry.getUuid());
        } catch (Exception ex) {
            log.warn("Failed to record login session for '{}' — login flow unaffected, no sid issued", email, ex);
            return Optional.empty();
        }
    }

    /** Sets the access token's {@code jti} once it's known — best-effort, after the row already exists. */
    @Transactional
    public void attachJti(UUID sessionId, String jti) {
        if (sessionId == null) {
            return;
        }
        try {
            loginAuditLogRepository.findByUuid(sessionId).ifPresent(entry -> entry.setJti(jti));
        } catch (Exception ex) {
            log.warn("Failed to attach jti to login session {}", sessionId, ex);
        }
    }

    /** Explicit logout (spec §Login "Chequeo simple con is_valid"). */
    @Transactional
    public void closeSession(UUID sessionId, String reason) {
        if (sessionId == null) {
            return;
        }
        try {
            loginAuditLogRepository.findByUuid(sessionId).ifPresent(entry -> {
                entry.setValid(false);
                entry.setSessionStatus(LoginSessionStatus.LOGGED_OUT);
                entry.setLoggedOutAt(Instant.now());
                entry.setLogoutReason(reason);
            });
            loginSessionValidityCache.evict(sessionId);
        } catch (Exception ex) {
            log.warn("Failed to close login session {}", sessionId, ex);
        }
    }

    /** Cached hot-path check used by {@code JwtAuthenticationFilter} — see class javadoc for the fail-open contract. */
    public boolean isSessionValid(UUID sessionId) {
        if (sessionId == null) {
            // No sid claim at all (login_audit_enabled was false when the token was
            // issued, or an older token predating this feature) — nothing to check.
            return true;
        }
        try {
            return loginSessionValidityCache.isValid(sessionId);
        } catch (Exception ex) {
            log.warn("Failed to resolve session validity for sid={} — failing open", sessionId, ex);
            return true;
        }
    }
}
