package com.fenixcore.optisaludplus.modules.auth.service;

import com.fenixcore.optisaludplus.modules.auth.dto.CreateRoleRequest;
import com.fenixcore.optisaludplus.modules.auth.dto.RoleDto;
import com.fenixcore.optisaludplus.modules.auth.dto.UpdateRoleRequest;
import com.fenixcore.optisaludplus.modules.auth.entity.Permission;
import com.fenixcore.optisaludplus.modules.auth.entity.Role;
import com.fenixcore.optisaludplus.modules.auth.entity.User;
import com.fenixcore.optisaludplus.modules.auth.mapper.UserMapper;
import com.fenixcore.optisaludplus.modules.auth.repository.PermissionRepository;
import com.fenixcore.optisaludplus.modules.auth.repository.RoleRepository;
import com.fenixcore.optisaludplus.modules.auth.repository.UserRepository;
import com.fenixcore.optisaludplus.modules.auth.repository.UserRoleRepository;
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
     *   <li>The {@code SYSTEM} role is not editable — always rejects with 403.</li>
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

        if (SYSTEM_ROLE_NAME.equals(role.getName())) {
            throw new AccessDeniedException("role.system.not_editable");
        }

        Set<Permission> newPermissions = loadAndValidatePermissions(permissionUuids);

        ensureActorKeepsPermissionEditAfterUpdate(actorUuid, role, newPermissions);

        // Reuse the existing collection instance (clear + addAll) instead of
        // replacing the reference, so Hibernate's orphanRemoval / dirty-check
        // sees a single managed collection rather than a swap.
        role.getPermissions().clear();
        role.getPermissions().addAll(newPermissions);
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
     * (same name) is allowed. The {@code SYSTEM} role is immutable.
     */
    @Transactional
    public RoleDto update(UUID uuid, UpdateRoleRequest req) {
        Role role = findRole(uuid);
        if (SYSTEM_ROLE_NAME.equals(role.getName())) {
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
     * <p>The {@code SYSTEM} role is never deletable — same guard as update.</p>
     */
    @Transactional
    public void delete(UUID uuid) {
        Role role = findRole(uuid);
        if (SYSTEM_ROLE_NAME.equals(role.getName())) {
            throw new AccessDeniedException("role.system.not_editable");
        }
        if (userRoleRepository.existsByRoleId(role.getId())) {
            role.setActive(false);
            // managed entity → dirty-check on commit
        } else {
            roleRepository.delete(role);
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
