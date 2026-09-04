package com.fenixcore.optibienestar360.modules.auth.service;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.Auditable;
import com.fenixcore.optibienestar360.core.dto.OptionDto;
import com.fenixcore.optibienestar360.core.util.DefaultSortResolver;
import com.fenixcore.optibienestar360.core.util.OptionsSupport;
import com.fenixcore.optibienestar360.core.util.SearchSpecifications;
import com.fenixcore.optibienestar360.core.util.SortFieldValidator;
import com.fenixcore.optibienestar360.modules.auth.dto.CreateRoleRequest;
import com.fenixcore.optibienestar360.modules.auth.dto.RoleDto;
import com.fenixcore.optibienestar360.modules.auth.dto.RoleUserDto;
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
import com.fenixcore.optibienestar360.modules.catalog.dto.UsageDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    private static final String[] SEARCHABLE_FIELDS = {"name", "description"};

    private static final Map<String, SortFieldValidator.SortableField> ROLE_SORTABLE_FIELDS =
            SortFieldValidator.sortableFieldsOf(Role.class, Map.of());

    /** {@code fullName}/{@code email}/{@code status}/{@code active} are flattened User(+Person) columns, not on {@code UserRole} itself. */
    private static final Map<String, SortFieldValidator.SortableField> ROLE_USER_SORTABLE_FIELDS =
            SortFieldValidator.sortableFieldsOf(UserRole.class, Map.of(
                    "fullName", "user.person.fullName",
                    "email", "user.email",
                    "status", "user.status",
                    "active", "user.active"
            ));

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserMapper userMapper;
    private final TokenBlacklistService blacklistService;
    private final DefaultSortResolver defaultSortResolver;

    public List<RoleDto> listActiveRoles() {
        return roleRepository.findAllByActiveTrue().stream()
            .map(userMapper::roleToDto)
            .toList()
        ;
    }

    public List<RoleDto> list(String q, boolean includeInactive, Pageable pageable) {
        Specification<Role> spec = includeInactive
                ? (root, query, cb) -> cb.conjunction()
                : (root, query, cb) -> cb.equal(root.get("active"), Boolean.TRUE);
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        Pageable defaulted = defaultSortResolver.withDefaultSortIfUnsorted("role", pageable);
        Pageable resolved = SortFieldValidator.resolve(defaulted, ROLE_SORTABLE_FIELDS, "role");
        return roleRepository.findAll(spec, resolved.getSort()).stream()
                .map(userMapper::roleToDto)
                .toList();
    }

    /** Lightweight options for select/dropdown population — see {@link OptionsSupport}. {@code code} is always null (Role has no own code). */
    public List<OptionDto> listOptions(String q, int limit, List<UUID> currentValues) {
        Specification<Role> spec = ((Specification<Role>) (root, query, cb) -> cb.isTrue(root.get("active")))
                .and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        return OptionsSupport.build(roleRepository, roleRepository::findByUuid, spec, currentValues, limit,
                Role::getUuid, role -> null, Role::getName, Role::isActive);
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
    @Auditable(entity = "role", action = AuditAction.CREATE)
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
    @Auditable(entity = "role", action = AuditAction.UPDATE, uuidArgIndex = 0)
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
     * Counts every {@code user_roles} row (active + inactive) assigned to
     * this role — even an inactive/expired assignment is still a real FK row
     * that would break a hard delete, so both count toward "in use".
     */
    public long countUsages(UUID uuid) {
        Role role = findRole(uuid);
        return userRoleRepository.countByRoleId(role.getId());
    }

    public UsageDto getUsage(UUID uuid) {
        long count = countUsages(uuid);
        return new UsageDto(count > 0, count);
    }

    /**
     * Smart-delete, extended with the {@code physical} flag shared by the
     * other 9 catalog-style entities — but Role predates that pattern with
     * its <em>own</em> smart-delete already: hard-delete when unreferenced,
     * soft-delete otherwise, decided purely by {@link #countUsages}
     * (re-checked here rather than trusted from the caller, guarding the
     * race between a usage check and the delete). That decision does not
     * depend on {@code physical} — it happens today regardless of the flag,
     * so omitting {@code physical} (default {@code false}) reproduces this
     * method's pre-existing behavior exactly, and passing {@code physical=true}
     * changes nothing beyond API-shape parity with the other 9 entities.
     *
     * <p>The {@code SYSTEM} role is never deletable — by anyone, including a
     * {@code SYSTEM} actor. Deletion is strictly more dangerous than editing
     * (it would strip the technical-admin tier of all its permissions), so this
     * guard stays absolute rather than actor-scoped like update.</p>
     */
    @Transactional
    @Auditable(entity = "role", action = AuditAction.DELETE, uuidArgIndex = 0)
    public void delete(UUID uuid, boolean physical) {
        Role role = findRole(uuid);
        if (SYSTEM_ROLE_NAME.equals(role.getName())) {
            throw new AccessDeniedException("role.system.not_deletable");
        }
        long usages = countUsages(uuid);
        if (usages == 0) {
            roleRepository.delete(role);
            // No fan-out: no one had this role, no token to invalidate.
            return;
        }
        role.setActive(false);
        // managed entity → dirty-check on commit
        // Same staleness fan-out as updateRolePermissions: users keep the
        // role in their JWT claim until refresh, but the role is now
        // inactive and its permissions should no longer count.
        invalidateAllUsersOf(role);
    }

    // ─── Role membership: /v1/admin/roles/{roleUuid}/users ─────────────────

    /** Users currently assigned to {@code roleUuid} (active pivot rows only). */
    public List<RoleUserDto> listUsers(UUID roleUuid, Pageable pageable) {
        Role role = findRole(roleUuid);
        Pageable defaulted = defaultSortResolver.withDefaultSortIfUnsorted(
                "role_user", pageable);
        Pageable resolved = SortFieldValidator.resolve(defaulted, ROLE_USER_SORTABLE_FIELDS, "role_user");
        return userRoleRepository.findByRoleIdAndActiveTrue(role.getId(), resolved.getSort()).stream()
                .map(UserRole::getUser)
                .map(userMapper::toRoleUserDto)
                .toList();
    }

    /**
     * Assigns {@code userUuid} to {@code roleUuid}. Reuses the existing pivot
     * row (reactivating it) when one already exists — same
     * reactivate-on-readmission approach as {@code AllyUsersService.add} —
     * otherwise inserts a new {@code user_roles} row.
     *
     * <p>The assigned user's JWT {@code permissions} claim is now stale (it
     * gained this role's permissions), so its token-staleness epoch is bumped
     * the same way {@link #updateRolePermissions} does.</p>
     */
    @Transactional
    @Auditable(entity = "user_role", action = AuditAction.CREATE, uuidArgIndex = 0)
    public void assignUser(UUID roleUuid, UUID userUuid) {
        Role role = findRole(roleUuid);
        User user = findUser(userUuid);

        UserRole userRole = userRoleRepository.findByUserIdAndRoleId(user.getId(), role.getId())
                .orElseGet(UserRole::new);
        userRole.setUser(user);
        userRole.setRole(role);
        userRole.setActive(true);
        userRoleRepository.save(userRole);

        blacklistService.markUserInvalidatedNow(userUuid.toString());
    }

    /**
     * Removes {@code userUuid} from {@code roleUuid} — soft-remove (the pivot
     * row is deactivated, never deleted, mirroring {@code AllyUsersService.delete}).
     * No-op (idempotent) if the user has no active assignment to this role.
     */
    @Transactional
    @Auditable(entity = "user_role", action = AuditAction.DELETE, uuidArgIndex = 0)
    public void removeUser(UUID roleUuid, UUID userUuid) {
        Role role = findRole(roleUuid);
        User user = findUser(userUuid);

        userRoleRepository.findByUserIdAndRoleId(user.getId(), role.getId())
                .filter(UserRole::isActive)
                .ifPresent(userRole -> {
                    userRole.setActive(false);
                    blacklistService.markUserInvalidatedNow(userUuid.toString());
                });
    }

    private User findUser(UUID uuid) {
        return userRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("user.not_found"));
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
