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

import static org.assertj.core.api.Assertions.assertThat;
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

    // ─── Helpers ────────────────────────────────────────────────────────────

    private Permission perm(String name) {
        Permission p = new Permission();
        p.setName(name);
        return p;
    }

    private Role role(String name, Permission... perms) {
        Role r = new Role();
        r.setName(name);
        r.setPermissions(new HashSet<>(Arrays.asList(perms)));
        return r;
    }

    private UserRole assignment(Role role, boolean active, Instant expiresAt) {
        UserRole ur = new UserRole();
        ur.setRole(role);
        ur.setActive(active);
        ur.setExpiresAt(expiresAt);
        return ur;
    }

    private User userWith(Role role) {
        User u = new User();
        u.setUserRoles(new ArrayList<>(List.of(assignment(role, true, null))));
        return u;
    }
}
