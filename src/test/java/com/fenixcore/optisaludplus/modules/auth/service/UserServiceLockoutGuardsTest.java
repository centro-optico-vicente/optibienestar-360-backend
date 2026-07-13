package com.fenixcore.optisaludplus.modules.auth.service;

import com.fenixcore.optisaludplus.modules.auth.dto.AdminCreateUserRequest;
import com.fenixcore.optisaludplus.modules.auth.dto.AdminUpdateUserRequest;
import com.fenixcore.optisaludplus.modules.auth.entity.Role;
import com.fenixcore.optisaludplus.modules.auth.entity.User;
import com.fenixcore.optisaludplus.modules.auth.entity.UserRole;
import com.fenixcore.optisaludplus.modules.auth.mapper.UserMapper;
import com.fenixcore.optisaludplus.modules.auth.repository.RoleRepository;
import com.fenixcore.optisaludplus.modules.auth.repository.UserRepository;
import com.fenixcore.optisaludplus.modules.auth.repository.UserRoleRepository;
import com.fenixcore.optisaludplus.modules.person.entity.Person;
import com.fenixcore.optisaludplus.modules.person.service.PersonService;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for the SYSTEM-role authorization and anti-lockout guards on
 * {@link UserService}: self-edit/anti-lockout on
 * {@link UserService#updateUser(UUID, AdminUpdateUserRequest, UUID)} and
 * {@link UserService#deleteUser(UUID, UUID)}, plus the actor-scoped SYSTEM
 * guards — only a SYSTEM actor may view/edit a SYSTEM user
 * ({@link UserService#getUser(UUID, UUID)}) or assign the SYSTEM role on
 * create/update.
 *
 * <p>Scope: reject-paths plus the SYSTEM-actor allow-paths. The generic happy
 * path (admin edits a non-SYSTEM non-self user) is covered indirectly by all
 * existing integration-style usage of the service.</p>
 */
@ExtendWith(MockitoExtension.class)
class UserServiceLockoutGuardsTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private UserMapper userMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TokenBlacklistService blacklistService;
    @Mock private PersonService personService;

    @InjectMocks private UserService userService;

    private static final UUID SYSTEM_ROLE_UUID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private UUID targetUuid;
    private UUID otherActorUuid;

    @BeforeEach
    void setUp() {
        targetUuid = UUID.randomUUID();
        otherActorUuid = UUID.randomUUID();
    }

    // ─── SYSTEM user immutability ───────────────────────────────────────────

    @Test
    void updateUser_rejectsWhenNonSystemActorEditsSystemUser() {
        User systemUser = userWithRole(targetUuid, "SYSTEM");
        when(userRepository.findWithRolesByUuid(targetUuid)).thenReturn(Optional.of(systemUser));
        // The actor is a non-SYSTEM admin → the guard fires.
        when(userRepository.findWithRolesByUuid(otherActorUuid))
                .thenReturn(Optional.of(userWithRole(otherActorUuid, "ADMINISTRADOR")));

        AdminUpdateUserRequest request = minimalUpdateFirstName("Renamed");

        assertThatThrownBy(() -> userService.updateUser(targetUuid, request, otherActorUuid))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("user.system.not_editable");

        verify(userRepository, never()).save(any());
        verify(blacklistService, never()).markUserInvalidatedNow(any());
    }

    @Test
    void updateUser_allowsSystemActorToEditSystemUser() {
        User systemTarget = userWithRole(targetUuid, "SYSTEM");
        when(userRepository.findWithRolesByUuid(targetUuid)).thenReturn(Optional.of(systemTarget));
        when(userRepository.findWithRolesByUuid(otherActorUuid))
                .thenReturn(Optional.of(userWithRole(otherActorUuid, "SYSTEM")));

        AdminUpdateUserRequest request = updateProfileFields("Renamed", null);

        // Should NOT throw — a SYSTEM actor may edit a SYSTEM user.
        userService.updateUser(targetUuid, request, otherActorUuid);

        verify(userRepository).save(any());
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

        AdminUpdateUserRequest request = updateWithActive(false);

        // actorUuid == targetUuid → self
        assertThatThrownBy(() -> userService.updateUser(targetUuid, request, targetUuid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("user.self.cannot_deactivate");
    }

    @Test
    void updateUser_rejectsSelfSuspensionViaStatus() {
        User target = userWithRole(targetUuid, "ADMINISTRADOR");
        when(userRepository.findWithRolesByUuid(targetUuid)).thenReturn(Optional.of(target));

        AdminUpdateUserRequest request = updateWithStatus("SUSPENDED");

        assertThatThrownBy(() -> userService.updateUser(targetUuid, request, targetUuid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("user.self.cannot_change_own_status");
    }

    @Test
    void updateUser_rejectsSelfRoleReassignment() {
        User target = userWithRole(targetUuid, "ADMINISTRADOR");
        when(userRepository.findWithRolesByUuid(targetUuid)).thenReturn(Optional.of(target));

        AdminUpdateUserRequest request = updateWithRoleIds(List.of(UUID.randomUUID()));

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
        // findWithRolesByUuid is called twice — once at entry, once for the
        // toDto call at the end. Stub a default return for both.
        when(userRepository.findWithRolesByUuid(targetUuid)).thenReturn(Optional.of(target));

        AdminUpdateUserRequest request = updateProfileFields("Edwin", "+58414-1234567");

        // Should NOT throw — profile fields are editable on self.
        userService.updateUser(targetUuid, request, targetUuid);

        verify(userRepository).save(any());
        // No token invalidation because no role change.
        verify(blacklistService, never()).markUserInvalidatedNow(any());
    }

    // ─── SYSTEM user visibility (getUser) ───────────────────────────────────

    @Test
    void getUser_rejectsNonSystemActorViewingSystemUser() {
        User systemTarget = userWithRole(targetUuid, "SYSTEM");
        when(userRepository.findWithRolesByUuid(targetUuid)).thenReturn(Optional.of(systemTarget));
        when(userRepository.findWithRolesByUuid(otherActorUuid))
                .thenReturn(Optional.of(userWithRole(otherActorUuid, "ADMINISTRADOR")));

        assertThatThrownBy(() -> userService.getUser(targetUuid, otherActorUuid))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("user.system.not_viewable");
    }

    @Test
    void getUser_allowsSystemActorToViewSystemUser() {
        User systemTarget = userWithRole(targetUuid, "SYSTEM");
        when(userRepository.findWithRolesByUuid(targetUuid)).thenReturn(Optional.of(systemTarget));
        when(userRepository.findWithRolesByUuid(otherActorUuid))
                .thenReturn(Optional.of(userWithRole(otherActorUuid, "SYSTEM")));

        // Should NOT throw — a SYSTEM actor may view a SYSTEM user.
        userService.getUser(targetUuid, otherActorUuid);

        verify(userMapper).toDto(systemTarget);
    }

    // ─── SYSTEM role assignment gated to SYSTEM actors ──────────────────────

    @Test
    void updateUser_rejectsSystemRoleAssignmentByNonSystemActor() {
        User target = userWithRole(targetUuid, "ADMINISTRADOR");
        when(userRepository.findWithRolesByUuid(targetUuid)).thenReturn(Optional.of(target));
        when(userRepository.findWithRolesByUuid(otherActorUuid))
                .thenReturn(Optional.of(userWithRole(otherActorUuid, "ADMINISTRADOR")));
        when(roleRepository.findByName("SYSTEM")).thenReturn(Optional.of(systemRole()));

        AdminUpdateUserRequest request = updateWithRoleIds(List.of(SYSTEM_ROLE_UUID));

        assertThatThrownBy(() -> userService.updateUser(targetUuid, request, otherActorUuid))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("user.system.role_not_assignable");

        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_rejectsSystemRoleAssignmentByNonSystemActor() {
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(roleRepository.findByName("SYSTEM")).thenReturn(Optional.of(systemRole()));
        when(userRepository.findWithRolesByUuid(otherActorUuid))
                .thenReturn(Optional.of(userWithRole(otherActorUuid, "ADMINISTRADOR")));

        AdminCreateUserRequest request = new AdminCreateUserRequest(
                "new@example.com", "First", null, "Last", null, "password1",
                "V", "12345678", null, null, null, List.of(SYSTEM_ROLE_UUID));

        assertThatThrownBy(() -> userService.createUser(request, otherActorUuid))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("user.system.role_not_assignable");

        verify(userRepository, never()).save(any());
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    /**
     * Builds a User entity with a populated Person (since UserService.updateUser
     * walks user.getPerson()...) plus a single active role assignment.
     */
    private User userWithRole(UUID userUuid, String roleName) {
        User u = new User();
        u.setUuid(userUuid);
        u.setPerson(emptyPerson());

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

    /** A Role entity standing in for the seeded SYSTEM role (name + uuid only). */
    private Role systemRole() {
        Role role = new Role();
        role.setName("SYSTEM");
        role.setUuid(SYSTEM_ROLE_UUID);
        return role;
    }

    private Person emptyPerson() {
        Person p = new Person();
        p.setFirstName("Seed");
        p.setLastName("Person");
        p.setDocumentType("V");
        p.setDocumentNumber("99999999");
        return p;
    }

    private AdminUpdateUserRequest minimalUpdateFirstName(String firstName) {
        // firstName-only edit: tests the SYSTEM-user guard fires before any
        // Person mutation, regardless of which field is being changed.
        return new AdminUpdateUserRequest(
                firstName, null, null, null,
                null, null, null, null,
                null, null,
                null, null, null);
    }

    private AdminUpdateUserRequest updateWithActive(boolean active) {
        return new AdminUpdateUserRequest(
                null, null, null, null,
                null, null, null, null,
                null, null,
                null, /* active */ active, null);
    }

    private AdminUpdateUserRequest updateWithStatus(String status) {
        return new AdminUpdateUserRequest(
                null, null, null, null,
                null, null, null, null,
                null, null,
                /* status */ status, null, null);
    }

    private AdminUpdateUserRequest updateWithRoleIds(List<UUID> roleIds) {
        return new AdminUpdateUserRequest(
                null, null, null, null,
                null, null, null, null,
                null, null,
                null, null, /* roleIds */ roleIds);
    }

    private AdminUpdateUserRequest updateProfileFields(String firstName, String phone) {
        return new AdminUpdateUserRequest(
                firstName, null, null, null,
                null, null, null, null,
                phone, null,
                null, null, null);
    }
}
