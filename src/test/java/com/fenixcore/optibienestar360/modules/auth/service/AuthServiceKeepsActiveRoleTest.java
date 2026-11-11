package com.fenixcore.optibienestar360.modules.auth.service;

import com.fenixcore.optibienestar360.common.service.EmailService;
import com.fenixcore.optibienestar360.core.audit.LoginAuditService;
import com.fenixcore.optibienestar360.modules.auth.dto.AccessTokenResponse;
import com.fenixcore.optibienestar360.modules.auth.dto.LoginResponse;
import com.fenixcore.optibienestar360.modules.auth.dto.RefreshRequest;
import com.fenixcore.optibienestar360.modules.auth.dto.UserDto;
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
import com.fenixcore.optibienestar360.security.RoleNames;
import com.fenixcore.optibienestar360.security.jwt.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reissued tokens keep the session's active role (backend#411): the refresh token carries it, a refresh reissues
 * both tokens for it, and a locale change keeps it. A token is never reissued roleless with the union of every
 * role's permissions.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceKeepsActiveRoleTest {

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
    private Role pendingAlly;
    private Role advisor;
    private UserRole advisorAssignment;
    private String subject;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, securityPolicyRepository, passwordHistoryRepository,
                sessionLogRepository, jwtService, permissionResolver, blacklistService, passwordEncoder, userMapper,
                emailService, messageSource, loginAuditService);
        ReflectionTestUtils.setField(authService, "accessExpirationMinutes", 15);
        ReflectionTestUtils.setField(authService, "refreshExpirationDays", 30);

        pendingAlly = role(1L, RoleNames.ALLY_PENDING);
        advisor = role(2L, RoleNames.ADVISOR);
        UserRole pendingAssignment = assignment(pendingAlly);
        advisorAssignment = assignment(advisor);
        Person person = new Person();
        person.setLocale("es-VE");
        user = new User();
        user.setUuid(UUID.randomUUID());
        user.setPerson(person);
        user.setDefaultRole(advisor);
        user.setUserRoles(new ArrayList<>(List.of(pendingAssignment, advisorAssignment)));
        subject = user.getUuid().toString();
    }

    @Test
    void refresh_reissuesBothTokensForTheRoleTheRefreshTokenCarries() {
        stubValidRefresh();
        when(jwtService.extractActiveRoleUuid("old-refresh")).thenReturn(pendingAlly.getUuid());
        when(jwtService.extractActiveRoleName("old-refresh")).thenReturn(RoleNames.ALLY_PENDING);
        when(permissionResolver.resolvePermissionNamesForActiveRole(user, pendingAlly.getUuid()))
                .thenReturn(List.of("ALLY_VIEW_OWN"));
        stubIssue(pendingAlly);

        LoginResponse response = authService.refresh(new RefreshRequest("old-refresh"));

        assertThat(response.accessToken()).isEqualTo("new-access");
        verify(jwtService).generateAccessToken(eq(subject), eq(List.of("ALLY_VIEW_OWN")), any(), isNull(),
                eq(pendingAlly.getUuid()), eq(RoleNames.ALLY_PENDING));
        verify(jwtService).generateRefreshToken(subject, null, pendingAlly.getUuid(), RoleNames.ALLY_PENDING);
        verify(permissionResolver, never()).resolvePermissionNames(user);
    }

    @Test
    void refresh_withALegacyRolelessRefreshToken_usesTheDefaultRoleNeverTheUnion() {
        stubValidRefresh();
        when(jwtService.extractActiveRoleUuid("old-refresh")).thenReturn(null);
        when(jwtService.extractActiveRoleName("old-refresh")).thenReturn(null);
        when(permissionResolver.resolveDefaultActiveRole(user)).thenReturn(advisorAssignment);
        when(permissionResolver.resolvePermissionNamesForActiveRole(user, advisor.getUuid()))
                .thenReturn(List.of("ADVISOR_PORTFOLIO_VIEW_OWN"));
        stubIssue(advisor);

        authService.refresh(new RefreshRequest("old-refresh"));

        verify(jwtService).generateAccessToken(eq(subject), eq(List.of("ADVISOR_PORTFOLIO_VIEW_OWN")), any(),
                isNull(), eq(advisor.getUuid()), eq(RoleNames.ADVISOR));
        verify(jwtService).generateRefreshToken(subject, null, advisor.getUuid(), RoleNames.ADVISOR);
        verify(permissionResolver, never()).resolvePermissionNames(user);
    }

    @Test
    void updateMyLocale_keepsTheSessionsActiveRoleAndItsPermissions() {
        when(userRepository.findWithRolesByUuid(user.getUuid())).thenReturn(Optional.of(user));
        when(permissionResolver.resolvePermissionNamesForActiveRole(user, pendingAlly.getUuid()))
                .thenReturn(List.of("ALLY_VIEW_OWN"));
        when(jwtService.generateAccessToken(eq(subject), any(), eq("en"), isNull(), eq(pendingAlly.getUuid()),
                eq(RoleNames.ALLY_PENDING))).thenReturn("locale-access");
        when(userMapper.toDto(user)).thenReturn(mock(UserDto.class));

        AccessTokenResponse response = authService.updateMyLocale(user.getUuid(), "en", "old-jti", null,
                pendingAlly.getUuid());

        assertThat(response.accessToken()).isEqualTo("locale-access");
        verify(jwtService).generateAccessToken(eq(subject), eq(List.of("ALLY_VIEW_OWN")), eq("en"), isNull(),
                eq(pendingAlly.getUuid()), eq(RoleNames.ALLY_PENDING));
        verify(permissionResolver, never()).resolvePermissionNames(user);
    }

    private void stubValidRefresh() {
        when(jwtService.isValid("old-refresh")).thenReturn(true);
        when(jwtService.isAccessToken("old-refresh")).thenReturn(false);
        when(jwtService.extractJti("old-refresh")).thenReturn("old-jti");
        when(blacklistService.isValidRefreshToken("old-jti")).thenReturn(true);
        when(jwtService.extractSubject("old-refresh")).thenReturn(subject);
        when(userRepository.findWithRolesByUuid(user.getUuid())).thenReturn(Optional.of(user));
    }

    private void stubIssue(Role active) {
        when(jwtService.generateAccessToken(eq(subject), any(), any(), isNull(), eq(active.getUuid()),
                eq(active.getName()))).thenReturn("new-access");
        when(jwtService.generateRefreshToken(subject, null, active.getUuid(), active.getName()))
                .thenReturn("new-refresh");
        when(jwtService.extractJti("new-access")).thenReturn("new-access-jti");
        when(jwtService.extractJti("new-refresh")).thenReturn("new-refresh-jti");
        when(userMapper.toDto(user)).thenReturn(mock(UserDto.class));
    }

    private static Role role(long id, String name) {
        Role role = new Role();
        role.setId(id);
        role.setUuid(UUID.randomUUID());
        role.setName(name);
        return role;
    }

    private static UserRole assignment(Role role) {
        UserRole assignment = new UserRole();
        assignment.setRole(role);
        assignment.setActive(true);
        return assignment;
    }
}
