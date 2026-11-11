package com.fenixcore.optibienestar360.modules.auth.service;

import com.fenixcore.optibienestar360.common.service.EmailService;
import com.fenixcore.optibienestar360.core.audit.LoginAuditResult;
import com.fenixcore.optibienestar360.core.audit.LoginAuditService;
import com.fenixcore.optibienestar360.core.exception.AuthenticationException;
import com.fenixcore.optibienestar360.modules.auth.dto.LoginRequest;
import com.fenixcore.optibienestar360.modules.auth.dto.LoginResponse;
import com.fenixcore.optibienestar360.modules.auth.dto.MagicLinkConsumeRequest;
import com.fenixcore.optibienestar360.modules.auth.dto.MagicLinkRequest;
import com.fenixcore.optibienestar360.modules.auth.dto.RecoverPasswordRequest;
import com.fenixcore.optibienestar360.modules.auth.dto.UserDto;
import com.fenixcore.optibienestar360.modules.auth.entity.Role;
import com.fenixcore.optibienestar360.modules.auth.entity.SecurityPolicy;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.entity.UserRole;
import com.fenixcore.optibienestar360.modules.auth.mapper.UserMapper;
import com.fenixcore.optibienestar360.modules.auth.repository.SecurityPolicyRepository;
import com.fenixcore.optibienestar360.modules.auth.repository.UserPasswordHistoryRepository;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.auth.repository.UserSessionLogRepository;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import com.fenixcore.optibienestar360.security.PermissionResolver;
import com.fenixcore.optibienestar360.security.jwt.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Passwordless access (hub ADR 0037 §4): login and recovery by email or
 * national ID, and the single-use 15-minute access link.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceAccessTest {

    private static final String FRONTEND_URL = "https://app.example.test";

    @Mock private UserRepository userRepository;
    @Mock private SecurityPolicyRepository securityPolicyRepository;
    @Mock private UserPasswordHistoryRepository passwordHistoryRepository;
    @Mock private UserSessionLogRepository sessionLogRepository;
    @Mock private JwtService jwtService;
    @Mock private PermissionResolver permissionResolver;
    @Mock private TokenBlacklistService blacklistService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private UserMapper userMapper;
    @Mock private EmailService emailService;
    @Mock private MessageSource messageSource;
    @Mock private LoginAuditService loginAuditService;
    @Mock private HttpServletRequest httpRequest;

    private AuthService authService;
    private User user;
    private Role role;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, securityPolicyRepository, passwordHistoryRepository,
                sessionLogRepository, jwtService, permissionResolver, blacklistService, passwordEncoder, userMapper,
                emailService, messageSource, loginAuditService);
        ReflectionTestUtils.setField(authService, "frontendUrl", FRONTEND_URL);
        ReflectionTestUtils.setField(authService, "accessExpirationMinutes", 15);
        ReflectionTestUtils.setField(authService, "refreshExpirationDays", 30);

        role = new Role();
        role.setId(2L);
        role.setUuid(UUID.randomUUID());
        role.setName("AFILIADO");

        Person person = new Person();
        person.setLocale("es-VE");
        person.setFullName("Ana Pérez");

        user = new User();
        user.setUuid(UUID.randomUUID());
        user.setId(10L);
        user.setEmail("ana@example.test");
        user.setPasswordHash("hash");
        user.setPerson(person);
        UserRole assignment = new UserRole();
        assignment.setRole(role);
        assignment.setActive(true);
        user.setUserRoles(new ArrayList<>(List.of(assignment)));
    }

    private User other() {
        User u = new User();
        u.setUuid(UUID.randomUUID());
        u.setId(99L);
        u.setEmail("other@example.test");
        return u;
    }

    // ─── Identifier resolution ──────────────────────────────────────────────

    @Test
    void anEmail_isLookedUpByEmail() {
        when(userRepository.findByEmail("ana@example.test")).thenReturn(Optional.of(user));

        assertThat(authService.resolveAccount(" ana@example.test ")).contains(user);
    }

    @Test
    void aNationalIdWithItsLetter_isLookedUpWithThatLetterOnly() {
        when(userRepository.findByPerson_DocumentTypeAndPerson_DocumentNumber("V", "12345678"))
                .thenReturn(Optional.of(user));

        assertThat(authService.resolveAccount("V12345678")).contains(user);
        assertThat(authService.resolveAccount("v-12.345.678")).contains(user);
        verify(userRepository, never())
                .findByPerson_DocumentTypeAndPerson_DocumentNumber(eq("E"), anyString());
    }

    @Test
    void aNationalIdWithoutLetter_triesVThenE() {
        when(userRepository.findByPerson_DocumentTypeAndPerson_DocumentNumber("V", "12345678"))
                .thenReturn(Optional.empty());
        when(userRepository.findByPerson_DocumentTypeAndPerson_DocumentNumber("E", "12345678"))
                .thenReturn(Optional.of(user));

        assertThat(authService.resolveAccount("12345678")).contains(user);
    }

    @Test
    void aNationalIdMatchingTwoAccounts_isAmbiguousAndResolvesToNothing() {
        when(userRepository.findByPerson_DocumentTypeAndPerson_DocumentNumber("V", "12345678"))
                .thenReturn(Optional.of(user));
        when(userRepository.findByPerson_DocumentTypeAndPerson_DocumentNumber("E", "12345678"))
                .thenReturn(Optional.of(other()));

        assertThat(authService.resolveAccount("12345678")).isEmpty();
    }

    @Test
    void junkAndBlankIdentifiers_resolveToNothingWithoutTouchingTheDatabase() {
        assertThat(authService.resolveAccount("not an id")).isEmpty();
        assertThat(authService.resolveAccount("   ")).isEmpty();
        assertThat(authService.resolveAccount(null)).isEmpty();

        verify(userRepository, never()).findByEmail(anyString());
    }

    // ─── Login by identifier ────────────────────────────────────────────────

    @Test
    void loginByNationalId_withAWrongPassword_failsGenericallyAndAuditsTheIdentifier() {
        when(userRepository.findByPerson_DocumentTypeAndPerson_DocumentNumber("V", "12345678"))
                .thenReturn(Optional.of(user));
        when(securityPolicyRepository.findFirstByActiveTrue()).thenReturn(Optional.of(new SecurityPolicy()));
        when(passwordEncoder.matches("bad", "hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("V12345678", null, "bad"), httpRequest))
                .isInstanceOf(AuthenticationException.class);

        verify(loginAuditService).recordFailure(eq("V12345678"), eq(10L), eq(LoginAuditResult.FAILED_CREDENTIALS),
                eq("bad_password"), any(), any(), any());
    }

    @Test
    void loginWithTheLegacyEmailField_stillWorks() {
        when(userRepository.findByEmail("nobody@example.test")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest(null, "nobody@example.test", "x"), httpRequest))
                .isInstanceOf(AuthenticationException.class);

        verify(loginAuditService).recordFailure(eq("nobody@example.test"), isNull(),
                eq(LoginAuditResult.FAILED_CREDENTIALS), eq("account_not_found"), any(), any(), any());
    }

    @Test
    void loginByNationalId_withTheRightPassword_issuesTheSession() {
        stubSessionIssue();
        when(userRepository.findByPerson_DocumentTypeAndPerson_DocumentNumber("V", "12345678"))
                .thenReturn(Optional.of(user));
        when(securityPolicyRepository.findFirstByActiveTrue()).thenReturn(Optional.of(new SecurityPolicy()));
        when(passwordEncoder.matches("good", "hash")).thenReturn(true);

        LoginResponse response = authService.login(new LoginRequest("V12345678", null, "good"), httpRequest);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(user.getLastLoginAt()).isNotNull();
    }

    // ─── Recovery by identifier ─────────────────────────────────────────────

    @Test
    void recoveryByNationalId_emailsTheResetLinkToTheAccount() {
        when(userRepository.findByPerson_DocumentTypeAndPerson_DocumentNumber("V", "12345678"))
                .thenReturn(Optional.of(user));
        when(messageSource.getMessage(eq("email.recovery.subject"), isNull(), any(Locale.class))).thenReturn("s");

        authService.recoverPassword(new RecoverPasswordRequest("V12345678", null));

        verify(emailService).sendTemplated(eq("ana@example.test"), eq("s"), eq("password-recovery"),
                any(Locale.class), any());
        assertThat(user.getPasswordResetToken()).isNotBlank();
    }

    @Test
    void recoveryForAnUnknownNationalId_sendsNothing() {
        when(userRepository.findByPerson_DocumentTypeAndPerson_DocumentNumber(anyString(), anyString()))
                .thenReturn(Optional.empty());

        authService.recoverPassword(new RecoverPasswordRequest("12345678", null));

        verify(emailService, never()).sendTemplated(anyString(), anyString(), anyString(), any(), any());
    }

    // ─── Access link ────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private String requestLinkAndCaptureToken() {
        when(userRepository.findByEmail("ana@example.test")).thenReturn(Optional.of(user));
        when(messageSource.getMessage(eq("email.magic_link.subject"), isNull(), any(Locale.class))).thenReturn("s");

        authService.requestMagicLink(new MagicLinkRequest("ana@example.test"));

        ArgumentCaptor<Map<String, Object>> model = ArgumentCaptor.forClass(Map.class);
        verify(emailService).sendTemplated(eq("ana@example.test"), eq("s"), eq("magic-link"), any(Locale.class),
                model.capture());
        String url = (String) model.getValue().get("loginUrl");
        assertThat(url).startsWith(FRONTEND_URL + "/acceso?token=");
        assertThat(model.getValue().get("expiresInMinutes")).isEqualTo(15L);
        return url.substring(url.indexOf("token=") + "token=".length());
    }

    @Test
    void requestingALink_storesOnlyTheHashWithA15MinuteExpiry() {
        Instant before = Instant.now();

        String token = requestLinkAndCaptureToken();

        assertThat(user.getLoginTokenHash()).isNotBlank().isNotEqualTo(token).hasSize(64);
        assertThat(user.getLoginTokenExpiresAt())
                .isAfter(before.plus(Duration.ofMinutes(14)))
                .isBefore(Instant.now().plus(Duration.ofMinutes(16)));
        // It is a different credential from the password reset token.
        assertThat(user.getPasswordResetToken()).isNull();
        verify(userRepository).save(user);
    }

    @Test
    void requestingALinkForAnUnknownAccount_doesNothing() {
        when(userRepository.findByEmail("nobody@example.test")).thenReturn(Optional.empty());

        authService.requestMagicLink(new MagicLinkRequest("nobody@example.test"));

        verify(emailService, never()).sendTemplated(anyString(), anyString(), anyString(), any(), any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void requestingALinkForAnInactiveAccount_doesNothing() {
        user.setActive(false);
        when(userRepository.findByEmail("ana@example.test")).thenReturn(Optional.of(user));

        authService.requestMagicLink(new MagicLinkRequest("ana@example.test"));

        verify(emailService, never()).sendTemplated(anyString(), anyString(), anyString(), any(), any());
        assertThat(user.getLoginTokenHash()).isNull();
    }

    @Test
    void askingTwiceWithinTheCooldown_sendsOnlyOneEmail() {
        requestLinkAndCaptureToken();
        String firstHash = user.getLoginTokenHash();

        authService.requestMagicLink(new MagicLinkRequest("ana@example.test"));

        verify(emailService).sendTemplated(anyString(), anyString(), anyString(), any(), any());
        assertThat(user.getLoginTokenHash()).isEqualTo(firstHash);
    }

    @Test
    void aLink_worksOnce_andThenIsGone() {
        String token = requestLinkAndCaptureToken();
        String hash = user.getLoginTokenHash();
        stubSessionIssue();
        when(userRepository.findByLoginTokenHash(hash)).thenReturn(Optional.of(user));

        LoginResponse response = authService.consumeMagicLink(new MagicLinkConsumeRequest(token), httpRequest);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(user.getLoginTokenHash()).isNull();
        assertThat(user.getLoginTokenExpiresAt()).isNull();

        // The row no longer carries the hash, so a second use finds nothing.
        when(userRepository.findByLoginTokenHash(hash)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> authService.consumeMagicLink(new MagicLinkConsumeRequest(token), httpRequest))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void anExpiredLink_isRejectedAndCleared() {
        user.setLoginTokenHash("h");
        user.setLoginTokenExpiresAt(Instant.now().minusSeconds(1));
        when(userRepository.findByLoginTokenHash(anyString())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.consumeMagicLink(new MagicLinkConsumeRequest("raw"), httpRequest))
                .isInstanceOf(AuthenticationException.class);

        assertThat(user.getLoginTokenHash()).isNull();
        verify(jwtService, never()).generateAccessToken(anyString(), any(), any(), any(), any(), any());
    }

    @Test
    void anUnknownToken_isRejected() {
        when(userRepository.findByLoginTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.consumeMagicLink(new MagicLinkConsumeRequest("raw"), httpRequest))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void aPasswordResetToken_isNotAnAccessLink() {
        // Reset tokens live in other columns: looking one up as a login token finds nothing.
        user.setPasswordResetToken("reset-hash");
        when(userRepository.findByLoginTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.consumeMagicLink(new MagicLinkConsumeRequest("reset-raw"), httpRequest))
                .isInstanceOf(AuthenticationException.class);
        assertThat(user.getPasswordResetToken()).isEqualTo("reset-hash");
    }

    private void stubSessionIssue() {
        UserRole assignment = user.getUserRoles().get(0);
        when(permissionResolver.resolveDefaultActiveRole(user)).thenReturn(assignment);
        when(permissionResolver.resolvePermissionNamesForActiveRole(user, role.getUuid()))
                .thenReturn(List.of("MEMBER_VIEW_OWN"));
        when(loginAuditService.startSession(anyLong(), anyString(), any(), any(), any(), any(), any(),
                anyInt(), any())).thenReturn(Optional.of(UUID.randomUUID()));
        when(jwtService.generateAccessToken(anyString(), any(), any(), any(), any(), anyString()))
                .thenReturn("access-token");
        when(jwtService.generateRefreshToken(anyString(), any(), any(), any())).thenReturn("refresh-token");
        when(jwtService.extractJti("access-token")).thenReturn("access-jti");
        when(jwtService.extractJti("refresh-token")).thenReturn("refresh-jti");
        when(userMapper.toDto(user)).thenReturn(mock(UserDto.class));
    }
}
