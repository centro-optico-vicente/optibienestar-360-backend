package com.fenixcore.optibienestar360.modules.auth.service;

import com.fenixcore.optibienestar360.modules.auth.entity.Permission;
import com.fenixcore.optibienestar360.modules.auth.entity.Role;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.entity.UserRole;
import com.fenixcore.optibienestar360.modules.auth.mapper.UserMapper;
import com.fenixcore.optibienestar360.modules.auth.repository.PermissionRepository;
import com.fenixcore.optibienestar360.modules.auth.repository.RoleRepository;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRoleRepository;
import com.fenixcore.optibienestar360.core.util.DefaultSortResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the write guards of {@link RoleService#updateRolePermissions}
 * — the rules that make role-permission editing safe. Mirrors the Mockito
 * pattern of {@code UserServiceLockoutGuardsTest}: real service, mocked
 * repositories, so the business rules are exercised for real (not a mock of the
 * rule) without a database.
 *
 * <p>The four behaviours the vertical-1 checklist item lists, verified here at
 * the layer where the rules actually live:</p>
 * <ul>
 *   <li>SYSTEM role edited by a non-SYSTEM actor → {@link AccessDeniedException}
 *       ({@code role.system.not_editable}) → HTTP 403.</li>
 *   <li>An unknown permission UUID → {@link IllegalArgumentException}
 *       ({@code role.permission.uuid.unknown}) → HTTP 422 (the checklist said
 *       400, but the service raises IllegalArgumentException, which
 *       GlobalExceptionHandler maps to 422).</li>
 *   <li>An update that would strip the actor's own {@code ROLE_PERMISSION_EDIT}
 *       → {@link IllegalArgumentException} ({@code role.auto_lockout}) → 422.</li>
 *   <li>A valid update on a non-SYSTEM role applies the new set and fans the
 *       token-staleness epoch out to every user holding the role.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class RoleServiceGuardsTest {

    @Mock private RoleRepository roleRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private UserMapper userMapper;
    @Mock private TokenBlacklistService blacklistService;
    @Mock private DefaultSortResolver defaultSortResolver;

    private RoleService service;

    @BeforeEach
    void setup() {
        service = new RoleService(roleRepository, permissionRepository, userRepository,
                userRoleRepository, userMapper, blacklistService, defaultSortResolver);
    }

    @Test
    void editingSystemRoleAsNonSystemActor_isForbidden() {
        UUID roleUuid = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();
        when(roleRepository.findByUuid(roleUuid)).thenReturn(Optional.of(role(1L, "SYSTEM")));
        // Actor holds an active role, but not SYSTEM → not a system actor.
        when(userRepository.findWithRolesByUuid(actorUuid))
                .thenReturn(Optional.of(actorWithRoles(role(2L, "ADMINISTRADOR"))));

        assertThatThrownBy(() -> service.updateRolePermissions(roleUuid, Set.of(UUID.randomUUID()), actorUuid))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("role.system.not_editable");

        // Rejected before it ever looked at the requested permissions.
        verify(permissionRepository, never()).findAllByUuidIn(any());
        verifyNoInteractions(blacklistService);
    }

    @Test
    void requestingAnUnknownPermissionUuid_isRejected() {
        UUID roleUuid = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();
        when(roleRepository.findByUuid(roleUuid)).thenReturn(Optional.of(role(1L, "ADMINISTRADOR")));
        // Two UUIDs requested, only one resolves → the whole call is rejected.
        when(permissionRepository.findAllByUuidIn(any())).thenReturn(List.of(perm(10L, "MEMBER_VIEW_ALL")));

        assertThatThrownBy(() -> service.updateRolePermissions(
                roleUuid, Set.of(UUID.randomUUID(), UUID.randomUUID()), actorUuid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("role.permission.uuid.unknown");

        // Never reached the auto-lockout check (which is what loads the actor).
        verify(userRepository, never()).findWithRolesByUuid(any());
        verifyNoInteractions(blacklistService);
    }

    @Test
    void updateThatWouldStripActorsOnlyEditPermission_isRejected() {
        UUID roleUuid = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();
        Permission edit = perm(1L, "ROLE_PERMISSION_EDIT");
        Permission memberView = perm(2L, "MEMBER_VIEW_ALL");
        // The edited role is the actor's ONLY source of ROLE_PERMISSION_EDIT...
        Role edited = role(5L, "ADMINISTRADOR", edit);
        when(roleRepository.findByUuid(roleUuid)).thenReturn(Optional.of(edited));
        // ...and the requested new set drops it.
        when(permissionRepository.findAllByUuidIn(any())).thenReturn(List.of(memberView));
        when(userRepository.findWithRolesByUuid(actorUuid)).thenReturn(Optional.of(actorWithRoles(edited)));

        assertThatThrownBy(() -> service.updateRolePermissions(roleUuid, Set.of(memberView.getUuid()), actorUuid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("role.auto_lockout");

        // Rejected before mutating: the role keeps its original permission.
        assertThat(edited.getPermissions()).containsExactly(edit);
        verifyNoInteractions(blacklistService);
    }

    @Test
    void validUpdateOnNonSystemRole_appliesTheNewSetAndFansOutInvalidation() {
        UUID roleUuid = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();
        Permission edit = perm(1L, "ROLE_PERMISSION_EDIT");
        Permission memberView = perm(2L, "MEMBER_VIEW_ALL");
        Role edited = role(5L, "ADMINISTRADOR", edit);
        when(roleRepository.findByUuid(roleUuid)).thenReturn(Optional.of(edited));
        when(permissionRepository.findAllByUuidIn(any())).thenReturn(List.of(memberView));
        // The actor keeps ROLE_PERMISSION_EDIT through a SEPARATE active role, so
        // dropping it from the edited role is allowed (no self-lockout).
        Role keeper = role(9L, "SECURITY_ADMIN", edit);
        when(userRepository.findWithRolesByUuid(actorUuid))
                .thenReturn(Optional.of(actorWithRoles(edited, keeper)));
        UUID affectedA = UUID.randomUUID();
        UUID affectedB = UUID.randomUUID();
        when(userRoleRepository.findActiveUserUuidsByRoleId(5L)).thenReturn(List.of(affectedA, affectedB));

        service.updateRolePermissions(roleUuid, Set.of(memberView.getUuid()), actorUuid);

        // Replace-the-set semantics: exactly the requested permission remains.
        assertThat(edited.getPermissions()).containsExactly(memberView);
        // Every user holding the edited role has its token invalidated.
        verify(blacklistService).markUserInvalidatedNow(affectedA.toString());
        verify(blacklistService).markUserInvalidatedNow(affectedB.toString());
    }

    // ─── Fixtures ───────────────────────────────────────────────────────────

    private Permission perm(long id, String name) {
        Permission p = new Permission();
        p.setId(id);
        p.setUuid(UUID.randomUUID());
        p.setName(name);
        return p;
    }

    private Role role(long id, String name, Permission... perms) {
        Role r = new Role();
        r.setId(id);
        r.setUuid(UUID.randomUUID());
        r.setName(name);
        for (Permission p : perms) {
            r.getPermissions().add(p);
        }
        return r;
    }

    private User actorWithRoles(Role... roles) {
        User u = new User();
        u.setId(999L);
        u.setUuid(UUID.randomUUID());
        for (Role r : roles) {
            UserRole ur = new UserRole();
            ur.setRole(r);
            ur.setActive(true);
            ur.setExpiresAt(null);
            u.getUserRoles().add(ur);
        }
        return u;
    }
}
