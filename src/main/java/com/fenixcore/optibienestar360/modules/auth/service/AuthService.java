package com.fenixcore.optibienestar360.modules.auth.service;

import com.fenixcore.optibienestar360.common.service.EmailService;
import com.fenixcore.optibienestar360.core.audit.LoginAuditResult;
import com.fenixcore.optibienestar360.core.audit.LoginAuditService;
import com.fenixcore.optibienestar360.core.exception.AccountLockedException;
import com.fenixcore.optibienestar360.core.exception.AuthenticationException;
import com.fenixcore.optibienestar360.modules.auth.dto.AccessTokenResponse;
import com.fenixcore.optibienestar360.modules.auth.dto.ChangePasswordRequest;
import com.fenixcore.optibienestar360.modules.auth.dto.LoginRequest;
import com.fenixcore.optibienestar360.modules.auth.dto.LoginResponse;
import com.fenixcore.optibienestar360.modules.auth.dto.LogoutRequest;
import com.fenixcore.optibienestar360.modules.auth.dto.RecoverPasswordRequest;
import com.fenixcore.optibienestar360.modules.auth.dto.RefreshRequest;
import com.fenixcore.optibienestar360.modules.auth.dto.ResetPasswordRequest;
import com.fenixcore.optibienestar360.modules.auth.entity.SecurityPolicy;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.entity.UserPasswordHistory;
import com.fenixcore.optibienestar360.modules.auth.entity.UserSessionLog;
import com.fenixcore.optibienestar360.modules.auth.mapper.UserMapper;
import com.fenixcore.optibienestar360.modules.auth.repository.SecurityPolicyRepository;
import com.fenixcore.optibienestar360.modules.auth.repository.UserPasswordHistoryRepository;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.auth.repository.UserSessionLogRepository;
import com.fenixcore.optibienestar360.security.PermissionResolver;
import com.fenixcore.optibienestar360.security.jwt.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String GENERIC_AUTH_ERROR = "auth.credentials.invalid";
    private static final Locale DEFAULT_RECIPIENT_LOCALE = Locale.forLanguageTag("es-VE");

    private final UserRepository userRepository;
    private final SecurityPolicyRepository securityPolicyRepository;
    private final UserPasswordHistoryRepository passwordHistoryRepository;
    private final UserSessionLogRepository sessionLogRepository;
    private final JwtService jwtService;
    private final PermissionResolver permissionResolver;
    private final TokenBlacklistService blacklistService;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final EmailService emailService;
    private final MessageSource messageSource;
    private final LoginAuditService loginAuditService;

    @Value("${jwt.access-expiration-minutes:15}")
    private int accessExpirationMinutes;

    @Value("${jwt.refresh-expiration-days:30}")
    private int refreshExpirationDays;

    // ─── Login ────────────────────────────────────────────────────────────────

    @Transactional
    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String ip = resolveClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        String hostname = httpRequest.getRemoteHost();

        // findByEmail (not findByEmailAndActiveTrue) so an inactive account can be
        // told apart from a nonexistent email for login_audit_log's FAILED_INACTIVE
        // (spec 16-audit.md §Login) — the response to the caller stays identical either way.
        User user = userRepository.findByEmail(request.email()).orElse(null);
        if (user == null) {
            loginAuditService.recordFailure(request.email(), null, LoginAuditResult.FAILED_CREDENTIALS,
                    "email_not_found", ip, userAgent, hostname);
            throw new AuthenticationException(GENERIC_AUTH_ERROR);
        }
        if (!user.isActive()) {
            loginAuditService.recordFailure(request.email(), user.getId(), LoginAuditResult.FAILED_INACTIVE,
                    "account_inactive", ip, userAgent, hostname);
            throw new AuthenticationException(GENERIC_AUTH_ERROR);
        }

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            loginAuditService.recordFailure(request.email(), user.getId(), LoginAuditResult.FAILED_LOCKED,
                    "account_locked", ip, userAgent, hostname);
            throw new AccountLockedException(user.getLockedUntil());
        }

        SecurityPolicy policy = activePolicy();

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            handleFailedAttempt(user, policy);
            loginAuditService.recordFailure(request.email(), user.getId(), LoginAuditResult.FAILED_CREDENTIALS,
                    "bad_password", ip, userAgent, hostname);
            throw new AuthenticationException(GENERIC_AUTH_ERROR);
        }

        resetFailedAttempts(user);

        List<String> permissions = collectPermissions(user);
        String subject = user.getUuid().toString();
        String userLocale = user.getPerson().getLocale();
        String effectiveLocale = resolveEffectiveLocale(userLocale);
        List<String> roleNames = user.getUserRoles().stream().map(ur -> ur.getRole().getName()).toList();

        // Inserted BEFORE the tokens so its uuid can be embedded as the sid claim
        // (spec §Login) — empty when login_audit_enabled=false, in which case the
        // tokens simply carry no sid (JwtAuthenticationFilter treats that as "no
        // session check to perform", never a rejection).
        Optional<UUID> sessionId = loginAuditService.startSession(user.getId(), user.getEmail(), roleNames,
                effectiveLocale, ip, userAgent, hostname, refreshExpirationDays);

        String accessToken  = jwtService.generateAccessToken(subject, permissions, userLocale, sessionId.orElse(null));
        String refreshToken = jwtService.generateRefreshToken(subject, sessionId.orElse(null));

        String accessJti  = jwtService.extractJti(accessToken);
        String refreshJti = jwtService.extractJti(refreshToken);

        long refreshTtlSeconds = (long) refreshExpirationDays * 24 * 60 * 60;
        blacklistService.storeRefreshToken(refreshJti, subject, refreshTtlSeconds);

        sessionId.ifPresent(sid -> loginAuditService.attachJti(sid, accessJti));

        logSession(user, accessJti, httpRequest, effectiveLocale);

        return LoginResponse.of(
                accessToken,
                refreshToken,
                (long) accessExpirationMinutes * 60,
                userMapper.toDto(user)
        );
    }

    // ─── Refresh ──────────────────────────────────────────────────────────────

    @Transactional
    public LoginResponse refresh(RefreshRequest request) {
        String token = request.refreshToken();

        if (!jwtService.isValid(token) || jwtService.isAccessToken(token)) {
            throw new AuthenticationException("auth.token.refresh.invalid");
        }

        String refreshJti = jwtService.extractJti(token);
        if (!blacklistService.isValidRefreshToken(refreshJti)) {
            throw new AuthenticationException("auth.token.refresh.invalid");
        }

        String subject    = jwtService.extractSubject(token);
        UUID userUuid     = UUID.fromString(subject);

        User user = userRepository.findWithRolesByUuid(userUuid)
                .filter(u -> u.isActive())
                .orElseThrow(() -> new AuthenticationException("auth.user.not_found"));

        blacklistService.revokeRefreshToken(refreshJti, subject);

        // Carries the same sid forward — a refresh reissues tokens for the same
        // login_audit_log session row, it doesn't start a new one.
        UUID sessionId = jwtService.extractSessionId(token);

        List<String> permissions = collectPermissions(user);
        String newAccessToken  = jwtService.generateAccessToken(subject, permissions, user.getPerson().getLocale(), sessionId);
        String newRefreshToken = jwtService.generateRefreshToken(subject, sessionId);

        String newAccessJti    = jwtService.extractJti(newAccessToken);
        if (sessionId != null) {
            loginAuditService.attachJti(sessionId, newAccessJti);
        }

        String newRefreshJti   = jwtService.extractJti(newRefreshToken);
        long refreshTtlSeconds = (long) refreshExpirationDays * 24 * 60 * 60;
        blacklistService.storeRefreshToken(newRefreshJti, subject, refreshTtlSeconds);

        return LoginResponse.of(
                newAccessToken,
                newRefreshToken,
                (long) accessExpirationMinutes * 60,
                userMapper.toDto(user)
        );
    }

    // ─── Logout ───────────────────────────────────────────────────────────────

    @Transactional
    public void logout(String accessToken, LogoutRequest request) {
        String jti = jwtService.extractJti(accessToken);
        long ttl   = jwtService.getRemainingTtlSeconds(accessToken);
        blacklistService.blacklistAccessToken(jti, ttl);

        loginAuditService.closeSession(jwtService.extractSessionId(accessToken), "user_logout");

        String subject = jwtService.extractSubject(accessToken);

        if (request != null && request.refreshToken() != null) {
            try {
                String refreshJti = jwtService.extractJti(request.refreshToken());
                blacklistService.revokeRefreshToken(refreshJti, subject);
            } catch (Exception e) {
                log.debug("Could not revoke refresh token during logout: {}", e.getMessage());
            }
        }

        sessionLogRepository.findByJtiAndLogoutAtIsNull(jti).ifPresent(session -> {
            session.setLogoutAt(Instant.now());
            session.setLogoutReason("user_logout");
            sessionLogRepository.save(session);
        });
    }

    // ─── Password Recovery ────────────────────────────────────────────────────

    @Transactional
    public void recoverPassword(RecoverPasswordRequest request) {
        userRepository.findByEmailAndActiveTrue(request.email()).ifPresent(user -> {
            String rawToken   = generateSecureToken();
            String hashedToken = sha256(rawToken);

            user.setPasswordResetToken(hashedToken);
            user.setPasswordResetExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));
            userRepository.save(user);

            // Recipient locale wins over request locale: the email goes to the
            // user, so it should match THEIR preference (admin-triggered flows
            // wouldn't honor the admin's Accept-Language for someone else).
            Locale recipientLocale = resolveRecipientLocale(user.getPerson().getLocale());
            String subject = messageSource.getMessage("email.recovery.subject", null, recipientLocale);

            emailService.sendTemplated(
                    user.getEmail(),
                    subject,
                    "password-recovery",
                    recipientLocale,
                    Map.of("fullName", user.getPerson().getFullName(), "token", rawToken)
            );
        });
        // Always return void (don't reveal if email exists)
    }

    private Locale resolveRecipientLocale(String userLocaleTag) {
        if (userLocaleTag == null || userLocaleTag.isBlank()) {
            return DEFAULT_RECIPIENT_LOCALE;
        }
        Locale parsed = Locale.forLanguageTag(userLocaleTag);
        return parsed.getLanguage().isEmpty() ? DEFAULT_RECIPIENT_LOCALE : parsed;
    }

    // ─── Reset Password ───────────────────────────────────────────────────────

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String hashedToken = sha256(request.token());

        User user = userRepository.findByPasswordResetToken(hashedToken)
                .orElseThrow(() -> new AuthenticationException("auth.token.invalid_or_expired"));

        if (user.getPasswordResetExpiresAt() == null
                || user.getPasswordResetExpiresAt().isBefore(Instant.now())) {
            throw new AuthenticationException("auth.token.invalid_or_expired");
        }

        SecurityPolicy policy = activePolicy();
        String newHash = hashNewPassword(user, request.newPassword(), policy);

        user.setPasswordHash(newHash);
        user.setPasswordResetToken(null);
        user.setPasswordResetExpiresAt(null);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        savePasswordHistory(user, newHash, policy);
        blacklistService.revokeAllUserRefreshTokens(user.getUuid().toString());
    }

    // ─── Change Password ──────────────────────────────────────────────────────

    @Transactional
    public void changePassword(ChangePasswordRequest request, UUID userUuid, String currentJti) {
        User user = userRepository.findWithRolesByUuid(userUuid)
                .orElseThrow(() -> new AuthenticationException("auth.user.not_found"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new AuthenticationException("auth.password.current.wrong");
        }

        SecurityPolicy policy = activePolicy();
        String newHash = hashNewPassword(user, request.newPassword(), policy);

        user.setPasswordHash(newHash);
        userRepository.save(user);

        savePasswordHistory(user, newHash, policy);
        // Blacklist current access token so it cannot be reused after password change
        if (currentJti != null) {
            blacklistService.blacklistAccessToken(currentJti, (long) accessExpirationMinutes * 60);
        }
        blacklistService.revokeAllUserRefreshTokens(userUuid.toString());
    }

    // ─── Locale preference (Option C: dedicated endpoint, immediate effect) ──

    /**
     * Updates the caller's locale preference and reissues an access token with
     * the new claim. The old access token is blacklisted so it cannot be used
     * with the stale claim — the refresh token stays valid (preference change
     * is not a security event).
     */
    @Transactional
    public AccessTokenResponse updateMyLocale(UUID userUuid, String newLocale, String currentJti) {
        return updateMyLocale(userUuid, newLocale, currentJti, null);
    }

    /** @param sessionId carried forward from the current token's {@code sid} claim (principal.getSessionId()) — same session, just a new locale claim. */
    @Transactional
    public AccessTokenResponse updateMyLocale(UUID userUuid, String newLocale, String currentJti, UUID sessionId) {
        User user = userRepository.findWithRolesByUuid(userUuid)
                .orElseThrow(() -> new AuthenticationException("auth.user.not_found"));

        user.getPerson().setLocale(newLocale);
        // No explicit save — managed entity → dirty-check on tx commit.

        List<String> permissions = collectPermissions(user);
        String subject = user.getUuid().toString();
        String newAccessToken = jwtService.generateAccessToken(subject, permissions, newLocale, sessionId);
        if (sessionId != null) {
            loginAuditService.attachJti(sessionId, jwtService.extractJti(newAccessToken));
        }

        if (currentJti != null) {
            blacklistService.blacklistAccessToken(currentJti, (long) accessExpirationMinutes * 60);
        }

        return AccessTokenResponse.of(
                newAccessToken,
                (long) accessExpirationMinutes * 60,
                userMapper.toDto(user)
        );
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private SecurityPolicy activePolicy() {
        return securityPolicyRepository.findFirstByActiveTrue()
                .orElseGet(SecurityPolicy::new);
    }

    private void handleFailedAttempt(User user, SecurityPolicy policy) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);

        if (attempts >= policy.getMaxLoginAttempts()) {
            user.setLockedUntil(Instant.now().plus(policy.getLockoutDurationMinutes(), ChronoUnit.MINUTES));
            log.warn("Account locked: {} (attempts={})", user.getEmail(), attempts);
        }
        userRepository.save(user);
    }

    private void resetFailedAttempts(User user) {
        if (user.getFailedLoginAttempts() > 0 || user.getLockedUntil() != null) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
        }
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
    }

    private List<String> collectPermissions(User user) {
        // Delegates to PermissionResolver so SYSTEM users receive the full
        // permission catalog (superuser) and this stays in sync with the
        // authority resolution in UserDetailsServiceImpl.
        return permissionResolver.resolvePermissionNames(user);
    }

    private void logSession(User user, String jti, HttpServletRequest req, String loginLocale) {
        String ip        = resolveClientIp(req);
        String userAgent = req.getHeader("User-Agent");
        sessionLogRepository.save(new UserSessionLog(user, jti, ip, userAgent, loginLocale));
    }

    /**
     * Effective locale at login time = preference if persisted, otherwise the
     * locale Spring resolved for this request (Accept-Language → app default).
     * Snapshot value persisted to user_sessions_log.login_locale.
     */
    private String resolveEffectiveLocale(String userLocale) {
        if (userLocale != null && !userLocale.isBlank()) {
            return userLocale;
        }
        return LocaleContextHolder.getLocale().toLanguageTag();
    }

    private String resolveClientIp(HttpServletRequest req) {
        // X-Forwarded-For is resolved by Jetty via server.forward-headers-strategy=NATIVE;
        // getRemoteAddr() returns the real client IP when behind Traefik.
        String ip = req.getRemoteAddr();
        if (ip == null) {
            return null;
        }
        // Some Servlet containers return IPv6 addresses wrapped in brackets
        // (e.g. "[0:0:0:0:0:0:0:1]" for ::1 loopback). Postgres `inet` rejects
        // that form, so strip the brackets before passing the value to
        // UserSessionLog.ip_address — otherwise the login session insert
        // throws 22P02 "invalid input syntax for type inet".
        if (ip.length() > 1 && ip.charAt(0) == '[' && ip.charAt(ip.length() - 1) == ']') {
            ip = ip.substring(1, ip.length() - 1);
        }
        return ip;
    }

    private String hashNewPassword(User user, String rawPassword, SecurityPolicy policy) {
        // Check password history
        List<UserPasswordHistory> history = passwordHistoryRepository.findRecentByUserId(user.getId());
        int limit = Math.min(history.size(), policy.getPasswordHistoryCount());
        for (int i = 0; i < limit; i++) {
            if (passwordEncoder.matches(rawPassword, history.get(i).getPasswordHash())) {
                // The historyCount is not embedded in the message because
                // i18n-resolved strings would have to carry it as a {0}
                // arg, and IllegalArgumentException doesn't model args.
                // The count still appears in logs via the stack trace and
                // the SecurityPolicy is the canonical place to look it up.
                throw new IllegalArgumentException("auth.password.history.repeat");
            }
        }
        return passwordEncoder.encode(rawPassword);
    }

    private void savePasswordHistory(User user, String passwordHash, SecurityPolicy policy) {
        Instant expiresAt = (user.isPasswordNeverExpires() || policy.getDaysPasswordExpires() <= 0)
                ? null
                : Instant.now().plus(policy.getDaysPasswordExpires(), ChronoUnit.DAYS);

        passwordHistoryRepository.save(new UserPasswordHistory(user, passwordHash, expiresAt));
        passwordHistoryRepository.pruneOlderThan(user.getId(), policy.getPasswordHistoryCount());
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

}
