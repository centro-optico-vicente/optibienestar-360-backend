package com.fenixcore.optisaludplus.modules.auth.service;

import com.fenixcore.optisaludplus.modules.auth.dto.AdminUpdateUserRequest;
import com.fenixcore.optisaludplus.modules.auth.entity.Role;
import com.fenixcore.optisaludplus.modules.auth.entity.User;
import com.fenixcore.optisaludplus.modules.auth.entity.UserRole;
import com.fenixcore.optisaludplus.modules.auth.mapper.UserMapper;
import com.fenixcore.optisaludplus.modules.auth.repository.RoleRepository;
import com.fenixcore.optisaludplus.modules.auth.repository.UserRepository;
import com.fenixcore.optisaludplus.modules.auth.repository.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for the [P2/C2] anti-lockout guards added to
 * {@link UserService#updateUser(UUID, AdminUpdateUserRequest, UUID)} and
 * {@link UserService#deleteUser(UUID, UUID)}.
 *
 * <p>Scope: the 6 reject-paths only. The happy path (admin edits a non-SYSTEM
 * non-self user) is covered indirectly by all existing integration-style
 * usage of the service.</p>
 */
@ExtendWith(MockitoExtension.class)
class UserServiceLockoutGuardsTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private UserMapper userMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TokenBlacklistService blacklistService;

    @InjectMocks private UserService userService;

    private UUID targetUuid;
    private UUID otherActorUuid;

    @BeforeEach
    void setUp() {
        targetUuid = UUID.randomUUID();
        otherActorUuid = UUID.randomUUID();
    }

    // ─── SYSTEM user immutability ───────────────────────────────────────────

    @Test
    void updateUser_rejectsWhenTargetIsSystemUser() {
        User systemUser = userWithRole(targetUuid, "SYSTEM");
        when(userRepository.findWithRolesByUuid(targetUuid)).thenReturn(Optional.of(systemUser));

        AdminUpdateUserRequest request = minimalUpdate("New Name");

        assertThatThrownBy(() -> userService.updateUser(targetUuid, request, otherActorUuid))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("user.system.not_editable");

        verify(userRepository, never()).save(any());
        verify(blacklistService, never()).markUserInvalidatedNow(any());
    }

    @Test
    void deleteUser_rejectsWhenTargetIsSystemUser() {
        User systemUser = userWithRole(targetUuid, "SYSTEM");
        when(userRepository.findWithRolesByUuid(targetUuid)).thenReturn(Optional.of(systemUser));

        assertThatThrownBy(() -> userService.deleteUser(targetUuid, otherActorUuid))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("user.system.not_deletable");

        verify(userRepository, never()).save(any());
        verify(blacklistService, never()).revokeAllUserRefreshTokens(any());
    }

    // ─── Self-edit restrictions ─────────────────────────────────────────────

    @Test
    void updateUser_rejectsSelfDeactivation() {
        User target = userWithRole(targetUuid, "ADMINISTRADOR");
        when(userRepository.findWithRolesByUuid(targetUuid)).thenReturn(Optional.of(target));

        AdminUpdateUserRequest request = new AdminUpdateUserRequest(
                null, null, null, null, null, null, /* active */ false, null);

        // actorUuid == targetUuid → self
        assertThatThrownBy(() -> userService.updateUser(targetUuid, request, targetUuid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("user.self.cannot_deactivate");
    }

    @Test
    void updateUser_rejectsSelfSuspensionViaStatus() {
        User target = userWithRole(targetUuid, "ADMINISTRADOR");
        when(userRepository.findWithRolesByUuid(targetUuid)).thenReturn(Optional.of(target));

        AdminUpdateUserRequest request = new AdminUpdateUserRequest(
                null, null, null, null, null, /* status */ "SUSPENDED", null, null);

        assertThatThrownBy(() -> userService.updateUser(targetUuid, request, targetUuid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("user.self.cannot_change_own_status");
    }

    @Test
    void updateUser_rejectsSelfRoleReassignment() {
        User target = userWithRole(targetUuid, "ADMINISTRADOR");
        when(userRepository.findWithRolesByUuid(targetUuid)).thenReturn(Optional.of(target));

        AdminUpdateUserRequest request = new AdminUpdateUserRequest(
                null, null, null, null, null, null, null,
                /* roleIds */ List.of(UUID.randomUUID()));

        assertThatThrownBy(() -> userService.updateUser(targetUuid, request, targetUuid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("user.self.cannot_change_own_roles");
    }

    @Test
    void deleteUser_rejectsSelfDelete() {
        // No need to stub the repository — the self-check fires before the load.
        assertThatThrownBy(() -> userService.deleteUser(targetUuid, /* actor */ targetUuid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("user.self.cannot_delete");

        verify(userRepository, never()).findWithRolesByUuid(any());
        verify(blacklistService, never()).revokeAllUserRefreshTokens(any());
    }

    // ─── Confirm guards do NOT fire on legitimate edits ─────────────────────

    @Test
    void updateUser_allowsSelfProfileEditWhenNotTouchingSecurityFields() {
        User target = userWithRole(targetUuid, "ADMINISTRADOR");
        when(userRepository.findWithRolesByUuid(targetUuid)).thenReturn(Optional.of(target));

        AdminUpdateUserRequest request = new AdminUpdateUserRequest(
                "Updated Self Name", null, null, "+58414-1234567", null, null, null, null);

        // Should NOT throw — profile fields are editable on self.
        // The mapper will be called; stub it so we don't NPE on the return value.
        when(userMapper.toDto(any())).thenReturn(null);
        UserMapper unused = userMapper;
        assertThat(unused).isNotNull();
        userService.updateUser(targetUuid, request, targetUuid);

        verify(userRepository).save(any());
        // No token invalidation because no role change.
        verify(blacklistService, never()).markUserInvalidatedNow(any());
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private User userWithRole(UUID userUuid, String roleName) {
        User u = new User();
        u.setUuid(userUuid);
        Role role = new Role();
        role.setName(roleName);
        UserRole ur = new UserRole();
        ur.setUser(u);
        ur.setRole(role);
        ur.setActive(true);
        List<UserRole> roles = new ArrayList<>();
        roles.add(ur);
        u.setUserRoles(roles);
        return u;
    }

    private AdminUpdateUserRequest minimalUpdate(String name) {
        return new AdminUpdateUserRequest(name, null, null, null, null, null, null, null);
    }
}
