package com.fenixcore.optibienestar360.modules.auth.service;

import com.fenixcore.optibienestar360.common.service.EmailService;
import com.fenixcore.optibienestar360.core.audit.LoginAuditService;
import com.fenixcore.optibienestar360.modules.auth.dto.ResetPasswordRequest;
import com.fenixcore.optibienestar360.modules.auth.entity.SecurityPolicy;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.entity.UserPasswordHistory;
import com.fenixcore.optibienestar360.modules.auth.mapper.UserMapper;
import com.fenixcore.optibienestar360.modules.auth.repository.SecurityPolicyRepository;
import com.fenixcore.optibienestar360.modules.auth.repository.UserPasswordHistoryRepository;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.auth.repository.UserSessionLogRepository;
import com.fenixcore.optibienestar360.security.PermissionResolver;
import com.fenixcore.optibienestar360.security.jwt.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Password-reuse check of {@link AuthService} (exercised through
 * {@link AuthService#resetPassword}): the history lookup is bounded in SQL to
 * the policy's {@code passwordHistoryCount} instead of loading every hash the
 * user ever had, and a count of 0 skips the lookup altogether.
 */
@ExtendWith(MockitoExtension.class)
class AuthServicePasswordHistoryTest {

    private static final String RAW_TOKEN = "raw-reset-token";
    private static final String NEW_PASSWORD = "N3wPassword!";

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
    private User user;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, securityPolicyRepository, passwordHistoryRepository,
                sessionLogRepository, jwtService, permissionResolver, blacklistService, passwordEncoder, userMapper,
                emailService, messageSource, loginAuditService);

        user = new User();
        user.setId(10L);
        user.setUuid(UUID.randomUUID());
        user.setPasswordResetExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));
        when(userRepository.findByPasswordResetToken(anyString())).thenReturn(Optional.of(user));
    }

    private void policyWithHistoryCount(int count) {
        SecurityPolicy policy = new SecurityPolicy();
        policy.setPasswordHistoryCount(count);
        when(securityPolicyRepository.findFirstByActiveTrue()).thenReturn(Optional.of(policy));
    }

    @Test
    void historyLookupIsLimitedToThePolicyCount() {
        policyWithHistoryCount(3);
        when(passwordHistoryRepository.findRecentByUserId(eq(10L), any(Pageable.class))).thenReturn(List.of());
        when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn("new-hash");

        authService.resetPassword(new ResetPasswordRequest(RAW_TOKEN, NEW_PASSWORD));

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(passwordHistoryRepository).findRecentByUserId(eq(10L), page.capture());
        assertThat(page.getValue().getPageNumber()).isZero();
        assertThat(page.getValue().getPageSize()).isEqualTo(3);
        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
    }

    @Test
    void reusingARecentPasswordIsRejected() {
        policyWithHistoryCount(5);
        UserPasswordHistory previous = new UserPasswordHistory(user, "old-hash", null);
        when(passwordHistoryRepository.findRecentByUserId(eq(10L), any(Pageable.class)))
                .thenReturn(List.of(previous));
        when(passwordEncoder.matches(NEW_PASSWORD, "old-hash")).thenReturn(true);

        assertThatThrownBy(() -> authService.resetPassword(new ResetPasswordRequest(RAW_TOKEN, NEW_PASSWORD)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("auth.password.history.repeat");
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void zeroHistoryCountSkipsTheLookup() {
        policyWithHistoryCount(0);
        when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn("new-hash");

        authService.resetPassword(new ResetPasswordRequest(RAW_TOKEN, NEW_PASSWORD));

        verify(passwordHistoryRepository, never()).findRecentByUserId(anyLong(), any(Pageable.class));
        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
    }
}
