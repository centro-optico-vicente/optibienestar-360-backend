package com.fenixcore.optibienestar360.modules.auth.service;

import com.fenixcore.optibienestar360.modules.auth.entity.Role;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.entity.UserRole;
import com.fenixcore.optibienestar360.modules.auth.mapper.UserMapper;
import com.fenixcore.optibienestar360.modules.auth.repository.PermissionRepository;
import com.fenixcore.optibienestar360.modules.auth.repository.RoleRepository;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RoleService#listUsers}, {@link RoleService#assignUser}
 * and {@link RoleService#removeUser} — the {@code /v1/admin/roles/{uuid}/users}
 * sub-resource. Mirrors the Mockito pattern of {@link RoleServiceGuardsTest}:
 * real service, mocked repositories.
 */
@ExtendWith(MockitoExtension.class)
class RoleUsersServiceTest {

    @Mock private RoleRepository roleRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private UserMapper userMapper;
    @Mock private TokenBlacklistService blacklistService;

    private RoleService service;

    @BeforeEach
    void setup() {
        service = new RoleService(roleRepository, permissionRepository, userRepository,
                userRoleRepository, userMapper, blacklistService);
    }

    @Test
    void assigningToUnknownRole_isNotFound() {
        UUID roleUuid = UUID.randomUUID();
        UUID userUuid = UUID.randomUUID();
        when(roleRepository.findByUuid(roleUuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignUser(roleUuid, userUuid))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("role.not_found");

        verifyNoInteractions(blacklistService, userRoleRepository);
    }

    @Test
    void assigningUnknownUser_isNotFound() {
        UUID roleUuid = UUID.randomUUID();
        UUID userUuid = UUID.randomUUID();
        when(roleRepository.findByUuid(roleUuid)).thenReturn(Optional.of(role(1L)));
        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignUser(roleUuid, userUuid))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("user.not_found");

        verifyNoInteractions(blacklistService);
        verify(userRoleRepository, never()).save(any());
    }

    @Test
    void assigningNewPair_insertsPivotRowAndInvalidatesToken() {
        UUID roleUuid = UUID.randomUUID();
        UUID userUuid = UUID.randomUUID();
        Role role = role(1L);
        User user = user(2L, userUuid);
        when(roleRepository.findByUuid(roleUuid)).thenReturn(Optional.of(role));
        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));
        when(userRoleRepository.findByUserIdAndRoleId(2L, 1L)).thenReturn(Optional.empty());

        service.assignUser(roleUuid, userUuid);

        ArgumentCaptor<UserRole> captor = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(role);
        assertThat(captor.getValue().getUser()).isEqualTo(user);
        assertThat(captor.getValue().isActive()).isTrue();
        verify(blacklistService).markUserInvalidatedNow(userUuid.toString());
    }

    @Test
    void assigningAlreadyInactivePair_reactivatesExistingRowInsteadOfInserting() {
        UUID roleUuid = UUID.randomUUID();
        UUID userUuid = UUID.randomUUID();
        Role role = role(1L);
        User user = user(2L, userUuid);
        UserRole existing = new UserRole();
        existing.setUser(user);
        existing.setRole(role);
        existing.setActive(false);
        when(roleRepository.findByUuid(roleUuid)).thenReturn(Optional.of(role));
        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));
        when(userRoleRepository.findByUserIdAndRoleId(2L, 1L)).thenReturn(Optional.of(existing));

        service.assignUser(roleUuid, userUuid);

        assertThat(existing.isActive()).isTrue();
        verify(userRoleRepository).save(existing);
        verify(blacklistService).markUserInvalidatedNow(userUuid.toString());
    }

    @Test
    void removingActiveAssignment_deactivatesAndInvalidatesToken() {
        UUID roleUuid = UUID.randomUUID();
        UUID userUuid = UUID.randomUUID();
        Role role = role(1L);
        User user = user(2L, userUuid);
        UserRole existing = new UserRole();
        existing.setUser(user);
        existing.setRole(role);
        existing.setActive(true);
        when(roleRepository.findByUuid(roleUuid)).thenReturn(Optional.of(role));
        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));
        when(userRoleRepository.findByUserIdAndRoleId(2L, 1L)).thenReturn(Optional.of(existing));

        service.removeUser(roleUuid, userUuid);

        assertThat(existing.isActive()).isFalse();
        verify(blacklistService, times(1)).markUserInvalidatedNow(userUuid.toString());
    }

    @Test
    void removingWithNoActiveAssignment_isNoopAndDoesNotInvalidateToken() {
        UUID roleUuid = UUID.randomUUID();
        UUID userUuid = UUID.randomUUID();
        Role role = role(1L);
        User user = user(2L, userUuid);
        when(roleRepository.findByUuid(roleUuid)).thenReturn(Optional.of(role));
        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));
        when(userRoleRepository.findByUserIdAndRoleId(2L, 1L)).thenReturn(Optional.empty());

        service.removeUser(roleUuid, userUuid);

        verifyNoInteractions(blacklistService);
    }

    // ─── Fixtures ───────────────────────────────────────────────────────────

    private Role role(long id) {
        Role r = new Role();
        r.setId(id);
        r.setUuid(UUID.randomUUID());
        r.setName("ADMINISTRADOR");
        return r;
    }

    private User user(long id, UUID uuid) {
        User u = new User();
        u.setId(id);
        u.setUuid(uuid);
        return u;
    }
}
