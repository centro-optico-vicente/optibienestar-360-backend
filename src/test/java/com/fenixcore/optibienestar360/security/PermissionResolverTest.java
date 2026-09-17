package com.fenixcore.optibienestar360.security;

import com.fenixcore.optibienestar360.modules.auth.entity.Permission;
import com.fenixcore.optibienestar360.modules.auth.entity.Role;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.entity.UserRole;
import com.fenixcore.optibienestar360.modules.auth.repository.PermissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PermissionResolver} — the SYSTEM-superuser rule and the
 * ordinary role→permission flattening for everyone else.
 */
@ExtendWith(MockitoExtension.class)
class PermissionResolverTest {

    @Mock private PermissionRepository permissionRepository;

    @InjectMocks private PermissionResolver resolver;

    @Test
    void systemUser_getsFullCatalog_beyondItsOwnRolePermissions() {
        // The SYSTEM role row grants only one permission, but the catalog has
        // three (incl. a "brand new" one never granted to the role).
        User system = userWith(role("SYSTEM", perm("USER_VIEW_ALL")));
        when(permissionRepository.findAll()).thenReturn(List.of(
                perm("USER_VIEW_ALL"), perm("PAYMENT_APPROVE"), perm("BRAND_NEW_PERMISSION")));

        List<String> names = resolver.resolvePermissionNames(system);

        assertThat(names).containsExactlyInAnyOrder(
                "USER_VIEW_ALL", "PAYMENT_APPROVE", "BRAND_NEW_PERMISSION");
    }

    @Test
    void nonSystemUser_getsUnionOfActiveRolePermissions() {
        User admin = userWith(role("ADMINISTRADOR", perm("USER_VIEW_ALL"), perm("PAYMENT_APPROVE")));

        List<String> names = resolver.resolvePermissionNames(admin);

        assertThat(names).containsExactlyInAnyOrder("USER_VIEW_ALL", "PAYMENT_APPROVE");
        // Non-SYSTEM path never touches the full catalog.
        verifyNoInteractions(permissionRepository);
    }

    @Test
    void inactiveSystemAssignment_isNotTreatedAsSuperuser() {
        User u = new User();
        UserRole ur = assignment(role("SYSTEM", perm("USER_VIEW_ALL")), false, null);
        u.setUserRoles(new ArrayList<>(List.of(ur)));

        List<String> names = resolver.resolvePermissionNames(u);

        // Inactive SYSTEM assignment → not a superuser, and (being inactive) it
        // contributes no permissions of its own either.
        assertThat(names).isEmpty();
        verifyNoInteractions(permissionRepository);
    }

    @Test
    void expiredSystemAssignment_isNotTreatedAsSuperuser() {
        User u = new User();
        UserRole ur = assignment(role("SYSTEM", perm("USER_VIEW_ALL")), true,
                Instant.now().minus(1, ChronoUnit.DAYS));
        u.setUserRoles(new ArrayList<>(List.of(ur)));

        List<String> names = resolver.resolvePermissionNames(u);

        assertThat(names).isEmpty();
        verifyNoInteractions(permissionRepository);
    }

    // ─── resolvePermissionNamesForActiveRole ───────────────────────────────

    @Test
    void activeRole_system_getsFullCatalogRegardlessOfOwnPermissions() {
        Role system = role("SYSTEM", perm("USER_VIEW_ALL"));
        User u = userWith(system);
        when(permissionRepository.findAll()).thenReturn(List.of(
                perm("USER_VIEW_ALL"), perm("PAYMENT_APPROVE")));

        List<String> names = resolver.resolvePermissionNamesForActiveRole(u, system.getUuid());

        assertThat(names).containsExactlyInAnyOrder("USER_VIEW_ALL", "PAYMENT_APPROVE");
    }

    @Test
    void activeRole_normalRole_yieldsOnlyThatRolesPermissions_notTheUnion() {
        Role admin = role("ADMINISTRADOR", perm("USER_VIEW_ALL"));
        Role operador = role("OPERADOR", perm("PAYMENT_APPROVE"));
        User u = new User();
        u.setUserRoles(new ArrayList<>(List.of(assignment(admin, true, null), assignment(operador, true, null))));

        List<String> names = resolver.resolvePermissionNamesForActiveRole(u, admin.getUuid());

        assertThat(names).containsExactly("USER_VIEW_ALL");
        verifyNoInteractions(permissionRepository);
    }

    @Test
    void activeRole_notAnEffectiveAssignment_throws() {
        Role admin = role("ADMINISTRADOR", perm("USER_VIEW_ALL"));
        User u = userWith(admin);

        assertThatThrownBy(() -> resolver.resolvePermissionNamesForActiveRole(u, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void activeRole_expiredAssignment_throws() {
        Role admin = role("ADMINISTRADOR", perm("USER_VIEW_ALL"));
        User u = new User();
        u.setUserRoles(new ArrayList<>(List.of(assignment(admin, true, Instant.now().minus(1, ChronoUnit.DAYS)))));

        assertThatThrownBy(() -> resolver.resolvePermissionNamesForActiveRole(u, admin.getUuid()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ─── resolveDefaultActiveRole ──────────────────────────────────────────

    @Test
    void defaultActiveRole_usesUsersDefaultRole_whenItIsEffective() {
        Role admin = role("ADMINISTRADOR");
        Role operador = role("OPERADOR");
        User u = new User();
        u.setDefaultRole(operador);
        u.setUserRoles(new ArrayList<>(List.of(assignment(admin, true, null), assignment(operador, true, null))));

        UserRole active = resolver.resolveDefaultActiveRole(u);

        assertThat(active.getRole()).isEqualTo(operador);
    }

    @Test
    void defaultActiveRole_fallsBackToOldestEffectiveAssignment_whenDefaultIsNotEffective() {
        Role admin = role("ADMINISTRADOR");
        Role operador = role("OPERADOR");
        User u = new User();
        u.setDefaultRole(operador); // assigned but inactive below → not effective

        UserRole adminAssignment = assignment(admin, true, null);
        adminAssignment.setCreatedAt(Instant.now().minus(2, ChronoUnit.DAYS));
        UserRole operadorAssignment = assignment(operador, false, null);

        u.setUserRoles(new ArrayList<>(List.of(operadorAssignment, adminAssignment)));

        UserRole active = resolver.resolveDefaultActiveRole(u);

        assertThat(active.getRole()).isEqualTo(admin);
    }

    @Test
    void defaultActiveRole_noEffectiveRoles_throws() {
        User u = new User();
        u.setUserRoles(new ArrayList<>());

        assertThatThrownBy(() -> resolver.resolveDefaultActiveRole(u))
                .isInstanceOf(NoSuchElementException.class);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private Permission perm(String name) {
        Permission p = new Permission();
        p.setName(name);
        return p;
    }

    private static final AtomicLong ROLE_ID_SEQ = new AtomicLong(1);

    private Role role(String name, Permission... perms) {
        Role r = new Role();
        r.setId(ROLE_ID_SEQ.getAndIncrement());
        r.setUuid(UUID.randomUUID());
        r.setName(name);
        r.setPermissions(new HashSet<>(Arrays.asList(perms)));
        return r;
    }

    private UserRole assignment(Role role, boolean active, Instant expiresAt) {
        UserRole ur = new UserRole();
        ur.setRole(role);
        ur.setActive(active);
        ur.setExpiresAt(expiresAt);
        ur.setCreatedAt(Instant.now());
        return ur;
    }

    private User userWith(Role role) {
        User u = new User();
        u.setUserRoles(new ArrayList<>(List.of(assignment(role, true, null))));
        return u;
    }
}
