package com.fenixcore.optibienestar360.modules.auth.service;

import com.fenixcore.optibienestar360.modules.auth.dto.CreateRoleRequest;
import com.fenixcore.optibienestar360.modules.auth.dto.RoleDto;
import com.fenixcore.optibienestar360.modules.auth.dto.UpdateRoleRequest;
import com.fenixcore.optibienestar360.modules.auth.entity.Permission;
import com.fenixcore.optibienestar360.modules.auth.entity.Role;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.entity.UserRole;
import com.fenixcore.optibienestar360.modules.auth.mapper.UserMapper;
import com.fenixcore.optibienestar360.modules.auth.repository.PermissionRepository;
import com.fenixcore.optibienestar360.modules.auth.repository.RoleRepository;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoleService {

    private static final String SYSTEM_ROLE_NAME = "SYSTEM";
    private static final String ROLE_PERMISSION_EDIT = "ROLE_PERMISSION_EDIT";

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserMapper userMapper;
    private final TokenBlacklistService blacklistService;

    public List<RoleDto> listActiveRoles() {
        return roleRepository.findAllByActiveTrue().stream()
            .map(userMapper::roleToDto)
            .toList()
        ;
    }

    public RoleDto getRole(UUID uuid) {
        return userMapper.roleToDto(findRole(uuid));
    }

    public Set<UUID> getRolePermissions(UUID roleUuid) {
        return findRole(roleUuid).getPermissions().stream()
                .map(Permission::getUuid)
                .collect(Collectors.toSet());
    }

    /**
     * Replace the permission set of {@code roleUuid} with exactly the supplied
     * UUIDs. Guards:
     * <ul>
     *   <li>The {@code SYSTEM} role is editable only by a {@code SYSTEM} actor;
     *       any other caller is rejected with 403.</li>
     *   <li>Every UUID in the request must exist in {@code permissions}; any
     *       unknown UUID rejects the whole call with 422.</li>
     *   <li>Anti-lockout: if the actor currently has {@code ROLE_PERMISSION_EDIT}
     *       only through the role being edited, and the new set drops that
     *       permission, the call is rejected with 422 to avoid the actor
     *       removing their own access to the admin panel.</li>
     * </ul>
     */
    @Transactional
    public void updateRolePermissions(UUID roleUuid, Set<UUID> permissionUuids, UUID actorUuid) {
        Role role = findRole(roleUuid);

        if (SYSTEM_ROLE_NAME.equals(role.getName()) && !isSystemActor(actorUuid)) {
            throw new AccessDeniedException("role.system.not_editable");
        }

        Set<Permission> newPermissions = loadAndValidatePermissions(permissionUuids);

        ensureActorKeepsPermissionEditAfterUpdate(actorUuid, role, newPermissions);

        // Reuse the existing collection instance (clear + addAll) instead of
        // replacing the reference, so Hibernate's orphanRemoval / dirty-check
        // sees a single managed collection rather than a swap.
        role.getPermissions().clear();
        role.getPermissions().addAll(newPermissions);

        // Token-staleness fan-out: every user currently assigned this role has
        // an access token whose `permissions` claim is now wrong. Bump their
        // invalidation epoch so the JwtAuthenticationFilter rejects the stale
        // token on the next request and forces a refresh.
        invalidateAllUsersOf(role);
    }

    /**
     * Create a new role with an empty permission set. The name must be unique
     * across all roles (active or inactive); duplicate names are rejected with
     * 422 ({@code role.name.duplicate}) before the DB constraint fires, so the
     * client gets a clear error instead of the misleading 409 "resource already
     * exists" from the generic {@code DataIntegrityViolationException} handler.
     */
    @Transactional
    public RoleDto create(CreateRoleRequest req) {
        if (roleRepository.findByName(req.name()).isPresent()) {
            throw new IllegalArgumentException("role.name.duplicate");
        }
        Role role = new Role();
        role.setName(req.name());
        role.setDescription(req.description());
        role.setActive(true);
        return userMapper.roleToDto(roleRepository.save(role));
    }

    /**
     * Update name and description. Same uniqueness guard as
     * {@link #create(CreateRoleRequest)} but excluding self, so a no-op rename
     * (same name) is allowed. The {@code SYSTEM} role is editable only by a
     * {@code SYSTEM} actor.
     */
    @Transactional
    public RoleDto update(UUID uuid, UpdateRoleRequest req, UUID actorUuid) {
        Role role = findRole(uuid);
        if (SYSTEM_ROLE_NAME.equals(role.getName()) && !isSystemActor(actorUuid)) {
            throw new AccessDeniedException("role.system.not_editable");
        }
        roleRepository.findByName(req.name())
                .filter(other -> !other.getId().equals(role.getId()))
                .ifPresent(other -> { throw new IllegalArgumentException("role.name.duplicate"); });
        role.setName(req.name());
        role.setDescription(req.description());
        return userMapper.roleToDto(role);  // managed entity, dirty-checked on tx commit
    }

    /**
     * Smart-delete. If the role has any {@code user_roles} row (active or
     * inactive), perform a soft-delete ({@code is_active = false}) to preserve
     * referential integrity and historical FK references. If the role is
     * completely unreferenced, hard-delete the row to keep the table tidy.
     *
     * <p>The {@code SYSTEM} role is never deletable — by anyone, including a
     * {@code SYSTEM} actor. Deletion is strictly more dangerous than editing
     * (it would strip the technical-admin tier of all its permissions), so this
     * guard stays absolute rather than actor-scoped like update.</p>
     */
    @Transactional
    public void delete(UUID uuid) {
        Role role = findRole(uuid);
        if (SYSTEM_ROLE_NAME.equals(role.getName())) {
            throw new AccessDeniedException("role.system.not_deletable");
        }
        if (userRoleRepository.existsByRoleId(role.getId())) {
            role.setActive(false);
            // managed entity → dirty-check on commit
            // Same staleness fan-out as updateRolePermissions: users keep the
            // role in their JWT claim until refresh, but the role is now
            // inactive and its permissions should no longer count.
            invalidateAllUsersOf(role);
        } else {
            roleRepository.delete(role);
            // No fan-out: no one had this role, no token to invalidate.
        }
    }

    /**
     * Fan-out the token-staleness epoch to every user currently assigned the
     * given role (active assignments only — historical/inactive assignments
     * have nothing to invalidate since their refresh flow already re-checks
     * roles). Uses {@code findActiveUserUuidsByRoleId} to avoid the N+1 of
     * walking each {@code UserRole.user.uuid} lazily.
     *
     * <p>Cost is O(N) Redis SET ops where N = active users with this role.
     * Pipelined under the hood by Spring Data Redis when called in tight
     * succession; even AFILIADO at ~100k users is well under a second.</p>
     */
    private void invalidateAllUsersOf(Role role) {
        List<UUID> userUuids = userRoleRepository.findActiveUserUuidsByRoleId(role.getId());
        for (UUID userUuid : userUuids) {
            blacklistService.markUserInvalidatedNow(userUuid.toString());
        }
    }

    private Role findRole(UUID uuid) {
        return roleRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("role.not_found"));
    }

    private Set<Permission> loadAndValidatePermissions(Set<UUID> requestedUuids) {
        if (requestedUuids.isEmpty()) {
            return new HashSet<>();
        }
        Set<Permission> found = new HashSet<>(permissionRepository.findAllByUuidIn(requestedUuids));
        if (found.size() != requestedUuids.size()) {
            Set<UUID> foundUuids = found.stream().map(Permission::getUuid).collect(Collectors.toSet());
            Set<UUID> missing = new HashSet<>(requestedUuids);
            missing.removeAll(foundUuids);
            // The set of missing UUIDs survives in the throw's stack trace
            // for log/debug; the localized user-facing message stays generic.
            throw new IllegalArgumentException("role.permission.uuid.unknown");
        }
        return found;
    }

    /**
     * @return {@code true} if the actor currently holds the {@code SYSTEM} role.
     *         Role names are absent from the JWT / authorities, so the SYSTEM
     *         check must load the actor's role assignments from the DB.
     */
    private boolean isSystemActor(UUID actorUuid) {
        return userRepository.findWithRolesByUuid(actorUuid)
                .map(actor -> actor.getUserRoles().stream()
                        .filter(UserRole::isActive)
                        .anyMatch(ur -> SYSTEM_ROLE_NAME.equals(ur.getRole().getName())))
                .orElse(false);
    }

    private void ensureActorKeepsPermissionEditAfterUpdate(UUID actorUuid, Role editedRole, Set<Permission> newPermissions) {
        User actor = userRepository.findWithRolesByUuid(actorUuid)
                .orElseThrow(() -> new IllegalStateException("Actor user not found: " + actorUuid));

        boolean wouldKeepEditPerm = actor.getUserRoles().stream()
                .filter(ur -> ur.isActive()
                        && (ur.getExpiresAt() == null || ur.getExpiresAt().isAfter(Instant.now())))
                .flatMap(ur -> ur.getRole().getId().equals(editedRole.getId())
                        ? newPermissions.stream()
                        : ur.getRole().getPermissions().stream())
                .anyMatch(p -> ROLE_PERMISSION_EDIT.equals(p.getName()));

        if (!wouldKeepEditPerm) {
            throw new IllegalArgumentException("role.auto_lockout");
        }
    }
}
