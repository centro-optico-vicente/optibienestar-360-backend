package com.fenixcore.optibienestar360.modules.auth.service;

import com.fenixcore.optibienestar360.common.service.EmailService;
import com.fenixcore.optibienestar360.core.audit.LoginAuditService;
import com.fenixcore.optibienestar360.modules.auth.dto.LoginResponse;
import com.fenixcore.optibienestar360.modules.auth.dto.UserDto;
import com.fenixcore.optibienestar360.modules.auth.entity.Permission;
import com.fenixcore.optibienestar360.modules.auth.entity.Role;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AuthService#switchActiveRole} — permissions are scoped to the
 * target role only (never a union), the old session is closed with reason
 * {@code "role_switch"}, and both tokens are rotated.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceRoleSwitchTest {

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
    private Role operadorRole;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, securityPolicyRepository, passwordHistoryRepository,
                sessionLogRepository, jwtService, permissionResolver, blacklistService, passwordEncoder, userMapper,
                emailService, messageSource, loginAuditService);
        ReflectionTestUtils.setField(authService, "accessExpirationMinutes", 15);
        ReflectionTestUtils.setField(authService, "refreshExpirationDays", 30);

        Role adminRole = new Role();
        adminRole.setId(1L);
        adminRole.setUuid(UUID.randomUUID());
        adminRole.setName("ADMINISTRADOR");
        adminRole.setPermissions(new HashSet<>(List.of(perm("USER_VIEW_ALL"))));

        operadorRole = new Role();
        operadorRole.setId(2L);
        operadorRole.setUuid(UUID.randomUUID());
        operadorRole.setName("OPERADOR");
        operadorRole.setPermissions(new HashSet<>(List.of(perm("PAYMENT_APPROVE"))));

        UserRole adminAssignment = new UserRole();
        adminAssignment.setRole(adminRole);
        adminAssignment.setActive(true);
        UserRole operadorAssignment = new UserRole();
        operadorAssignment.setRole(operadorRole);
        operadorAssignment.setActive(true);

        Person person = new Person();
        person.setLocale("es-VE");

        user = new User();
        user.setUuid(UUID.randomUUID());
        user.setId(10L);
        user.setEmail("user@example.test");
        user.setPerson(person);
        user.setUserRoles(new ArrayList<>(List.of(adminAssignment, operadorAssignment)));
    }

    @Test
    void switchingRole_scopesPermissionsToTheTargetRoleOnly() {
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(userRepository.findWithRolesByUuid(user.getUuid())).thenReturn(Optional.of(user));
        when(permissionResolver.resolvePermissionNamesForActiveRole(user, operadorRole.getUuid()))
                .thenReturn(List.of("PAYMENT_APPROVE"));
        when(jwtService.generateAccessToken(anyString(), any(), any(), any(), any(), anyString()))
                .thenReturn("new-access-token");
        when(jwtService.generateRefreshToken(anyString(), any())).thenReturn("new-refresh-token");
        when(jwtService.extractJti("new-access-token")).thenReturn("new-access-jti");
        when(jwtService.extractJti("new-refresh-token")).thenReturn("new-refresh-jti");
        when(jwtService.extractJti("old-refresh-token")).thenReturn("old-refresh-jti");
        when(loginAuditService.startSession(anyLong(), anyString(), any(), any(), any(), any(), any(),
                anyInt(), any())).thenReturn(Optional.of(UUID.randomUUID()));
        when(userMapper.toDto(user)).thenReturn(mock(UserDto.class));

        LoginResponse response = authService.switchActiveRole(user.getUuid(), operadorRole.getUuid(), "old-jti",
                UUID.randomUUID(), "old-refresh-token", httpRequest);

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isEqualTo("new-refresh-token");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> permissionsCaptor = ArgumentCaptor.forClass(List.class);
        verify(jwtService).generateAccessToken(anyString(), permissionsCaptor.capture(), any(), any(),
                eq(operadorRole.getUuid()), eq("OPERADOR"));
        // Scoped to OPERADOR only — never the union that would also include USER_VIEW_ALL.
        assertThat(permissionsCaptor.getValue()).containsExactly("PAYMENT_APPROVE");

        verify(loginAuditService).closeSession(any(), eq("role_switch"));
        verify(blacklistService).blacklistAccessToken(eq("old-jti"), anyLong());
        verify(blacklistService).revokeRefreshToken(eq("old-refresh-jti"), anyString());
    }

    @Test
    void switchingToARoleTheUserDoesNotHold_throwsAndLeavesTheOldSessionUntouched() {
        when(userRepository.findWithRolesByUuid(user.getUuid())).thenReturn(Optional.of(user));
        UUID notAssigned = UUID.randomUUID();
        when(permissionResolver.resolvePermissionNamesForActiveRole(user, notAssigned))
                .thenThrow(new IllegalArgumentException("role.not_assigned_or_expired"));

        assertThatThrownBy(() -> authService.switchActiveRole(user.getUuid(), notAssigned, "old-jti",
                UUID.randomUUID(), "old-refresh-token", httpRequest))
                .isInstanceOf(IllegalArgumentException.class);

        verify(loginAuditService, never()).closeSession(any(), anyString());
    }

    private Permission perm(String name) {
        Permission p = new Permission();
        p.setName(name);
        return p;
    }
}
