package com.fenixcore.optibienestar360.modules.onboarding.service;

import com.fenixcore.optibienestar360.modules.affiliation.entity.AffiliationRequest;
import com.fenixcore.optibienestar360.modules.affiliation.repository.AffiliationRequestRepository;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser.AllyRole;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyRepository;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyUserRepository;
import com.fenixcore.optibienestar360.modules.ally.service.AllyDraftAccess;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.entity.UserRole;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRoleRepository;
import com.fenixcore.optibienestar360.modules.auth.service.TokenBlacklistService;
import com.fenixcore.optibienestar360.modules.media.MediaTables;
import com.fenixcore.optibienestar360.modules.media.repository.MediaItemRepository;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import com.fenixcore.optibienestar360.security.RoleNames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Discards registrations that are still in progress (hub ADR 0037 §2) and cleans the abandoned ones.
 *
 * <p><b>What "discard" means.</b> A registration that was never sent to review is not a record anyone
 * has to keep: the request (and, for an ally, the ally row with its hours, types and gallery) is
 * <em>deleted</em>. What cannot be deleted is the login created in step 1 of a self-registration:
 * audit logs, sessions and accepted terms reference it. So the login is <em>tombstoned</em> — email
 * and cédula are rewritten to unique placeholders, the user is suspended and its tokens revoked —
 * which frees the real cédula and email for a new registration without touching history. An advisor's
 * assisted draft never had a login, so there is nothing to tombstone.</p>
 *
 * <p>Only DRAFT / not-yet-submitted registrations qualify: a request already sent to review
 * ({@code PENDING}, reviewed, approved or rejected) is refused with a 422 and is never touched by
 * the cleanup job.</p>
 */
@Service
@Slf4j
public class RegistrationDraftService {

    /** Domain of the placeholder email a discarded login is left with; never deliverable ({@code .invalid}). */
    static final String TOMBSTONE_EMAIL_DOMAIN = "discarded.invalid";
    private static final String USER_SUSPENDED = "SUSPENDED";
    /** A person created together with its login shares its creation moment (same transaction) within this window. */
    private static final Duration SAME_CREATION_WINDOW = Duration.ofMinutes(1);

    private final AffiliationRequestRepository affiliationRequests;
    private final AllyRepository allyRepository;
    private final AllyUserRepository allyUserRepository;
    private final AllyDraftAccess allyDraftAccess;
    private final PromoterRepository promoterRepository;
    private final UserRoleRepository userRoleRepository;
    private final MediaItemRepository mediaItemRepository;
    private final TokenBlacklistService tokenBlacklist;
    private final TransactionTemplate perItemTransaction;

    public RegistrationDraftService(AffiliationRequestRepository affiliationRequests, AllyRepository allyRepository,
                                    AllyUserRepository allyUserRepository, AllyDraftAccess allyDraftAccess,
                                    PromoterRepository promoterRepository, UserRoleRepository userRoleRepository,
                                    MediaItemRepository mediaItemRepository, TokenBlacklistService tokenBlacklist,
                                    PlatformTransactionManager transactionManager) {
        this.affiliationRequests = affiliationRequests;
        this.allyRepository = allyRepository;
        this.allyUserRepository = allyUserRepository;
        this.allyDraftAccess = allyDraftAccess;
        this.promoterRepository = promoterRepository;
        this.userRoleRepository = userRoleRepository;
        this.mediaItemRepository = mediaItemRepository;
        this.tokenBlacklist = tokenBlacklist;
        this.perItemTransaction = new TransactionTemplate(transactionManager);
        this.perItemTransaction.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** What one cleanup run removed. */
    public record CleanupResult(int memberDrafts, int allyDrafts, int failures) {}

    // ─── Discard on request ─────────────────────────────────────────────────

    /** The person discards their own DRAFT affiliation registration; the login is tombstoned too. */
    @Transactional
    public void discardOwnMemberDraft(UUID userUuid) {
        AffiliationRequest own = affiliationRequests.findFirstByUser_UuidOrderByCreatedAtDesc(userUuid)
                .orElseThrow(() -> new NoSuchElementException("affiliation_request.not_found"));
        AffiliationRequest request = affiliationRequests.findForUpdateByUuid(own.getUuid())
                .orElseThrow(() -> new NoSuchElementException("affiliation_request.not_found"));
        requireDraft(request);
        removeMemberDraft(request);
        log.info("Affiliation draft {} discarded by its owner", request.getUuid());
    }

    /** The advisor discards one of their own assisted DRAFTs; another advisor's is a 404. */
    @Transactional
    public void discardAssistedAffiliationDraft(UUID advisorUserUuid, UUID uuid) {
        Promoter advisor = promoterRepository.findActiveByUserUuid(advisorUserUuid)
                .orElseThrow(() -> new NoSuchElementException("me.promoter.not_found"));
        AffiliationRequest request = affiliationRequests.findForUpdateByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("affiliation_request.not_found"));
        if (!request.isAssisted() || request.getPromoter() == null
                || !request.getPromoter().getId().equals(advisor.getId())) {
            throw new NoSuchElementException("affiliation_request.not_found");
        }
        requireDraft(request);
        removeMemberDraft(request);
        log.info("Assisted affiliation draft {} discarded by advisor {}", uuid, advisor.getReferralCode());
    }

