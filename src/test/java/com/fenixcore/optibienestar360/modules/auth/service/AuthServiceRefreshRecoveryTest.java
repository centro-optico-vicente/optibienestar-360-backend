package com.fenixcore.optibienestar360.modules.auth.service;

import com.fenixcore.optibienestar360.common.service.EmailService;
import com.fenixcore.optibienestar360.core.audit.LoginAuditService;
import com.fenixcore.optibienestar360.core.exception.AuthenticationException;
import com.fenixcore.optibienestar360.modules.auth.dto.RefreshRequest;
import com.fenixcore.optibienestar360.modules.auth.mapper.UserMapper;
import com.fenixcore.optibienestar360.modules.auth.repository.SecurityPolicyRepository;
import com.fenixcore.optibienestar360.modules.auth.repository.UserPasswordHistoryRepository;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.security.PermissionResolver;
import com.fenixcore.optibienestar360.security.jwt.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * With the volatile ({@code memory}) token store a restart forgets every refresh token. A token
 * issued before the store started is then checked against its persisted session instead of being
 * rejected outright — once per session, and never for tokens issued after the start.
 *
 * <p>The happy path is asserted by the request getting PAST the token check (it then fails on the
 * stubbed-out user lookup with {@code auth.user.not_found}); every rejection fails earlier with
 * {@code auth.token.refresh.invalid}.</p>
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceRefreshRecoveryTest {

    private static final String TOKEN = "refresh-token";
    private static final String JTI = "refresh-jti";
    private static final Instant STARTED_AT = Instant.parse("2026-11-11T21:25:00Z");
    private static final long BEFORE_START = STARTED_AT.getEpochSecond() - 600;

    @Mock private UserRepository userRepository;
    @Mock private SecurityPolicyRepository securityPolicyRepository;
    @Mock private UserPasswordHistoryRepository passwordHistoryRepository;
    @Mock private JwtService jwtService;
    @Mock private PermissionResolver permissionResolver;
    @Mock private TokenBlacklistService blacklistService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private UserMapper userMapper;
    @Mock private EmailService emailService;
    @Mock private MessageSource messageSource;
    @Mock private LoginAuditService loginAuditService;

    private AuthService authService;
    private final UUID sessionId = UUID.randomUUID();
    private final UUID userUuid = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, securityPolicyRepository, passwordHistoryRepository,
                jwtService, permissionResolver, blacklistService, passwordEncoder, userMapper,
                emailService, messageSource, loginAuditService);
        ReflectionTestUtils.setField(authService, "accessExpirationMinutes", 15);
        ReflectionTestUtils.setField(authService, "refreshExpirationDays", 30);

        when(jwtService.isValid(TOKEN)).thenReturn(true);
        when(jwtService.isAccessToken(TOKEN)).thenReturn(false);
        when(jwtService.extractJti(TOKEN)).thenReturn(JTI);
        when(blacklistService.isValidRefreshToken(JTI)).thenReturn(false);
    }

    private void volatileStore() {
        when(blacklistService.isVolatile()).thenReturn(true);
        when(blacklistService.startedAt()).thenReturn(STARTED_AT);
    }

    private void passesTokenCheck() {
        when(jwtService.extractSubject(TOKEN)).thenReturn(userUuid.toString());
        when(userRepository.findWithRolesByUuid(userUuid)).thenReturn(Optional.empty());
    }

    @Test
    void preRestartTokenOfAnActiveSession_isRecovered() {
        volatileStore();
        when(jwtService.extractIssuedAt(TOKEN)).thenReturn(BEFORE_START);
        when(jwtService.extractSessionId(TOKEN)).thenReturn(sessionId);
        when(loginAuditService.isSessionActive(sessionId)).thenReturn(true);
        when(blacklistService.markSessionRecovered(sessionId)).thenReturn(true);
        passesTokenCheck();

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(TOKEN)))
                .isInstanceOf(AuthenticationException.class)
                .extracting(e -> ((AuthenticationException) e).getMessageCode())
                .isEqualTo("auth.user.not_found");
    }

    @Test
    void secondPreRestartTokenOfTheSameSession_isRejected() {
        volatileStore();
        when(jwtService.extractIssuedAt(TOKEN)).thenReturn(BEFORE_START);
        when(jwtService.extractSessionId(TOKEN)).thenReturn(sessionId);
        when(loginAuditService.isSessionActive(sessionId)).thenReturn(true);
        when(blacklistService.markSessionRecovered(sessionId)).thenReturn(false);

        assertRejected();
    }

    @Test
    void tokenIssuedAfterTheStart_isRejectedWithoutTouchingTheSession() {
        volatileStore();
        when(jwtService.extractIssuedAt(TOKEN)).thenReturn(STARTED_AT.getEpochSecond());

        assertRejected();
        verify(loginAuditService, never()).isSessionActive(any());
    }

    @Test
    void sessionClosedOrExpired_isRejected() {
        volatileStore();
        when(jwtService.extractIssuedAt(TOKEN)).thenReturn(BEFORE_START);
        when(jwtService.extractSessionId(TOKEN)).thenReturn(sessionId);
        when(loginAuditService.isSessionActive(sessionId)).thenReturn(false);

        assertRejected();
        verify(blacklistService, never()).markSessionRecovered(any());
    }

    @Test
    void tokenWithoutSession_isRejected() {
        volatileStore();
        when(jwtService.extractIssuedAt(TOKEN)).thenReturn(BEFORE_START);
        when(jwtService.extractSessionId(TOKEN)).thenReturn(null);

        assertRejected();
    }

    @Test
    void durableStore_neverRecovers() {
        when(blacklistService.isVolatile()).thenReturn(false);

        assertRejected();
        verify(jwtService, never()).extractIssuedAt(any());
    }

    private void assertRejected() {
        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(TOKEN)))
                .isInstanceOf(AuthenticationException.class)
                .extracting(e -> ((AuthenticationException) e).getMessageCode())
                .isEqualTo("auth.token.refresh.invalid");
    }
}
