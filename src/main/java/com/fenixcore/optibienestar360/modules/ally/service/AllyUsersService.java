package com.fenixcore.optibienestar360.modules.ally.service;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.Auditable;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyUserCreateRequest;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyUserDto;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyUserUpdateRequest;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser.AllyRole;
import com.fenixcore.optibienestar360.modules.ally.mapper.AllyMapper;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyRepository;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyUserRepository;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/**
 * Admin-side management of {@link AllyUser} memberships for a parent
 * {@link Ally}.
 *
 * <p>Three invariants enforced here (mirroring the V12 schema):</p>
 * <ol>
 *   <li><b>UNIQUE (ally_id, user_id)</b> — creating a duplicate membership
 *       for the same (ally, user) pair reactivates the existing row instead
 *       of inserting a second one. Without this, the DB constraint would
 *       throw a misleading 409.</li>
 *   <li><b>{@code is_primary=true} requires {@code allyRole='OWNER'}</b> —
 *       CHECK {@code chk_ally_users_primary_is_owner}. Rejected with 422
 *       before the DB even sees the write.</li>
 *   <li><b>At most one active primary per ally</b> — partial unique index
 *       {@code uniq_ally_users_one_primary_per_ally}. If a second primary
 *       is requested, this service rejects with 422 — the admin must
 *       demote the existing primary first.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AllyUsersService {

    private final AllyRepository allyRepository;
    private final AllyUserRepository allyUserRepository;
    private final UserRepository userRepository;
    private final AllyMapper mapper;

    public List<AllyUserDto> listForAlly(UUID allyUuid) {
        Ally ally = findAlly(allyUuid);
        return allyUserRepository.findByAllyIdAndActiveTrue(ally.getId()).stream()
                .map(mapper::toAllyUserDto)
                .toList();
    }

    public AllyUserDto get(UUID allyUuid, UUID membershipUuid) {
        return mapper.toAllyUserDto(findMembershipUnderAlly(allyUuid, membershipUuid));
    }

    @Transactional
    @Auditable(entity = "ally_user", action = AuditAction.CREATE)
    public AllyUserDto add(UUID allyUuid, AllyUserCreateRequest req) {
        Ally ally = findAlly(allyUuid);
        User user = userRepository.findByUuid(req.userUuid())
                .orElseThrow(() -> new NoSuchElementException("user.not_found"));

        validatePrimaryCoherence(req.allyRole(), req.primary());

        // Reactivate-on-readmission per the V12 schema design (UNIQUE on
        // ally_id, user_id — inactive rows count).
        Optional<AllyUser> existing = allyUserRepository.findByAllyIdAndUserId(ally.getId(), user.getId());
        AllyUser membership = existing.orElseGet(AllyUser::new);
        membership.setAlly(ally);
        membership.setUser(user);
        membership.setAllyRole(req.allyRole());
        membership.setPrimary(Boolean.TRUE.equals(req.primary()));
        membership.setJoinedAt(req.joinedAt());
        membership.setActive(true);

        if (membership.isPrimary()) {
            ensureNoOtherActivePrimary(ally, membership);
        }

        return mapper.toAllyUserDto(allyUserRepository.save(membership));
    }

    @Transactional
    @Auditable(entity = "ally_user", action = AuditAction.UPDATE, uuidArgIndex = 1)
    public AllyUserDto update(UUID allyUuid, UUID membershipUuid, AllyUserUpdateRequest req) {
        AllyUser membership = findMembershipUnderAlly(allyUuid, membershipUuid);

        AllyRole nextRole = req.allyRole() != null ? req.allyRole() : membership.getAllyRole();
        boolean nextPrimary = req.primary() != null ? req.primary() : membership.isPrimary();
        validatePrimaryCoherence(nextRole, nextPrimary);

        if (req.allyRole() != null) membership.setAllyRole(req.allyRole());
        if (req.joinedAt() != null) membership.setJoinedAt(req.joinedAt());
        if (req.active() != null)   membership.setActive(req.active());
        if (req.primary() != null)  membership.setPrimary(req.primary());

        if (membership.isPrimary() && membership.isActive()) {
            ensureNoOtherActivePrimary(membership.getAlly(), membership);
        }

        return mapper.toAllyUserDto(membership);
    }

    @Transactional
    @Auditable(entity = "ally_user", action = AuditAction.DELETE, uuidArgIndex = 1)
    public void delete(UUID allyUuid, UUID membershipUuid) {
        AllyUser membership = findMembershipUnderAlly(allyUuid, membershipUuid);
        membership.setActive(false);
        membership.setPrimary(false);  // free the primary slot for another OWNER
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private Ally findAlly(UUID uuid) {
        return allyRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("ally.not_found"));
    }

    private AllyUser findMembershipUnderAlly(UUID allyUuid, UUID membershipUuid) {
        AllyUser membership = allyUserRepository.findByUuid(membershipUuid)
                .orElseThrow(() -> new NoSuchElementException("ally_user.not_found"));
        if (!membership.getAlly().getUuid().equals(allyUuid)) {
            throw new NoSuchElementException("ally_user.not_found");
        }
        return membership;
    }

    private void validatePrimaryCoherence(AllyRole role, Boolean primary) {
        if (Boolean.TRUE.equals(primary) && role != AllyRole.OWNER) {
            throw new IllegalArgumentException("ally_user.primary_requires_owner");
        }
    }

    /**
     * Rejects when another active OWNER on the same ally already has
     * {@code primary=true} (excluding the membership being saved). The
     * caller must demote the existing primary explicitly before promoting a
     * new one — avoids the partial unique index throwing a 409 mid-tx.
     */
    private void ensureNoOtherActivePrimary(Ally ally, AllyUser candidate) {
        Optional<AllyUser> currentPrimary =
                allyUserRepository.findFirstByAllyIdAndPrimaryTrueAndActiveTrue(ally.getId());
        currentPrimary
                .filter(other -> !other.getId().equals(candidate.getId()))
                .ifPresent(other -> {
                    throw new IllegalArgumentException("ally_user.primary_already_set");
                });
    }
}
