package com.fenixcore.optibienestar360.modules.auth.service;

import com.fenixcore.optibienestar360.common.service.EmailService;
import com.fenixcore.optibienestar360.core.audit.LoginAuditService;
import com.fenixcore.optibienestar360.modules.auth.dto.RecoverPasswordRequest;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.mapper.UserMapper;
import com.fenixcore.optibienestar360.modules.auth.repository.SecurityPolicyRepository;
import com.fenixcore.optibienestar360.modules.auth.repository.UserPasswordHistoryRepository;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.auth.repository.UserSessionLogRepository;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import com.fenixcore.optibienestar360.security.PermissionResolver;
import com.fenixcore.optibienestar360.security.jwt.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AuthService#recoverPassword} — the recovery email carries both the
 * raw token (fallback to paste by hand) and a {@code resetUrl} that opens the
 * frontend reset page in the recipient's language with that same token.
 */
@ExtendWith(MockitoExtension.class)
class AuthServicePasswordRecoveryTest {

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

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, securityPolicyRepository, passwordHistoryRepository,
                sessionLogRepository, jwtService, permissionResolver, blacklistService, passwordEncoder, userMapper,
                emailService, messageSource, loginAuditService);
        // Trailing slash on purpose: the service must not produce "//restablecer-clave".
        ReflectionTestUtils.setField(authService, "frontendUrl", FRONTEND_URL + "/");
    }

    private User userWithLocale(String localeTag) {
        Person person = new Person();
        person.setLocale(localeTag);
        person.setFullName("Ana Pérez");

        User user = new User();
        user.setUuid(UUID.randomUUID());
        user.setId(10L);
        user.setEmail("ana@example.test");
        user.setPerson(person);
        return user;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sendAndCaptureModel(User user) {
        when(userRepository.findByEmailAndActiveTrue(user.getEmail())).thenReturn(Optional.of(user));
        when(messageSource.getMessage(eq("email.recovery.subject"), isNull(), any(Locale.class)))
                .thenReturn("subject");

        authService.recoverPassword(new RecoverPasswordRequest(user.getEmail()));

        ArgumentCaptor<Map<String, Object>> model = ArgumentCaptor.forClass(Map.class);
        verify(emailService).sendTemplated(eq(user.getEmail()), eq("subject"), eq("password-recovery"),
                any(Locale.class), model.capture());
        return model.getValue();
    }

    @Test
    void spanishRecipient_getsSpanishResetUrlWithTheSameToken() {
        Map<String, Object> model = sendAndCaptureModel(userWithLocale("es-VE"));

        String token = (String) model.get("token");
        assertThat(token).isNotBlank();
        assertThat(model.get("resetUrl"))
                .isEqualTo(FRONTEND_URL + "/restablecer-clave?token=" + token);
        assertThat(model.get("fullName")).isEqualTo("Ana Pérez");
    }

    @Test
    void englishRecipient_getsEnglishResetUrlWithTheSameToken() {
        Map<String, Object> model = sendAndCaptureModel(userWithLocale("en"));

        String token = (String) model.get("token");
        assertThat(token).isNotBlank();
        assertThat(model.get("resetUrl"))
                .isEqualTo(FRONTEND_URL + "/en/reset-password?token=" + token);
    }

    @Test
    void recipientWithoutLocale_fallsBackToSpanishResetUrl() {
        Map<String, Object> model = sendAndCaptureModel(userWithLocale(null));

        assertThat((String) model.get("resetUrl"))
                .startsWith(FRONTEND_URL + "/restablecer-clave?token=");
    }

    @Test
    void unknownEmail_sendsNothing() {
        when(userRepository.findByEmailAndActiveTrue("nobody@example.test")).thenReturn(Optional.empty());

        authService.recoverPassword(new RecoverPasswordRequest("nobody@example.test"));

        verify(emailService, never()).sendTemplated(anyString(), anyString(), anyString(), any(), any());
    }

    // ─── Password setup link (assisted affiliation approved without password) ───

    @Test
    void passwordSetupLink_storesOnlyTheHashWithTheGivenTtl_andLinksTheResetPage() {
        User user = userWithLocale("es-VE");
        java.time.Instant before = java.time.Instant.now();

        String url = authService.issuePasswordSetupLink(user, java.time.Duration.ofHours(72), Locale.forLanguageTag("es"));

        assertThat(url).startsWith(FRONTEND_URL + "/restablecer-clave?token=");
        String token = url.substring(url.indexOf("token=") + "token=".length());
        assertThat(user.getPasswordResetToken()).isNotBlank().isNotEqualTo(token).hasSize(64);
        assertThat(user.getPasswordResetExpiresAt())
                .isAfter(before.plus(java.time.Duration.ofHours(71)))
                .isBefore(java.time.Instant.now().plus(java.time.Duration.ofHours(73)));
        verify(userRepository).save(user);
        verify(emailService, never()).sendTemplated(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void passwordSetupLink_inEnglish_usesTheEnglishResetPage() {
        String url = authService.issuePasswordSetupLink(userWithLocale(null), java.time.Duration.ofHours(1),
                Locale.ENGLISH);

        assertThat(url).startsWith(FRONTEND_URL + "/en/reset-password?token=");
    }
}