    /** The OWNER of a self-registered ally discards it while it was not sent to review. */
    @Transactional
    public void discardOwnAllyRegistration(UUID allyUuid, UUID ownerUserUuid) {
        AllyUser membership = ownerUserUuid == null ? null
                : allyUserRepository.findActiveByAllyUuidAndUserUuid(allyUuid, ownerUserUuid).orElse(null);
        if (membership == null || membership.getAllyRole() != AllyRole.OWNER) {
            throw new AccessDeniedException("ally.profile.not_allowed");
        }
        Ally ally = allyRepository.findForUpdateByUuid(allyUuid)
                .orElseThrow(() -> new NoSuchElementException("ally_request.not_found"));
        requireAllyInProgress(ally, Ally.RegistrationChannel.SELF_SERVICE);
        removeAllyDraft(ally);
        log.info("Ally registration {} discarded by its owner", allyUuid);
    }

    /** The advisor discards an ally they captured while it is still a draft; another advisor's is a 404. */
    @Transactional
    public void discardAdvisorAllyDraft(UUID advisorUserUuid, UUID allyUuid) {
        Ally visible = allyDraftAccess.requireOwnDraft(allyUuid, advisorUserUuid);
        Ally ally = allyRepository.findForUpdateByUuid(visible.getUuid())
                .orElseThrow(() -> new NoSuchElementException("ally_request.not_found"));
        requireAllyInProgress(ally, Ally.RegistrationChannel.ADVISOR);
        removeAllyDraft(ally);
        log.info("Assisted ally draft {} discarded by its advisor", allyUuid);
    }

    // ─── Abandoned drafts ───────────────────────────────────────────────────

    /**
     * Discards every DRAFT affiliation registration and every in-progress ally registration (self-service or
     * advisor-captured) untouched since {@code cutoff}. One transaction per registration: a failure is logged and
     * counted and does not stop the rest. Submitted requests never match.
     */
    public CleanupResult purgeAbandoned(Instant cutoff) {
        int members = 0;
        int allies = 0;
        int failures = 0;
        for (AffiliationRequest candidate : affiliationRequests
                .findByStatusAndUpdatedAtBefore(AffiliationRequest.Status.DRAFT.name(), cutoff)) {
            UUID uuid = candidate.getUuid();
            try {
                Boolean removed = perItemTransaction.execute(tx -> purgeMemberDraft(uuid, cutoff));
                if (Boolean.TRUE.equals(removed)) members++;
            } catch (RuntimeException ex) {
                failures++;
                log.warn("Could not discard the abandoned affiliation draft {}", uuid, ex);
            }
        }
        for (Ally candidate : allyRepository
                .findByRegistrationStatusAndRegistrationSubmittedAtIsNullAndRegistrationChannelInAndUpdatedAtBefore(
                        Ally.RegistrationStatus.PENDING,
                        List.of(Ally.RegistrationChannel.SELF_SERVICE, Ally.RegistrationChannel.ADVISOR), cutoff)) {
            UUID uuid = candidate.getUuid();
            try {
                Boolean removed = perItemTransaction.execute(tx -> purgeAllyDraft(uuid, cutoff));
                if (Boolean.TRUE.equals(removed)) allies++;
            } catch (RuntimeException ex) {
                failures++;
                log.warn("Could not discard the abandoned ally registration {}", uuid, ex);
            }
        }
        return new CleanupResult(members, allies, failures);
    }

    /** Re-checks under the row lock that the draft is still a draft and still abandoned. */
    private boolean purgeMemberDraft(UUID uuid, Instant cutoff) {
        AffiliationRequest request = affiliationRequests.findForUpdateByUuid(uuid).orElse(null);
        if (request == null || !request.isDraft() || request.getUpdatedAt() == null
                || !request.getUpdatedAt().isBefore(cutoff)) {
            return false;
        }
        removeMemberDraft(request);
        return true;
    }

