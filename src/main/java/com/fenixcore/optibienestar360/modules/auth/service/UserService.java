package com.fenixcore.optibienestar360.modules.auth.service;

import com.fenixcore.optibienestar360.modules.auth.dto.AdminCreateUserRequest;
import com.fenixcore.optibienestar360.modules.auth.dto.AdminUpdateUserRequest;
import com.fenixcore.optibienestar360.modules.auth.dto.UserDto;
import com.fenixcore.optibienestar360.modules.auth.entity.Role;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.entity.UserRole;
import com.fenixcore.optibienestar360.modules.auth.mapper.UserMapper;
import com.fenixcore.optibienestar360.modules.auth.repository.RoleRepository;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRoleRepository;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyUserRepository;
import com.fenixcore.optibienestar360.modules.catalog.dto.UsageDto;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import com.fenixcore.optibienestar360.core.dto.OptionDto;
import com.fenixcore.optibienestar360.core.util.OptionsSupport;
import com.fenixcore.optibienestar360.core.util.RsqlFieldValidator;
import com.fenixcore.optibienestar360.core.util.SearchSpecifications;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import com.fenixcore.optibienestar360.modules.person.service.PersonService;
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
            "email", "person.fullName", "status", "person.documentType",
            "person.documentNumber", "active", "createdAt");

    /**
     * Free-text {@code ?q=} fields. {@code person.fullName} is a Postgres
     * GENERATED column with a GIN unaccent index from V15, so the search is
     * accent-insensitive ({@code "jose"} matches {@code "José"}) at no extra cost.
     */
    private static final String[] SEARCHABLE_FIELDS = {
            "email", "person.fullName", "person.documentNumber"
    };

    private static final String SYSTEM_ROLE_NAME = "SYSTEM";
    private static final String ACTIVE_STATUS = "ACTIVE";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenBlacklistService blacklistService;
    private final PersonService personService;
    private final AllyUserRepository allyUserRepository;
    private final PromoterRepository promoterRepository;

    // ─── /v1/me ───────────────────────────────────────────────────────────────

    public UserDto getMe(UUID userUuid) {
        User user = userRepository.findWithRolesByUuid(userUuid)
                .orElseThrow(() -> new NoSuchElementException("user.not_found"));
        return userMapper.toDto(user);
    }

    // ─── Admin CRUD ───────────────────────────────────────────────────────────

    public Page<UserDto> listUsers(String filter, String q, boolean includeInactive, Pageable pageable, UUID actorUuid) {
        Specification<User> spec = includeInactive
                ? (root, query, cb) -> cb.conjunction()
                : (root, query, cb) -> cb.equal(root.get("active"), Boolean.TRUE);
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS, "user.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        // Only SYSTEM actors may see SYSTEM users; hide them from everyone else.
        if (!isSystemActor(actorUuid)) {
            spec = spec.and(excludeSystemUsers());
        }
        return userRepository.findAll(spec, pageable).map(userMapper::toDto);
    }

    /** Lightweight options for select/dropdown population — see {@link OptionsSupport}. {@code code} is always null (User has no own code). */
    public List<OptionDto> listOptions(String q, int limit, List<UUID> currentValues) {
        Specification<User> spec = ((Specification<User>) (root, query, cb) -> cb.isTrue(root.get("active")))
                .and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        return OptionsSupport.build(userRepository, userRepository::findByUuid, spec, currentValues, limit,
                User::getUuid, user -> null, user -> user.getPerson().getFullName(), User::isActive);
    }

    public UserDto getUser(UUID uuid, UUID actorUuid) {
        User user = userRepository.findWithRolesByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("user.not_found"));
        // A non-SYSTEM actor cannot view a SYSTEM user's detail.
        if (hasSystemRole(user) && !isSystemActor(actorUuid)) {
            throw new AccessDeniedException("user.system.not_viewable");
        }
        return userMapper.toDto(user);
    }

    @Transactional
    public UserDto createUser(AdminCreateUserRequest request, UUID actorUuid) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("user.email.exists");
        }

        // Only a SYSTEM actor may create a user carrying the SYSTEM role.
        if (requestsSystemRole(request.roleIds()) && !isSystemActor(actorUuid)) {
            throw new AccessDeniedException("user.system.role_not_assignable");
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
     * Admin update. Three guards:
     * <ul>
     *   <li><b>SYSTEM user protection</b> — a user carrying the {@code SYSTEM}
     *       role may be edited <em>only</em> by another {@code SYSTEM} actor.
     *       Any non-SYSTEM admin that somehow obtains {@code USER_UPDATE} is
     *       still blocked, so the technical-admin tier stays self-governing.</li>
     *   <li><b>SYSTEM role assignment</b> — granting the {@code SYSTEM} role to
     *       the target requires the actor to be a {@code SYSTEM} user; a
     *       non-SYSTEM admin cannot escalate anyone (incl. self) to SYSTEM.</li>
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

        // Whether the actor holds the SYSTEM role. For self-edits the target is
        // the actor, so reuse the already-loaded entity instead of a 2nd lookup.
        boolean actorIsSystem = uuid.equals(actorUuid) ? hasSystemRole(user) : isSystemActor(actorUuid);

        // SYSTEM users are editable only by other SYSTEM users.
        if (hasSystemRole(user) && !actorIsSystem) {
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

        // Only a SYSTEM actor may grant the SYSTEM role to the target.
        if (requestsSystemRole(request.roleIds()) && !actorIsSystem) {
            throw new AccessDeniedException("user.system.role_not_assignable");
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
     * Counts real functional FK references to this user — deliberately
     * excludes actor-stamp columns that merely record "who did this"
     * historically ({@code Payment.reviewedBy}/{@code discountedBy},
     * {@code MemberDocument.uploadedBy}, {@code Subsidy.authorizedBy},
     * {@code SubsidyAuditLog.actor}, {@code AllyService.reviewedBy},
     * {@code AllyServiceReviewLog.actor}, {@code MemberPromoterAssignment.actor},
     * {@code CorporateContract.contactUser}, {@code Payment.payerUser}), which
     * behave like {@code createdBy}/{@code updatedBy} audit metadata rather
     * than an ownership/membership relationship — blocking a hard delete over
     * a historical "who approved/paid this" stamp would be overly strict.
     * Counts only structural relationships: role assignments ({@code
     * user_roles}), ally memberships ({@code ally_users}), and promoter
     * accounts ({@code promoters.user_id}).
     */
    public long countUsages(UUID uuid) {
        User user = userRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("user.not_found"));
        long roles = userRoleRepository.countByUserId(user.getId());
        long allyMemberships = allyUserRepository.countByUserId(user.getId());
        long promoterAccounts = promoterRepository.countByUserId(user.getId());
        return roles + allyMemberships + promoterAccounts;
    }

    public UsageDto getUsage(UUID uuid) {
        long count = countUsages(uuid);
        return new UsageDto(count > 0, count);
    }

    /**
     * Admin soft-delete (sets active=false + status=SUSPENDED + revokes
     * tokens), extended with the shared {@code physical} flag. Same two
     * anti-lockout guards as {@link #updateUser}:
     * <ul>
     *   <li>Actor cannot delete themselves (would lock the actor out and
     *       require another admin's intervention).</li>
     *   <li>The SYSTEM seed user is not deletable.</li>
     * </ul>
     * {@code physical} is re-verified against {@link #countUsages} at delete
     * time (never trusted blindly) to close the race between the usage check
     * and the delete; omitting it (default {@code false}) reproduces the
     * prior soft-delete-only behavior exactly.
     */
    @Transactional
    public void deleteUser(UUID uuid, UUID actorUuid, boolean physical) {
        if (uuid.equals(actorUuid)) {
            throw new IllegalArgumentException("user.self.cannot_delete");
        }
        User user = userRepository.findWithRolesByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("user.not_found"));
        if (hasSystemRole(user)) {
            throw new AccessDeniedException("user.system.not_deletable");
        }
        long usages = countUsages(uuid);
        if (physical && usages == 0) {
            userRepository.delete(user);
            blacklistService.revokeAllUserRefreshTokens(uuid.toString());
            blacklistService.markUserInvalidatedNow(uuid.toString());
            return;
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

    /**
     * @return {@code true} if the actor identified by {@code actorUuid}
     *         currently holds the {@code SYSTEM} role. Loads the actor with its
     *         roles; returns {@code false} if the actor no longer exists. This
     *         is the only reliable SYSTEM check for the actor — role names are
     *         not carried in the JWT / {@code CustomUserDetails}, only permissions.
     */
    private boolean isSystemActor(UUID actorUuid) {
        return userRepository.findWithRolesByUuid(actorUuid)
                .map(this::hasSystemRole)
                .orElse(false);
    }

    /**
     * @return {@code true} if {@code roleIds} contains the {@code SYSTEM} role.
     *         Used to gate SYSTEM-role assignment on create/update. A {@code null}
     *         or empty list, or an environment with no SYSTEM role, yields
     *         {@code false}.
     */
    private boolean requestsSystemRole(List<UUID> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return false;
        }
        return roleRepository.findByName(SYSTEM_ROLE_NAME)
                .map(systemRole -> roleIds.contains(systemRole.getUuid()))
                .orElse(false);
    }

    /**
     * Specification excluding any user with an active assignment to the
     * {@code SYSTEM} role — the list-endpoint counterpart of the detail-view
     * guard. Correlated {@code NOT EXISTS} against {@code user_roles}, so it
     * composes with the RSQL filter and survives the pagination count query.
     */
    private Specification<User> excludeSystemUsers() {
        return (root, query, cb) -> {
            var sub = query.subquery(Long.class);
            var urRoot = sub.from(UserRole.class);
            sub.select(urRoot.get("id")).where(
                    cb.equal(urRoot.get("user"), root),
                    cb.isTrue(urRoot.get("active")),
                    cb.equal(urRoot.get("role").get("name"), SYSTEM_ROLE_NAME));
            return cb.not(cb.exists(sub));
        };
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
