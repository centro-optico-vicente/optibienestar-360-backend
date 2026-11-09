package com.fenixcore.optibienestar360.modules.auth.service;

import com.fenixcore.optibienestar360.common.service.EmailService;
import com.fenixcore.optibienestar360.core.audit.LoginAuditService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A refresh after staff approved a registration (the pending role was swapped
 * for the full one) reissues the token with the new role and permissions, so
 * the portal needs no new login (hub ADR 0037 §5).
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceRefreshAfterRoleSwapTest {

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
        ReflectionTestUtils.setField(authService, "accessExpirationMinutes", 15);
        ReflectionTestUtils.setField(authService, "refreshExpirationDays", 30);
    }

    @Test
    void refresh_afterTheRoleSwap_fallsBackToTheNewDefaultRoleAndItsPermissions() {
        Role pending = new Role();
        pending.setId(1L);
        pending.setUuid(UUID.randomUUID());
        pending.setName(RoleNames.ALLY_PENDING);
        Role ally = new Role();
        ally.setId(2L);
        ally.setUuid(UUID.randomUUID());
        ally.setName(RoleNames.ALLY);
        UserRole allyAssignment = new UserRole();
        allyAssignment.setRole(ally);
        allyAssignment.setActive(true);
        Person person = new Person();
        person.setLocale("es-VE");
        User user = new User();
        user.setUuid(UUID.randomUUID());
        user.setPerson(person);
        user.setDefaultRole(ally);
        user.setUserRoles(new ArrayList<>(List.of(allyAssignment)));

        String subject = user.getUuid().toString();
        when(jwtService.isValid("old-refresh")).thenReturn(true);
        when(jwtService.isAccessToken("old-refresh")).thenReturn(false);
        when(jwtService.extractJti("old-refresh")).thenReturn("old-jti");
        when(blacklistService.isValidRefreshToken("old-jti")).thenReturn(true);
        when(jwtService.extractSubject("old-refresh")).thenReturn(subject);
        when(userRepository.findWithRolesByUuid(user.getUuid())).thenReturn(Optional.of(user));
        when(jwtService.extractActiveRoleUuid("old-refresh")).thenReturn(pending.getUuid());
        when(jwtService.extractActiveRoleName("old-refresh")).thenReturn(RoleNames.ALLY_PENDING);
        when(permissionResolver.resolvePermissionNamesForActiveRole(user, pending.getUuid()))
                .thenThrow(new IllegalArgumentException("role.not_assigned_or_expired"));
        when(permissionResolver.resolveDefaultActiveRole(user)).thenReturn(allyAssignment);
        when(permissionResolver.resolvePermissionNamesForActiveRole(user, ally.getUuid()))
                .thenReturn(List.of("ALLY_VIEW_OWN", "ALLY_PRODUCT_CREATE_OWN"));
        when(jwtService.generateAccessToken(eq(subject), any(), any(), isNull(), eq(ally.getUuid()),
                eq(RoleNames.ALLY))).thenReturn("new-access");
        when(jwtService.generateRefreshToken(subject, null)).thenReturn("new-refresh");
        when(jwtService.extractJti("new-access")).thenReturn("new-access-jti");
        when(jwtService.extractJti("new-refresh")).thenReturn("new-refresh-jti");
        when(userMapper.toDto(user)).thenReturn(mock(UserDto.class));

        LoginResponse response = authService.refresh(new RefreshRequest("old-refresh"));

        assertThat(response.accessToken()).isEqualTo("new-access");
        verify(jwtService).generateAccessToken(eq(subject), eq(List.of("ALLY_VIEW_OWN", "ALLY_PRODUCT_CREATE_OWN")),
                any(), isNull(), eq(ally.getUuid()), eq(RoleNames.ALLY));
    }
}
