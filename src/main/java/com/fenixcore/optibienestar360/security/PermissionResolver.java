package com.fenixcore.optibienestar360.security;

import com.fenixcore.optibienestar360.modules.auth.entity.Permission;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.entity.UserRole;
import com.fenixcore.optibienestar360.modules.auth.repository.PermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Single source of truth for the permission names a user effectively holds —
 * used both when minting the JWT {@code permissions} claim ({@code AuthService})
 * and when building Spring authorities ({@code UserDetailsServiceImpl}), so the
 * two paths can never diverge.
 *
 * <p><b>SYSTEM is a superuser:</b> a user carrying the {@code SYSTEM} role is
 * granted <em>every</em> permission that exists, not just the ones currently
 * present in its {@code role_permissions} rows. This makes "SYSTEM has access to
 * everything" hold <em>by default and forever</em>: any permission added later
 * (new feature, new migration) is covered automatically on the SYSTEM user's
 * next login/refresh, with no need to remember to grant it to the SYSTEM role.</p>
 */
@Component
@RequiredArgsConstructor
public class PermissionResolver {

    private static final String SYSTEM_ROLE_NAME = "SYSTEM";

    private final PermissionRepository permissionRepository;

    /**
     * @return the distinct permission names granted to {@code user}. For a
     *         SYSTEM user this is the full permission catalog; for everyone else
     *         it is the union of the permissions of their active, non-expired
     *         role assignments.
     */
    public List<String> resolvePermissionNames(User user) {
        if (hasSystemRole(user)) {
            return permissionRepository.findAll().stream()
                .map(Permission::getName)
                .distinct()
                .toList()
            ;
        }
        return user.getUserRoles().stream()
            .filter(UserRole::isEffective)
            .flatMap(ur -> ur.getRole().getPermissions().stream())
            .map(Permission::getName)
            .distinct()
            .toList()
        ;
    }

    /**
     * @return the permissions granted by the single active role {@code roleUuid}
     *         — used once a session has an "active role" (login, switch-role),
     *         as opposed to {@link #resolvePermissionNames} which still unions
     *         every effective role (kept for {@code UserDetailsServiceImpl}).
     *         SYSTEM as the active role still yields the full catalog.
     * @throws IllegalArgumentException if {@code roleUuid} isn't an effective
     *         (active, non-expired) assignment of this user.
     */
    public List<String> resolvePermissionNamesForActiveRole(User user, UUID roleUuid) {
        UserRole match = user.getUserRoles().stream()
            .filter(UserRole::isEffective)
            .filter(ur -> roleUuid.equals(ur.getRole().getUuid()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("role.not_assigned_or_expired"))
        ;

        if (SYSTEM_ROLE_NAME.equals(match.getRole().getName())) {
            return permissionRepository.findAll().stream()
                .map(Permission::getName)
                .distinct()
                .toList()
            ;
        }
        return match.getRole().getPermissions().stream()
            .map(Permission::getName)
            .distinct()
            .toList()
        ;
    }

    /**
     * @return the {@link UserRole} to use as the session's active role:
     *         {@code user.getDefaultRole()} when it is itself an effective
     *         assignment, otherwise the effective assignment held longest
     *         (oldest {@code createdAt}, tie-broken by role id) — a
     *         deterministic "original role" fallback for users with no
     *         default set, or whose default is no longer effective.
     * @throws NoSuchElementException if the user has no effective role at all.
     */
    public UserRole resolveDefaultActiveRole(User user) {
        List<UserRole> effective = user.getUserRoles().stream().filter(UserRole::isEffective).toList();
        if (effective.isEmpty()) {
            throw new NoSuchElementException("user.no_effective_roles");
        }

        var defaultRole = user.getDefaultRole();
        if (defaultRole != null) {
            for (UserRole ur : effective) {
                if (ur.getRole().getId().equals(defaultRole.getId())) {
                    return ur;
                }
            }
        }

        return effective.stream()
            .min(Comparator.comparing(UserRole::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ur -> ur.getRole().getId()))
            .orElseThrow()
        ;
    }

    private boolean hasSystemRole(User user) {
        return user.getUserRoles().stream()
            .filter(UserRole::isEffective)
            .anyMatch(ur -> SYSTEM_ROLE_NAME.equals(ur.getRole().getName()))
        ;
    }
}