    private boolean purgeAllyDraft(UUID uuid, Instant cutoff) {
        Ally ally = allyRepository.findForUpdateByUuid(uuid).orElse(null);
        if (ally == null || ally.getRegistrationStatus() != Ally.RegistrationStatus.PENDING
                || ally.getRegistrationSubmittedAt() != null || ally.getUpdatedAt() == null
                || !ally.getUpdatedAt().isBefore(cutoff)
                || (ally.getRegistrationChannel() != Ally.RegistrationChannel.SELF_SERVICE
                        && ally.getRegistrationChannel() != Ally.RegistrationChannel.ADVISOR)) {
            return false;
        }
        removeAllyDraft(ally);
        return true;
    }

    // ─── Removal ────────────────────────────────────────────────────────────

    private static void requireDraft(AffiliationRequest request) {
        if (!request.isDraft()) {
            throw new IllegalArgumentException("affiliation_request.discard.invalid_state");
        }
    }

    private static void requireAllyInProgress(Ally ally, Ally.RegistrationChannel channel) {
        if (ally.getRegistrationChannel() != channel
                || ally.getRegistrationStatus() != Ally.RegistrationStatus.PENDING
                || ally.getRegistrationSubmittedAt() != null) {
            throw new IllegalArgumentException("ally_request.draft.invalid_state");
        }
    }

    /** Deletes the DRAFT (its log rows go with it) and, when it came with a login, tombstones that login. */
    private void removeMemberDraft(AffiliationRequest request) {
        User user = request.getUser();
        affiliationRequests.delete(request);
        affiliationRequests.flush();
        if (user != null) {
            tombstoneLogin(user, true, RoleNames.MEMBER_PENDING);
        }
    }

    /** Deletes the ally (hours, types and member links cascade in the database) and its gallery rows; tombstones its pending logins. */
    private void removeAllyDraft(Ally ally) {
        List<User> logins = allyUserRepository.findByAllyIdAndActiveTrue(ally.getId(), Sort.unsorted()).stream()
                .map(AllyUser::getUser)
                .toList();
        Person holder = ally.getHolderPerson();
        mediaItemRepository.deleteAll(mediaItemRepository
                .findByOwnerTableAndOwnerUuidAndActiveTrueOrderBySortOrderAscIdAsc(MediaTables.ALLIES, ally.getUuid()));
        allyRepository.delete(ally);
        allyRepository.flush();
        for (User user : logins) {
            // The holder person is only released when it was created together with the login: an existing
            // person (a member without an account, say) keeps its cédula and record.
            boolean createdWithLogin = holder != null && createdTogether(user, holder);
            tombstoneLogin(user, createdWithLogin, RoleNames.ALLY_PENDING);
        }
    }

    private static boolean createdTogether(User user, Person person) {
        return user.getCreatedAt() != null && person.getCreatedAt() != null
                && Duration.between(person.getCreatedAt(), user.getCreatedAt()).abs().compareTo(SAME_CREATION_WINDOW) <= 0;
    }

    /**
     * Leaves the login unusable and frees its identifiers. Only a login that exists because of the registration
     * (it holds nothing but the pending role) is touched; any other is left alone.
     *
     * @param releasePerson also rewrites the person's cédula and contact data, so the real cédula can register
     *                      again ({@code users.person_id} is unique); false for a person that existed before
     */
    void tombstoneLogin(User user, boolean releasePerson, String pendingRole) {
        List<UserRole> roles = userRoleRepository.findByUserIdAndActiveTrue(user.getId());
        boolean onlyPending = !roles.isEmpty() && roles.stream().allMatch(ur -> pendingRole.equals(ur.getRole().getName()));
        if (!onlyPending) {
            log.warn("Login {} holds more than the pending role: left untouched by the discard", user.getUuid());
            return;
        }
        String token = user.getUuid().toString().replace("-", "");
        user.setEmail("discarded-" + token + "@" + TOMBSTONE_EMAIL_DOMAIN);
        user.setStatus(USER_SUSPENDED);
        user.setActive(false);
        user.setLoginTokenHash(null);
        user.setLoginTokenExpiresAt(null);
        user.setPasswordResetToken(null);
        user.setPasswordResetExpiresAt(null);
        Person person = user.getPerson();
        if (releasePerson && person != null) {
            person.setDocumentNumber("X" + token.substring(0, 19));  // VARCHAR(20)
            person.setEmail(null);
            person.setPhone(null);
            person.setTaxDocumentType(null);
            person.setTaxDocumentNumber(null);
        }
        tokenBlacklist.revokeAllUserRefreshTokens(user.getUuid().toString());
        tokenBlacklist.markUserInvalidatedNow(user.getUuid().toString());
    }
}
