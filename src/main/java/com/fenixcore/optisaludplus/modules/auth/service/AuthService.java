package com.fenixcore.optisaludplus.modules.auth.service;

import com.fenixcore.optisaludplus.common.service.EmailService;
import com.fenixcore.optisaludplus.core.exception.AccountLockedException;
import com.fenixcore.optisaludplus.core.exception.AuthenticationException;
import com.fenixcore.optisaludplus.modules.auth.dto.AccessTokenResponse;
import com.fenixcore.optisaludplus.modules.auth.dto.ChangePasswordRequest;
import com.fenixcore.optisaludplus.modules.auth.dto.LoginRequest;
import com.fenixcore.optisaludplus.modules.auth.dto.LoginResponse;
import com.fenixcore.optisaludplus.modules.auth.dto.LogoutRequest;
import com.fenixcore.optisaludplus.modules.auth.dto.RecoverPasswordRequest;
import com.fenixcore.optisaludplus.modules.auth.dto.RefreshRequest;
import com.fenixcore.optisaludplus.modules.auth.dto.ResetPasswordRequest;
import com.fenixcore.optisaludplus.modules.auth.entity.SecurityPolicy;
import com.fenixcore.optisaludplus.modules.auth.entity.User;
import com.fenixcore.optisaludplus.modules.auth.entity.UserPasswordHistory;
import com.fenixcore.optisaludplus.modules.auth.entity.UserSessionLog;
import com.fenixcore.optisaludplus.modules.auth.mapper.UserMapper;
import com.fenixcore.optisaludplus.modules.auth.repository.SecurityPolicyRepository;
import com.fenixcore.optisaludplus.modules.auth.repository.UserPasswordHistoryRepository;
import com.fenixcore.optisaludplus.modules.auth.repository.UserRepository;
import com.fenixcore.optisaludplus.modules.auth.repository.UserSessionLogRepository;
import com.fenixcore.optisaludplus.security.jwt.JwtService;
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
    private final TokenBlacklistService blacklistService;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final EmailService emailService;
    private final MessageSource messageSource;

    @Value("${jwt.access-expiration-minutes:15}")
    private int accessExpirationMinutes;

    @Value("${jwt.refresh-expiration-days:30}")
    private int refreshExpirationDays;

    // ─── Login ────────────────────────────────────────────────────────────────

    @Transactional
    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        User user = userRepository.findByEmailAndActiveTrue(request.email())
                .orElseThrow(() -> new AuthenticationException(GENERIC_AUTH_ERROR));

        checkAccountNotLocked(user);

        SecurityPolicy policy = activePolicy();

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            handleFailedAttempt(user, policy);
            throw new AuthenticationException(GENERIC_AUTH_ERROR);
        }

        resetFailedAttempts(user);

        List<String> permissions = collectPermissions(user);
        String subject = user.getUuid().toString();
        String userLocale = user.getLocale();
        String effectiveLocale = resolveEffectiveLocale(userLocale);
        String accessToken  = jwtService.generateAccessToken(subject, permissions, userLocale);
        String refreshToken = jwtService.generateRefreshToken(subject);

        String accessJti  = jwtService.extractJti(accessToken);
        String refreshJti = jwtService.extractJti(refreshToken);

        long refreshTtlSeconds = (long) refreshExpirationDays * 24 * 60 * 60;
        blacklistService.storeRefreshToken(refreshJti, subject, refreshTtlSeconds);

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

        List<String> permissions = collectPermissions(user);
        String newAccessToken  = jwtService.generateAccessToken(subject, permissions, user.getLocale());
        String newRefreshToken = jwtService.generateRefreshToken(subject);

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
            Locale recipientLocale = resolveRecipientLocale(user.getLocale());
            String subject = messageSource.getMessage("email.recovery.subject", null, recipientLocale);

            emailService.sendTemplated(
                    user.getEmail(),
                    subject,
                    "password-recovery",
                    recipientLocale,
                    Map.of("fullName", user.getFullName(), "token", rawToken)
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
        User user = userRepository.findWithRolesByUuid(userUuid)
                .orElseThrow(() -> new AuthenticationException("auth.user.not_found"));

        user.setLocale(newLocale);
        userRepository.save(user);

        List<String> permissions = collectPermissions(user);
        String subject = user.getUuid().toString();
        String newAccessToken = jwtService.generateAccessToken(subject, permissions, newLocale);

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

    private void checkAccountNotLocked(User user) {
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            throw new AccountLockedException(user.getLockedUntil());
        }
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
        return user.getUserRoles().stream()
                .filter(ur -> ur.isActive()
                        && (ur.getExpiresAt() == null || ur.getExpiresAt().isAfter(Instant.now())))
                .flatMap(ur -> ur.getRole().getPermissions().stream())
                .map(p -> p.getName())
                .distinct()
                .toList();
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
