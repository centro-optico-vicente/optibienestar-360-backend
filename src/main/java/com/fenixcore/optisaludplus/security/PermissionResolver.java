package com.fenixcore.optisaludplus.security;

import com.fenixcore.optisaludplus.modules.auth.entity.Permission;
import com.fenixcore.optisaludplus.modules.auth.entity.User;
import com.fenixcore.optisaludplus.modules.auth.entity.UserRole;
import com.fenixcore.optisaludplus.modules.auth.repository.PermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

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
            .filter(this::isEffective)
            .flatMap(ur -> ur.getRole().getPermissions().stream())
            .map(Permission::getName)
            .distinct()
            .toList()
        ;
    }

    private boolean hasSystemRole(User user) {
        return user.getUserRoles().stream()
            .filter(this::isEffective)
            .anyMatch(ur -> SYSTEM_ROLE_NAME.equals(ur.getRole().getName()))
        ;
    }

    /** Active assignment that has not expired. */
    private boolean isEffective(UserRole ur) {
        return ur.isActive()
            && (ur.getExpiresAt() == null || ur.getExpiresAt().isAfter(Instant.now()))
        ;
    }
}
