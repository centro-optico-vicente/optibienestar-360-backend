package com.fenixcore.optibienestar360.modules.onboarding.service;

import com.fenixcore.optibienestar360.common.storage.AttachedFile;
import com.fenixcore.optibienestar360.common.storage.AttachedFileRepository;
import com.fenixcore.optibienestar360.common.storage.FileVisibility;
import com.fenixcore.optibienestar360.common.storage.StoredFileCleaner;
import com.fenixcore.optibienestar360.common.storage.StoredObject;
import com.fenixcore.optibienestar360.modules.affiliation.entity.AffiliationRequest;
import com.fenixcore.optibienestar360.modules.affiliation.repository.AffiliationRequestRepository;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyAgreement;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser.AllyRole;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyAgreementRepository;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyRepository;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyUserRepository;
import com.fenixcore.optibienestar360.modules.ally.service.AllyDraftAccess;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.entity.UserRole;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRoleRepository;
import com.fenixcore.optibienestar360.modules.auth.service.TokenBlacklistService;
import com.fenixcore.optibienestar360.modules.media.MediaTables;
import com.fenixcore.optibienestar360.modules.media.entity.MediaItem;
import com.fenixcore.optibienestar360.modules.media.repository.MediaItemRepository;
import com.fenixcore.optibienestar360.modules.onboarding.dto.DraftRetentionDto;
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
import java.util.ArrayList;
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
 *
 * <p><b>Retention (V237).</b> A draft held open (negotiation in progress, waiting for information) can be
 * flagged {@code retain_from_cleanup}; the cleanup skips it. The advisor sets or clears the flag on the drafts in their
 * scope (their own, or self-registered ones nobody is attributed to), staff on any registration in progress. Setting the flag touches the row, so releasing it
 * gives the draft a fresh retention window. A retained draft that is submitted just stops being a draft: the
 * flag is then ignored.</p>
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
    private final AttachedFileRepository attachedFileRepository;
    private final AllyAgreementRepository allyAgreementRepository;
    private final StoredFileCleaner storedFileCleaner;
    private final TokenBlacklistService tokenBlacklist;
    private final TransactionTemplate perItemTransaction;

    public RegistrationDraftService(AffiliationRequestRepository affiliationRequests, AllyRepository allyRepository,
                                    AllyUserRepository allyUserRepository, AllyDraftAccess allyDraftAccess,
                                    PromoterRepository promoterRepository, UserRoleRepository userRoleRepository,
                                    MediaItemRepository mediaItemRepository,
                                    AttachedFileRepository attachedFileRepository,
                                    AllyAgreementRepository allyAgreementRepository,
                                    StoredFileCleaner storedFileCleaner, TokenBlacklistService tokenBlacklist,
                                    PlatformTransactionManager transactionManager) {
        this.affiliationRequests = affiliationRequests;
        this.allyRepository = allyRepository;
        this.allyUserRepository = allyUserRepository;
        this.allyDraftAccess = allyDraftAccess;
        this.promoterRepository = promoterRepository;
        this.userRoleRepository = userRoleRepository;
        this.mediaItemRepository = mediaItemRepository;
        this.attachedFileRepository = attachedFileRepository;
        this.allyAgreementRepository = allyAgreementRepository;
        this.storedFileCleaner = storedFileCleaner;
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

    // ─── Retention ──────────────────────────────────────────────────────────

    /**
     * The advisor retains / releases an affiliation draft in their scope: their own (assisted, or self-registered
     * through their QR) or a self-registered one nobody is attributed to. Another advisor's is a 404.
     */
    @Transactional
    public DraftRetentionDto retainAdvisorAffiliationDraft(UUID advisorUserUuid, UUID uuid, boolean retain, String note) {
        Promoter advisor = promoterRepository.findActiveByUserUuid(advisorUserUuid)
                .orElseThrow(() -> new NoSuchElementException("me.promoter.not_found"));
        AffiliationRequest request = affiliationRequests.findForUpdateByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("affiliation_request.not_found"));
        if (request.getPromoter() != null && !request.getPromoter().getId().equals(advisor.getId())) {
            throw new NoSuchElementException("affiliation_request.not_found");
        }
        if (request.isAssisted() && request.getPromoter() == null) {
            throw new NoSuchElementException("affiliation_request.not_found");
        }
        return applyMemberRetention(request, advisorUserUuid, retain, note);
    }

    /** Staff retains / releases any affiliation draft. */
    @Transactional
    public DraftRetentionDto retainAffiliationDraft(UUID actorUserUuid, UUID uuid, boolean retain, String note) {
        AffiliationRequest request = affiliationRequests.findForUpdateByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("affiliation_request.not_found"));
        return applyMemberRetention(request, actorUserUuid, retain, note);
    }

    /**
     * The advisor retains / releases an ally registration in their scope: one they captured, or a self-registered one
     * no advisor brought in. Another advisor's is a 404.
     */
    @Transactional
    public DraftRetentionDto retainAdvisorAllyDraft(UUID advisorUserUuid, UUID allyUuid, boolean retain, String note) {
        Promoter advisor = promoterRepository.findActiveByUserUuid(advisorUserUuid)
                .orElseThrow(() -> new NoSuchElementException("me.promoter.not_found"));
        Ally ally = allyRepository.findForUpdateByUuid(allyUuid)
                .orElseThrow(() -> new NoSuchElementException("ally_request.not_found"));
        Promoter capturedBy = ally.getCapturedByPromoter();
        boolean own = capturedBy != null && capturedBy.getId().equals(advisor.getId());
        boolean unattributed = capturedBy == null && ally.getRegistrationChannel() == Ally.RegistrationChannel.SELF_SERVICE;
        if (!own && !unattributed) {
            throw new NoSuchElementException("ally_request.not_found");
        }
        return applyAllyRetention(ally, advisorUserUuid, retain, note);
    }

    /** Staff retains / releases any ally registration that is still in progress (self-service or advisor-captured). */
    @Transactional
    public DraftRetentionDto retainAllyRegistration(UUID actorUserUuid, UUID allyUuid, boolean retain, String note) {
        Ally ally = allyRepository.findForUpdateByUuid(allyUuid)
                .orElseThrow(() -> new NoSuchElementException("ally_request.not_found"));
        return applyAllyRetention(ally, actorUserUuid, retain, note);
    }

    private DraftRetentionDto applyMemberRetention(AffiliationRequest request, UUID actorUserUuid, boolean retain, String note) {
        if (!request.isDraft()) {
            throw new IllegalArgumentException("affiliation_request.retain.invalid_state");
        }
        request.setRetainFromCleanup(retain);
        request.setRetainNote(retain ? normalizeNote(note) : null);
        request.setRetainedBy(retain ? actorUserUuid : null);
        request.setRetainedAt(retain ? Instant.now() : null);
        affiliationRequests.save(request);
        log.info("Affiliation draft {} {} cleanup by {}", request.getUuid(), retain ? "retained from" : "released to", actorUserUuid);
        return new DraftRetentionDto(request.getUuid(), request.isRetainFromCleanup(), request.getRetainNote(),
                request.getRetainedAt());
    }

    private DraftRetentionDto applyAllyRetention(Ally ally, UUID actorUserUuid, boolean retain, String note) {
        if (!isAllyDraft(ally)) {
            throw new IllegalArgumentException("ally_request.retain.invalid_state");
        }
        ally.setRetainFromCleanup(retain);
        ally.setRetainNote(retain ? normalizeNote(note) : null);
        ally.setRetainedBy(retain ? actorUserUuid : null);
        ally.setRetainedAt(retain ? Instant.now() : null);
        allyRepository.save(ally);
        log.info("Ally registration {} {} cleanup by {}", ally.getUuid(), retain ? "retained from" : "released to", actorUserUuid);
        return new DraftRetentionDto(ally.getUuid(), ally.isRetainFromCleanup(), ally.getRetainNote(), ally.getRetainedAt());
    }

    private static String normalizeNote(String note) {
        return note == null || note.isBlank() ? null : note.trim();
    }

    /** An ally registration still in progress: self-service or advisor-captured, pending and never sent to review. */
    private static boolean isAllyDraft(Ally ally) {
        return ally.getRegistrationStatus() == Ally.RegistrationStatus.PENDING
                && ally.getRegistrationSubmittedAt() == null
                && (ally.getRegistrationChannel() == Ally.RegistrationChannel.SELF_SERVICE
                        || ally.getRegistrationChannel() == Ally.RegistrationChannel.ADVISOR);
    }

    // ─── Abandoned drafts ───────────────────────────────────────────────────

    /**
     * Discards every DRAFT affiliation registration untouched since {@code memberCutoff} and every in-progress ally
     * registration (self-service or advisor-captured) untouched since {@code allyCutoff}: the two kinds keep
     * different retention windows. A draft flagged {@code retain_from_cleanup} is always skipped. One transaction
     * per registration: a failure is logged and counted and does not stop the rest. Submitted requests never match.
     */
    public CleanupResult purgeAbandoned(Instant memberCutoff, Instant allyCutoff) {
        int members = 0;
        int allies = 0;
        int failures = 0;
        for (AffiliationRequest candidate : affiliationRequests
                .findByStatusAndRetainFromCleanupFalseAndUpdatedAtBefore(AffiliationRequest.Status.DRAFT.name(), memberCutoff)) {
            UUID uuid = candidate.getUuid();
            try {
                Boolean removed = perItemTransaction.execute(tx -> purgeMemberDraft(uuid, memberCutoff));
                if (Boolean.TRUE.equals(removed)) members++;
            } catch (RuntimeException ex) {
                failures++;
                log.warn("Could not discard the abandoned affiliation draft {}", uuid, ex);
            }
        }
        for (Ally candidate : allyRepository
                .findByRegistrationStatusAndRegistrationSubmittedAtIsNullAndRegistrationChannelInAndRetainFromCleanupFalseAndUpdatedAtBefore(
                        Ally.RegistrationStatus.PENDING,
                        List.of(Ally.RegistrationChannel.SELF_SERVICE, Ally.RegistrationChannel.ADVISOR), allyCutoff)) {
            UUID uuid = candidate.getUuid();
            try {
                Boolean removed = perItemTransaction.execute(tx -> purgeAllyDraft(uuid, allyCutoff));
                if (Boolean.TRUE.equals(removed)) allies++;
            } catch (RuntimeException ex) {
                failures++;
                log.warn("Could not discard the abandoned ally registration {}", uuid, ex);
            }
        }
        return new CleanupResult(members, allies, failures);
    }

    /** Re-checks under the row lock that the draft is still a draft, still abandoned and not retained. */
    private boolean purgeMemberDraft(UUID uuid, Instant cutoff) {
        AffiliationRequest request = affiliationRequests.findForUpdateByUuid(uuid).orElse(null);
        if (request == null || !request.isDraft() || request.isRetainFromCleanup() || request.getUpdatedAt() == null
                || !request.getUpdatedAt().isBefore(cutoff)) {
            return false;
        }
        removeMemberDraft(request);
        return true;
    }

    private boolean purgeAllyDraft(UUID uuid, Instant cutoff) {
        Ally ally = allyRepository.findForUpdateByUuid(uuid).orElse(null);
        if (ally == null || ally.getRegistrationStatus() != Ally.RegistrationStatus.PENDING
                || ally.isRetainFromCleanup()
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

    /**
     * Deletes the DRAFT (its log rows go with it) and, when it came with a login, tombstones that login. The payment
     * proof stored for the draft is deleted from the object store once the transaction commits.
     */
    private void removeMemberDraft(AffiliationRequest request) {
        User user = request.getUser();
        List<StoredObject> stored = memberDraftObjects(request);
        affiliationRequests.delete(request);
        affiliationRequests.flush();
        if (user != null) {
            tombstoneLogin(user, true, RoleNames.MEMBER_PENDING);
        }
        storedFileCleaner.deleteAfterCommit(stored);
    }

    /**
     * Deletes the ally (hours, types and member links cascade in the database), its gallery rows and their stored
     * files, and tombstones its pending logins. Gallery images and signed agreement files are deleted from the object
     * store once the transaction commits.
     */
    private void removeAllyDraft(Ally ally) {
        List<User> logins = allyUserRepository.findByAllyIdAndActiveTrue(ally.getId(), Sort.unsorted()).stream()
                .map(AllyUser::getUser)
                .toList();
        Person holder = ally.getHolderPerson();
        List<MediaItem> gallery = mediaItemRepository
                .findByOwnerTableAndOwnerUuidAndActiveTrueOrderBySortOrderAscIdAsc(MediaTables.ALLIES, ally.getUuid());
        List<StoredObject> stored = allyDraftObjects(ally, gallery);
        List<AttachedFile> galleryFiles = gallery.stream()
                .map(MediaItem::getAttachedFile)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        mediaItemRepository.deleteAll(gallery);
        mediaItemRepository.flush();
        // The stored file rows of the gallery go once no image references them.
        attachedFileRepository.deleteAll(galleryFiles);
        allyRepository.delete(ally);
        allyRepository.flush();
        for (User user : logins) {
            // The holder person is only released when it was created together with the login: an existing
            // person (a member without an account, say) keeps its cédula and record.
            boolean createdWithLogin = holder != null && createdTogether(user, holder);
            tombstoneLogin(user, createdWithLogin, RoleNames.ALLY_PENDING);
        }
        storedFileCleaner.deleteAfterCommit(stored);
    }

    /** The object keys owned by an affiliation draft: the payment proof it carries (private bucket). */
    static List<StoredObject> memberDraftObjects(AffiliationRequest request) {
        List<StoredObject> stored = new ArrayList<>();
        String proofKey = request.getPaymentSupportFileUrl();
        if (proofKey != null && !proofKey.isBlank()) {
            stored.add(new StoredObject(FileVisibility.CONFIDENTIAL, proofKey));
        }
        return stored;
    }

    /**
     * The object keys owned by an ally draft: its gallery images (the stored file's own bucket, or the public bucket
     * for an image with only a public key) and the signed support file of each of its agreements (private bucket).
     * External-link images own no object.
     */
    private List<StoredObject> allyDraftObjects(Ally ally, List<MediaItem> gallery) {
        List<StoredObject> stored = new ArrayList<>();
        for (MediaItem item : gallery) {
            AttachedFile file = item.getAttachedFile();
            if (file != null) {
                stored.add(new StoredObject(file.getVisibility(), file.getFileKey()));
            } else if (item.getPublicKey() != null) {
                stored.add(new StoredObject(FileVisibility.PUBLIC, item.getPublicKey()));
            }
        }
        for (AllyAgreement agreement : allyAgreementRepository.findByAllyId(ally.getId())) {
            String key = agreement.getSignedPdfUrl();
            // signed_pdf_url may also hold an external link: only an object key owns a stored file.
            if (key != null && !key.isBlank() && !key.contains("://")) {
                stored.add(new StoredObject(FileVisibility.CONFIDENTIAL, key));
            }
        }
        return stored;
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
    /**
     * Deactivates {@code pendingRole} on a login that holds other roles too. {@code ALIADO_PENDIENTE} stays
     * while the login still operates another ally (a second registration in progress).
     */
    private void releasePendingRole(User user, List<UserRole> activeRoles, String pendingRole) {
        if (RoleNames.ALLY_PENDING.equals(pendingRole) && !allyUserRepository.findActiveByUserUuid(user.getUuid()).isEmpty()) {
            log.info("Login {} keeps {}: it still operates another ally", user.getUuid(), pendingRole);
            return;
        }
        for (UserRole assignment : activeRoles) {
            if (pendingRole.equals(assignment.getRole().getName())) {
                assignment.setActive(false);
                userRoleRepository.save(assignment);
            }
        }
        if (user.getDefaultRole() != null && pendingRole.equals(user.getDefaultRole().getName())) {
            activeRoles.stream()
                    .filter(ur -> !pendingRole.equals(ur.getRole().getName()))
                    .findFirst()
                    .ifPresent(ur -> user.setDefaultRole(ur.getRole()));
        }
        log.info("Login {} holds other roles: only {} was removed by the discard", user.getUuid(), pendingRole);
    }

    void tombstoneLogin(User user, boolean releasePerson, String pendingRole) {
        List<UserRole> roles = userRoleRepository.findByUserIdAndActiveTrue(user.getId());
        boolean onlyPending = !roles.isEmpty() && roles.stream().allMatch(ur -> pendingRole.equals(ur.getRole().getName()));
        if (!onlyPending) {
            // An existing account that joined another segment (an advisor who started an affiliation, a member
            // who started an ally): the account stays, only the role this registration gave it goes.
            releasePendingRole(user, roles, pendingRole);
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
