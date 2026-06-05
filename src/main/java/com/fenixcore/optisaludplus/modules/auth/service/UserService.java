package com.fenixcore.optisaludplus.modules.auth.service;

import com.fenixcore.optisaludplus.modules.auth.dto.AdminCreateUserRequest;
import com.fenixcore.optisaludplus.modules.auth.dto.AdminUpdateUserRequest;
import com.fenixcore.optisaludplus.modules.auth.dto.UserDto;
import com.fenixcore.optisaludplus.modules.auth.entity.Role;
import com.fenixcore.optisaludplus.modules.auth.entity.User;
import com.fenixcore.optisaludplus.modules.auth.entity.UserRole;
import com.fenixcore.optisaludplus.modules.auth.mapper.UserMapper;
import com.fenixcore.optisaludplus.modules.auth.repository.RoleRepository;
import com.fenixcore.optisaludplus.modules.auth.repository.UserRepository;
import com.fenixcore.optisaludplus.modules.auth.repository.UserRoleRepository;
import com.fenixcore.optisaludplus.modules.person.entity.Person;
import com.fenixcore.optisaludplus.modules.person.service.PersonService;
import io.github.perplexhub.rsql.RSQLJPASupport;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of(
            "email", "fullName", "status", "documentType", "documentNumber", "active", "createdAt");

    private static final String SYSTEM_ROLE_NAME = "SYSTEM";
    private static final String ACTIVE_STATUS = "ACTIVE";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenBlacklistService blacklistService;
    private final PersonService personService;

    // ─── /v1/me ───────────────────────────────────────────────────────────────

    public UserDto getMe(UUID userUuid) {
        User user = userRepository.findWithRolesByUuid(userUuid)
                .orElseThrow(() -> new NoSuchElementException("user.not_found"));
        return userMapper.toDto(user);
    }

    // ─── Admin CRUD ───────────────────────────────────────────────────────────

    public Page<UserDto> listUsers(String filter, Pageable pageable) {
        Specification<User> spec = (root, query, cb) -> null;
        if (filter != null && !filter.isBlank()) {
            validateFilterFields(filter);
            spec = RSQLJPASupport.toSpecification(filter);
        }
        return userRepository.findAll(spec, pageable).map(userMapper::toDto);
    }

    private void validateFilterFields(String filter) {
        // Reject any field name not in the allow-list to prevent JPA association traversal
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("([a-zA-Z][a-zA-Z0-9]*(?:\\.[a-zA-Z][a-zA-Z0-9]*)*)\\s*[=!<>]")
                .matcher(filter);
        while (matcher.find()) {
            String field = matcher.group(1).split("\\.")[0];
            if (!ALLOWED_FILTER_FIELDS.contains(field)) {
                // The field name is not embedded in the localized message
                // (no args support on IllegalArgumentException); the value
                // arrives in logs via the throw's stack trace.
                throw new IllegalArgumentException("user.filter.field_not_allowed");
            }
        }
    }

    public UserDto getUser(UUID uuid) {
        User user = userRepository.findWithRolesByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("user.not_found"));
        return userMapper.toDto(user);
    }

    @Transactional
    public UserDto createUser(AdminCreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("user.email.exists");
        }

        // Resolve or create the Person hub for this cédula. If a Member or
        // another User already exists for the same cédula, the same persons
        // row is reused so the same human is never duplicated across roles.
        Person seed = new Person();
        seed.setFirstName(request.firstName());
        seed.setMiddleName(request.middleName());
        seed.setLastName(request.lastName());
        seed.setSecondLastName(request.secondLastName());
        seed.setDocumentType(request.documentType());
        seed.setDocumentNumber(request.documentNumber());
        seed.setTaxDocumentType(request.taxDocumentType());
        seed.setTaxDocumentNumber(request.taxDocumentNumber());
        seed.setPhone(request.phone());
        seed.setEmail(request.email());
        Person person = personService.findOrCreate(seed);

        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setPerson(person);
        user.setStatus("ACTIVE");
        userRepository.save(user);

        assignRoles(user, request.roleIds());
        return userMapper.toDto(userRepository.findWithRolesByUuid(user.getUuid()).orElseThrow());
    }

    /**
     * Admin update. Two anti-lockout guards:
     * <ul>
     *   <li><b>SYSTEM user immutability</b> — the seeded bootstrap user (the
     *       one carrying the {@code SYSTEM} role) is hardcoded immutable
     *       regardless of who edits it. Mirrors {@code RoleService}'s
     *       {@code SYSTEM} role guard, but at the user layer so the seed
     *       account survives even if an admin somehow obtains
     *       {@code USER_UPDATE}.</li>
     *   <li><b>Self-edit restrictions</b> — when {@code actorUuid == uuid}:
     *       the actor cannot deactivate themselves, change their own status
     *       to anything other than ACTIVE, or alter their own roles. The
     *       roles block is intentionally coarse (any change → reject) rather
     *       than computing "would I keep ROLE_PERMISSION_EDIT after this",
     *       so the guard cannot be defeated by a wrong-but-plausible role
     *       set. Self-edits to profile fields (fullName, phone, locale,
     *       documentType, documentNumber) are still allowed.</li>
     * </ul>
     */
    @Transactional
    public UserDto updateUser(UUID uuid, AdminUpdateUserRequest request, UUID actorUuid) {
        User user = userRepository.findWithRolesByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("user.not_found"));

        if (hasSystemRole(user)) {
            throw new AccessDeniedException("user.system.not_editable");
        }

        if (uuid.equals(actorUuid)) {
            if (Boolean.FALSE.equals(request.active())) {
                throw new IllegalArgumentException("user.self.cannot_deactivate");
            }
            if (request.status() != null && !ACTIVE_STATUS.equals(request.status())) {
                throw new IllegalArgumentException("user.self.cannot_change_own_status");
            }
            if (request.roleIds() != null && !request.roleIds().isEmpty()) {
                throw new IllegalArgumentException("user.self.cannot_change_own_roles");
            }
        }

        // Person fields → mutate the linked persons row (managed → dirty-check on commit)
        Person person = user.getPerson();
        if (request.firstName()         != null) person.setFirstName(request.firstName());
        if (request.middleName()        != null) person.setMiddleName(request.middleName());
        if (request.lastName()          != null) person.setLastName(request.lastName());
        if (request.secondLastName()    != null) person.setSecondLastName(request.secondLastName());
        if (request.documentType()      != null) person.setDocumentType(request.documentType());
        if (request.documentNumber()    != null) person.setDocumentNumber(request.documentNumber());
        if (request.taxDocumentType()   != null) person.setTaxDocumentType(request.taxDocumentType());
        if (request.taxDocumentNumber() != null) person.setTaxDocumentNumber(request.taxDocumentNumber());
        if (request.phone()             != null) person.setPhone(request.phone());
        if (request.locale()            != null) person.setLocale(request.locale());

        // User-level fields
        if (request.status() != null) user.setStatus(request.status());
        if (request.active() != null) user.setActive(request.active());

        boolean rolesChanged = request.roleIds() != null && !request.roleIds().isEmpty();
        if (rolesChanged) {
            syncRoles(user, request.roleIds());
        }

        userRepository.save(user);

        // Token-staleness: when role assignment changes, the user's current
        // access token still carries the old `permissions` claim. Bump the
        // invalidation epoch so the JwtAuthenticationFilter rejects the stale
        // token on the next request and forces a refresh.
        if (rolesChanged) {
            blacklistService.markUserInvalidatedNow(uuid.toString());
        }

        return userMapper.toDto(userRepository.findWithRolesByUuid(uuid).orElseThrow());
    }

    /**
     * Admin soft-delete (sets active=false + status=SUSPENDED + revokes
     * tokens). Same two anti-lockout guards as {@link #updateUser}:
     * <ul>
     *   <li>Actor cannot delete themselves (would lock the actor out and
     *       require another admin's intervention).</li>
     *   <li>The SYSTEM seed user is not deletable.</li>
     * </ul>
     */
    @Transactional
    public void deleteUser(UUID uuid, UUID actorUuid) {
        if (uuid.equals(actorUuid)) {
            throw new IllegalArgumentException("user.self.cannot_delete");
        }
        User user = userRepository.findWithRolesByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("user.not_found"));
        if (hasSystemRole(user)) {
            throw new AccessDeniedException("user.system.not_deletable");
        }
        user.setActive(false);
        user.setStatus("SUSPENDED");
        userRepository.save(user);
        blacklistService.revokeAllUserRefreshTokens(uuid.toString());
        // Close the access-token window too: refresh revocation alone leaves
        // up to 15 min during which the existing access token still works.
        blacklistService.markUserInvalidatedNow(uuid.toString());
    }

    /**
     * @return {@code true} if {@code user} currently has an active assignment
     *         to the {@code SYSTEM} role. Requires the {@code userRoles}
     *         collection to be populated — callers should load via
     *         {@code findWithRolesByUuid}, not {@code findByUuid}.
     */
    private boolean hasSystemRole(User user) {
        return user.getUserRoles().stream()
                .filter(UserRole::isActive)
                .anyMatch(ur -> SYSTEM_ROLE_NAME.equals(ur.getRole().getName()));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void assignRoles(User user, List<UUID> roleIds) {
        List<UserRole> newRoles = new ArrayList<>();
        for (UUID roleUuid : roleIds) {
            Role role = roleRepository.findByUuid(roleUuid)
                    .orElseThrow(() -> new NoSuchElementException("role.not_found"));
            UserRole ur = new UserRole();
            ur.setUser(user);
            ur.setRole(role);
            newRoles.add(ur);
        }
        userRoleRepository.saveAll(newRoles);
    }

    /**
     * Reconciles the user's role pivot rows to match the requested set.
     * Reactivates or deactivates existing rows and inserts only roles that
     * have no pivot row yet, avoiding the unique (user_id, role_id) collision
     * that a deactivate-then-reinsert approach would trigger.
     */
    private void syncRoles(User user, List<UUID> roleIds) {
        // Resolve requested roles up-front (validates existence) keyed by role id
        Map<Long, Role> requested = new LinkedHashMap<>();
        for (UUID roleUuid : roleIds) {
            Role role = roleRepository.findByUuid(roleUuid)
                    .orElseThrow(() -> new NoSuchElementException("role.not_found"));
            requested.put(role.getId(), role);
        }

        List<UserRole> toSave = new ArrayList<>();

        // Flip active flag on existing rows to match the requested set
        for (UserRole ur : userRoleRepository.findByUserId(user.getId())) {
            Long roleId = ur.getRole().getId();
            boolean shouldBeActive = requested.containsKey(roleId);
            if (ur.isActive() != shouldBeActive) {
                ur.setActive(shouldBeActive);
                toSave.add(ur);
            }
            requested.remove(roleId); // already represented by a pivot row
        }

        // Insert pivot rows only for roles with no existing row
        for (Role role : requested.values()) {
            UserRole ur = new UserRole();
            ur.setUser(user);
            ur.setRole(role);
            toSave.add(ur);
        }

        userRoleRepository.saveAll(toSave);
    }
}
