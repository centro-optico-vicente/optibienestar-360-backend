package com.fenixcore.optibienestar360.core.audit;

import com.fenixcore.optibienestar360.core.audit.repository.LoginAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Background expiry sweep for {@code login_audit_log} (spec 16-audit.md
 * §Login "Chequeo simple con is_valid") — the only place that compares
 * {@code session_expires_at} against {@code now()}; the hot-path check in
 * {@code JwtAuthenticationFilter} (via {@link LoginAuditService#isSessionValid})
 * only ever reads the {@code is_valid} boolean.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginSessionSweepJob {

    private final LoginAuditLogRepository loginAuditLogRepository;

    @Scheduled(fixedDelayString = "${audit.login-session-sweep.fixed-delay-ms:300000}")
    @Transactional
    public void sweepExpiredSessions() {
        try {
            int expired = loginAuditLogRepository.expireStaleSessions(Instant.now());
            if (expired > 0) {
                log.info("Expired {} stale login session(s)", expired);
            }
        } catch (Exception ex) {
            log.warn("Login session sweep failed — will retry on next tick", ex);
        }
    }
}
