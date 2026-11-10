package com.fenixcore.optibienestar360.modules.onboarding.service;

import com.fenixcore.optibienestar360.modules.affiliation.entity.AffiliationRequest;
import com.fenixcore.optibienestar360.modules.affiliation.repository.AffiliationRequestRepository;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyRepository;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyUserRepository;
import com.fenixcore.optibienestar360.modules.ally.service.AllyDraftAccess;
import com.fenixcore.optibienestar360.modules.auth.entity.Role;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.entity.UserRole;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRoleRepository;
import com.fenixcore.optibienestar360.modules.auth.service.TokenBlacklistService;
import com.fenixcore.optibienestar360.modules.media.repository.MediaItemRepository;
import com.fenixcore.optibienestar360.modules.onboarding.dto.DraftRetentionDto;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import com.fenixcore.optibienestar360.security.RoleNames;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Discarding registrations in progress and the abandoned-draft cleanup (hub ADR 0037 §2): a DRAFT is deleted
 * and its login tombstoned so the cédula and email are free again; whatever was already sent to review is never
 * touched.
 */
@ExtendWith(MockitoExtension.class)
class RegistrationDraftServiceTest {

    @Mock private AffiliationRequestRepository affiliationRequests;
    @Mock private AllyRepository allyRepository;
    @Mock private AllyUserRepository allyUserRepository;
    @Mock private AllyDraftAccess allyDraftAccess;
    @Mock private PromoterRepository promoterRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private MediaItemRepository mediaItemRepository;
    @Mock private TokenBlacklistService tokenBlacklist;
    @Mock private PlatformTransactionManager transactionManager;

    private RegistrationDraftService service;
    private User user;
    private Person person;
    private Promoter advisor;
    private User advisorUser;

    @BeforeEach
    void setUp() {
        service = new RegistrationDraftService(affiliationRequests, allyRepository, allyUserRepository,
                allyDraftAccess, promoterRepository, userRoleRepository, mediaItemRepository, tokenBlacklist,
                transactionManager);
        person = new Person();
        person.setId(1L);
        person.setUuid(UUID.randomUUID());
        person.setDocumentType("V");
        person.setDocumentNumber("12345678");
        person.setEmail("maria@example.com");
        person.setPhone("04141234567");
        person.setCreatedAt(Instant.now());
        user = new User();
        user.setId(2L);
        user.setUuid(UUID.randomUUID());
        user.setEmail("maria@example.com");
        user.setPerson(person);
        user.setCreatedAt(person.getCreatedAt());
        advisor = new Promoter();
        advisor.setId(8L);
        advisor.setUuid(UUID.randomUUID());
        advisor.setReferralCode("ABC123");
        advisorUser = new User();
        advisorUser.setId(50L);
        advisorUser.setUuid(UUID.randomUUID());
    }

    private void holdsOnly(String roleName) {
        Role role = new Role();
        role.setName(roleName);
        UserRole ur = new UserRole();
        ur.setRole(role);
        when(userRoleRepository.findByUserIdAndActiveTrue(user.getId())).thenReturn(List.of(ur));
    }

    private AffiliationRequest draft(User owner, boolean assisted) {
        AffiliationRequest r = new AffiliationRequest();
        r.setId(10L);
        r.setUuid(UUID.randomUUID());
        r.setStatus(AffiliationRequest.Status.DRAFT.name());
        r.setChannel((assisted ? AffiliationRequest.Channel.ASSISTED : AffiliationRequest.Channel.SELF_SERVICE).name());
        r.setUser(owner);
        if (assisted) r.setPromoter(advisor);
        r.setUpdatedAt(Instant.now().minus(40, ChronoUnit.DAYS));
        return r;
    }

    private Ally allyDraft(Ally.RegistrationChannel channel) {
        Ally a = new Ally();
        a.setId(30L);
        a.setUuid(UUID.randomUUID());
        a.setRegistrationChannel(channel);
        a.setRegistrationStatus(Ally.RegistrationStatus.PENDING);
        a.setHolderPerson(person);
        a.setUpdatedAt(Instant.now().minus(40, ChronoUnit.DAYS));
        return a;
    }

    // ─── Member discard ─────────────────────────────────────────────────────

    @Test
    void discardOwnMemberDraft_deletesTheRequestAndTombstonesTheLogin() {
        AffiliationRequest request = draft(user, false);
        when(affiliationRequests.findFirstByUser_UuidOrderByCreatedAtDesc(user.getUuid())).thenReturn(Optional.of(request));
        when(affiliationRequests.findForUpdateByUuid(request.getUuid())).thenReturn(Optional.of(request));
        holdsOnly(RoleNames.MEMBER_PENDING);

        service.discardOwnMemberDraft(user.getUuid());

        verify(affiliationRequests).delete(request);
        assertThat(user.getEmail()).isEqualTo("discarded-" + user.getUuid().toString().replace("-", "") + "@discarded.invalid");
        assertThat(user.getStatus()).isEqualTo("SUSPENDED");
        assertThat(user.isActive()).isFalse();
        assertThat(person.getDocumentNumber()).isNotEqualTo("12345678").hasSizeLessThanOrEqualTo(20);
        assertThat(person.getEmail()).isNull();
        assertThat(person.getPhone()).isNull();
        verify(tokenBlacklist).revokeAllUserRefreshTokens(user.getUuid().toString());
        verify(tokenBlacklist).markUserInvalidatedNow(user.getUuid().toString());
    }

    @Test
    void discardOwnMemberDraft_refusesARequestAlreadySentToReview() {
        AffiliationRequest request = draft(user, false);
        request.setStatus(AffiliationRequest.Status.PENDING.name());
        when(affiliationRequests.findFirstByUser_UuidOrderByCreatedAtDesc(user.getUuid())).thenReturn(Optional.of(request));
        when(affiliationRequests.findForUpdateByUuid(request.getUuid())).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.discardOwnMemberDraft(user.getUuid()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("affiliation_request.discard.invalid_state");
        verify(affiliationRequests, never()).delete(any(AffiliationRequest.class));
        assertThat(user.isActive()).isTrue();
    }

    @Test
    void discardOwnMemberDraft_leavesALoginWithOtherRolesUntouched() {
        AffiliationRequest request = draft(user, false);
        when(affiliationRequests.findFirstByUser_UuidOrderByCreatedAtDesc(user.getUuid())).thenReturn(Optional.of(request));
        when(affiliationRequests.findForUpdateByUuid(request.getUuid())).thenReturn(Optional.of(request));
        holdsOnly(RoleNames.MEMBER);

        service.discardOwnMemberDraft(user.getUuid());

        verify(affiliationRequests).delete(request);
        assertThat(user.getEmail()).isEqualTo("maria@example.com");
        assertThat(person.getDocumentNumber()).isEqualTo("12345678");
        verify(tokenBlacklist, never()).revokeAllUserRefreshTokens(any());
    }

    @Test
    void discardAssistedAffiliationDraft_ofTheAdvisor_deletesWithoutTouchingAnyLogin() {
        AffiliationRequest request = draft(null, true);
        when(promoterRepository.findActiveByUserUuid(advisorUser.getUuid())).thenReturn(Optional.of(advisor));
        when(affiliationRequests.findForUpdateByUuid(request.getUuid())).thenReturn(Optional.of(request));

        service.discardAssistedAffiliationDraft(advisorUser.getUuid(), request.getUuid());

        verify(affiliationRequests).delete(request);
        verify(tokenBlacklist, never()).revokeAllUserRefreshTokens(any());
    }

    @Test
    void discardAssistedAffiliationDraft_ofAnotherAdvisor_isNotFound() {
        AffiliationRequest request = draft(null, true);
        Promoter other = new Promoter();
        other.setId(99L);
        when(promoterRepository.findActiveByUserUuid(advisorUser.getUuid())).thenReturn(Optional.of(other));
        when(affiliationRequests.findForUpdateByUuid(request.getUuid())).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.discardAssistedAffiliationDraft(advisorUser.getUuid(), request.getUuid()))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("affiliation_request.not_found");
        verify(affiliationRequests, never()).delete(any(AffiliationRequest.class));
    }

    // ─── Ally discard ───────────────────────────────────────────────────────

    @Test
    void discardOwnAllyRegistration_byANonOwner_isForbidden() {
        UUID allyUuid = UUID.randomUUID();
        when(allyUserRepository.findActiveByAllyUuidAndUserUuid(allyUuid, user.getUuid())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.discardOwnAllyRegistration(allyUuid, user.getUuid()))
                .isInstanceOf(AccessDeniedException.class);
        verify(allyRepository, never()).delete(any(Ally.class));
    }

    @Test
    void discardOwnAllyRegistration_afterTheSubmit_isRefused() {
        Ally ally = allyDraft(Ally.RegistrationChannel.SELF_SERVICE);
        ally.setRegistrationSubmittedAt(Instant.now());
        AllyUser membership = new AllyUser();
        membership.setAllyRole(AllyUser.AllyRole.OWNER);
        when(allyUserRepository.findActiveByAllyUuidAndUserUuid(ally.getUuid(), user.getUuid())).thenReturn(Optional.of(membership));
        when(allyRepository.findForUpdateByUuid(ally.getUuid())).thenReturn(Optional.of(ally));

        assertThatThrownBy(() -> service.discardOwnAllyRegistration(ally.getUuid(), user.getUuid()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ally_request.draft.invalid_state");
        verify(allyRepository, never()).delete(any(Ally.class));
    }

    @Test
    void discardOwnAllyRegistration_deletesTheAllyAndReleasesTheLogin() {
        Ally ally = allyDraft(Ally.RegistrationChannel.SELF_SERVICE);
        AllyUser membership = new AllyUser();
        membership.setAllyRole(AllyUser.AllyRole.OWNER);
        membership.setUser(user);
        when(allyUserRepository.findActiveByAllyUuidAndUserUuid(ally.getUuid(), user.getUuid())).thenReturn(Optional.of(membership));
        when(allyRepository.findForUpdateByUuid(ally.getUuid())).thenReturn(Optional.of(ally));
        when(allyUserRepository.findByAllyIdAndActiveTrue(eq(ally.getId()), any(Sort.class))).thenReturn(List.of(membership));
        holdsOnly(RoleNames.ALLY_PENDING);

        service.discardOwnAllyRegistration(ally.getUuid(), user.getUuid());

        verify(allyRepository).delete(ally);
        assertThat(user.getStatus()).isEqualTo("SUSPENDED");
        assertThat(person.getDocumentNumber()).isNotEqualTo("12345678");
    }

    @Test
    void discardOwnAllyRegistration_keepsAPersonThatExistedBeforeTheLogin() {
        person.setCreatedAt(Instant.now().minus(200, ChronoUnit.DAYS));  // a member without account, say
        Ally ally = allyDraft(Ally.RegistrationChannel.SELF_SERVICE);
        AllyUser membership = new AllyUser();
        membership.setAllyRole(AllyUser.AllyRole.OWNER);
        membership.setUser(user);
        when(allyUserRepository.findActiveByAllyUuidAndUserUuid(ally.getUuid(), user.getUuid())).thenReturn(Optional.of(membership));
        when(allyRepository.findForUpdateByUuid(ally.getUuid())).thenReturn(Optional.of(ally));
        when(allyUserRepository.findByAllyIdAndActiveTrue(eq(ally.getId()), any(Sort.class))).thenReturn(List.of(membership));
        holdsOnly(RoleNames.ALLY_PENDING);

        service.discardOwnAllyRegistration(ally.getUuid(), user.getUuid());

        assertThat(user.getEmail()).endsWith("@discarded.invalid");
        assertThat(person.getDocumentNumber()).isEqualTo("12345678");
    }

    @Test
    void discardAdvisorAllyDraft_deletesTheAllyOfTheAdvisor() {
        Ally ally = allyDraft(Ally.RegistrationChannel.ADVISOR);
        when(allyDraftAccess.requireOwnDraft(ally.getUuid(), advisorUser.getUuid())).thenReturn(ally);
        when(allyRepository.findForUpdateByUuid(ally.getUuid())).thenReturn(Optional.of(ally));
        when(allyUserRepository.findByAllyIdAndActiveTrue(eq(ally.getId()), any(Sort.class))).thenReturn(List.of());

        service.discardAdvisorAllyDraft(advisorUser.getUuid(), ally.getUuid());

        verify(allyRepository).delete(ally);
        verify(tokenBlacklist, never()).revokeAllUserRefreshTokens(any());
    }

    @Test
    void discardAdvisorAllyDraft_ofAnotherAdvisor_isNotFound() {
        UUID allyUuid = UUID.randomUUID();
        when(allyDraftAccess.requireOwnDraft(allyUuid, advisorUser.getUuid()))
                .thenThrow(new NoSuchElementException("ally_request.not_found"));

        assertThatThrownBy(() -> service.discardAdvisorAllyDraft(advisorUser.getUuid(), allyUuid))
                .isInstanceOf(NoSuchElementException.class);
        verify(allyRepository, never()).delete(any(Ally.class));
    }

    // ─── Abandoned drafts ───────────────────────────────────────────────────

    @Test
    void purgeAbandoned_discardsOldDraftsAndSkipsWhatChangedMeanwhile() {
        Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);
        AffiliationRequest abandoned = draft(null, true);
        AffiliationRequest sentMeanwhile = draft(null, true);
        sentMeanwhile.setUuid(UUID.randomUUID());
        sentMeanwhile.setStatus(AffiliationRequest.Status.PENDING.name());
        Ally allyAbandoned = allyDraft(Ally.RegistrationChannel.ADVISOR);
        when(affiliationRequests.findByStatusAndRetainFromCleanupFalseAndUpdatedAtBefore("DRAFT", cutoff)).thenReturn(List.of(abandoned, sentMeanwhile));
        when(affiliationRequests.findForUpdateByUuid(abandoned.getUuid())).thenReturn(Optional.of(abandoned));
        when(affiliationRequests.findForUpdateByUuid(sentMeanwhile.getUuid())).thenReturn(Optional.of(sentMeanwhile));
        when(allyRepository.findByRegistrationStatusAndRegistrationSubmittedAtIsNullAndRegistrationChannelInAndRetainFromCleanupFalseAndUpdatedAtBefore(
                eq(Ally.RegistrationStatus.PENDING), anyList(), eq(cutoff))).thenReturn(List.of(allyAbandoned));
        when(allyRepository.findForUpdateByUuid(allyAbandoned.getUuid())).thenReturn(Optional.of(allyAbandoned));
        when(allyUserRepository.findByAllyIdAndActiveTrue(eq(allyAbandoned.getId()), any(Sort.class))).thenReturn(List.of());

        RegistrationDraftService.CleanupResult result = service.purgeAbandoned(cutoff, cutoff);

        assertThat(result.memberDrafts()).isEqualTo(1);
        assertThat(result.allyDrafts()).isEqualTo(1);
        assertThat(result.failures()).isZero();
        verify(affiliationRequests).delete(abandoned);
        verify(affiliationRequests, never()).delete(sentMeanwhile);
        verify(allyRepository).delete(allyAbandoned);
    }

    @Test
    void purgeAbandoned_skipsADraftTouchedAfterTheCutoff() {
        Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);
        AffiliationRequest touched = draft(null, true);
        touched.setUpdatedAt(Instant.now());
        when(affiliationRequests.findByStatusAndRetainFromCleanupFalseAndUpdatedAtBefore("DRAFT", cutoff)).thenReturn(List.of(touched));
        when(affiliationRequests.findForUpdateByUuid(touched.getUuid())).thenReturn(Optional.of(touched));
        when(allyRepository.findByRegistrationStatusAndRegistrationSubmittedAtIsNullAndRegistrationChannelInAndRetainFromCleanupFalseAndUpdatedAtBefore(
                eq(Ally.RegistrationStatus.PENDING), anyList(), eq(cutoff))).thenReturn(List.of());

        RegistrationDraftService.CleanupResult result = service.purgeAbandoned(cutoff, cutoff);

        assertThat(result.memberDrafts()).isZero();
        verify(affiliationRequests, never()).delete(any(AffiliationRequest.class));
    }

    @Test
    void purgeAbandoned_countsAFailureAndKeepsGoing() {
        Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);
        AffiliationRequest broken = draft(null, true);
        AffiliationRequest fine = draft(null, true);
        fine.setUuid(UUID.randomUUID());
        when(affiliationRequests.findByStatusAndRetainFromCleanupFalseAndUpdatedAtBefore("DRAFT", cutoff)).thenReturn(List.of(broken, fine));
        when(affiliationRequests.findForUpdateByUuid(broken.getUuid())).thenThrow(new IllegalStateException("db down"));
        when(affiliationRequests.findForUpdateByUuid(fine.getUuid())).thenReturn(Optional.of(fine));
        when(allyRepository.findByRegistrationStatusAndRegistrationSubmittedAtIsNullAndRegistrationChannelInAndRetainFromCleanupFalseAndUpdatedAtBefore(
                eq(Ally.RegistrationStatus.PENDING), anyList(), eq(cutoff))).thenReturn(List.of());

        RegistrationDraftService.CleanupResult result = service.purgeAbandoned(cutoff, cutoff);

        assertThat(result.failures()).isEqualTo(1);
        assertThat(result.memberDrafts()).isEqualTo(1);
        verify(affiliationRequests).delete(fine);
    }

    @Test
    void purgeAbandoned_neverDiscardsADraftRetainedMeanwhile() {
        Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);
        AffiliationRequest retained = draft(null, true);
        retained.setRetainFromCleanup(true);
        Ally allyRetained = allyDraft(Ally.RegistrationChannel.ADVISOR);
        allyRetained.setRetainFromCleanup(true);
        when(affiliationRequests.findByStatusAndRetainFromCleanupFalseAndUpdatedAtBefore("DRAFT", cutoff)).thenReturn(List.of(retained));
        when(affiliationRequests.findForUpdateByUuid(retained.getUuid())).thenReturn(Optional.of(retained));
        when(allyRepository.findByRegistrationStatusAndRegistrationSubmittedAtIsNullAndRegistrationChannelInAndRetainFromCleanupFalseAndUpdatedAtBefore(
                eq(Ally.RegistrationStatus.PENDING), anyList(), eq(cutoff))).thenReturn(List.of(allyRetained));
        when(allyRepository.findForUpdateByUuid(allyRetained.getUuid())).thenReturn(Optional.of(allyRetained));

        RegistrationDraftService.CleanupResult result = service.purgeAbandoned(cutoff, cutoff);

        assertThat(result.memberDrafts()).isZero();
        assertThat(result.allyDrafts()).isZero();
        verify(affiliationRequests, never()).delete(any(AffiliationRequest.class));
        verify(allyRepository, never()).delete(any(Ally.class));
    }

    @Test
    void purgeAbandoned_usesEachKindsOwnCutoff() {
        Instant memberCutoff = Instant.now().minus(15, ChronoUnit.DAYS);
        Instant allyCutoff = Instant.now().minus(30, ChronoUnit.DAYS);
        when(affiliationRequests.findByStatusAndRetainFromCleanupFalseAndUpdatedAtBefore("DRAFT", memberCutoff)).thenReturn(List.of());
        when(allyRepository.findByRegistrationStatusAndRegistrationSubmittedAtIsNullAndRegistrationChannelInAndRetainFromCleanupFalseAndUpdatedAtBefore(
                eq(Ally.RegistrationStatus.PENDING), anyList(), eq(allyCutoff))).thenReturn(List.of());

        service.purgeAbandoned(memberCutoff, allyCutoff);

        verify(affiliationRequests).findByStatusAndRetainFromCleanupFalseAndUpdatedAtBefore("DRAFT", memberCutoff);
        verify(allyRepository).findByRegistrationStatusAndRegistrationSubmittedAtIsNullAndRegistrationChannelInAndRetainFromCleanupFalseAndUpdatedAtBefore(
                eq(Ally.RegistrationStatus.PENDING), anyList(), eq(allyCutoff));
    }

    @Test
    void purgeAbandoned_aNullCutoffSkipsThatKindEntirely() {
        RegistrationDraftService.CleanupResult result = service.purgeAbandoned(null, null);

        assertThat(result.memberDrafts()).isZero();
        assertThat(result.allyDrafts()).isZero();
        verify(affiliationRequests, never()).findByStatusAndRetainFromCleanupFalseAndUpdatedAtBefore(any(), any());
    }

    // ─── Retention ──────────────────────────────────────────────────────────

    @Test
    void retainAssistedAffiliationDraft_byItsAdvisor_setsTheFlagNoteAndAudit() {
        AffiliationRequest request = draft(null, true);
        when(promoterRepository.findActiveByUserUuid(advisorUser.getUuid())).thenReturn(Optional.of(advisor));
        when(affiliationRequests.findForUpdateByUuid(request.getUuid())).thenReturn(Optional.of(request));

        DraftRetentionDto dto = service.retainAssistedAffiliationDraft(advisorUser.getUuid(), request.getUuid(), true, "  waiting for the cédula  ");

        assertThat(request.isRetainFromCleanup()).isTrue();
        assertThat(request.getRetainNote()).isEqualTo("waiting for the cédula");
        assertThat(request.getRetainedBy()).isEqualTo(advisorUser.getUuid());
        assertThat(request.getRetainedAt()).isNotNull();
        assertThat(dto.retainFromCleanup()).isTrue();
        verify(affiliationRequests).save(request);
    }

    @Test
    void retainAssistedAffiliationDraft_release_clearsTheNoteAndAudit() {
        AffiliationRequest request = draft(null, true);
        request.setRetainFromCleanup(true);
        request.setRetainNote("x");
        request.setRetainedBy(advisorUser.getUuid());
        request.setRetainedAt(Instant.now());
        when(promoterRepository.findActiveByUserUuid(advisorUser.getUuid())).thenReturn(Optional.of(advisor));
        when(affiliationRequests.findForUpdateByUuid(request.getUuid())).thenReturn(Optional.of(request));

        service.retainAssistedAffiliationDraft(advisorUser.getUuid(), request.getUuid(), false, "ignored");

        assertThat(request.isRetainFromCleanup()).isFalse();
        assertThat(request.getRetainNote()).isNull();
        assertThat(request.getRetainedBy()).isNull();
        assertThat(request.getRetainedAt()).isNull();
    }

    @Test
    void retainAssistedAffiliationDraft_ofAnotherAdvisor_isNotFound() {
        AffiliationRequest request = draft(null, true);
        Promoter other = new Promoter();
        other.setId(99L);
        when(promoterRepository.findActiveByUserUuid(advisorUser.getUuid())).thenReturn(Optional.of(other));
        when(affiliationRequests.findForUpdateByUuid(request.getUuid())).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.retainAssistedAffiliationDraft(advisorUser.getUuid(), request.getUuid(), true, null))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("affiliation_request.not_found");
        assertThat(request.isRetainFromCleanup()).isFalse();
    }

    @Test
    void retainAffiliationDraft_byStaff_worksOnASelfServiceDraft() {
        AffiliationRequest request = draft(user, false);
        when(affiliationRequests.findForUpdateByUuid(request.getUuid())).thenReturn(Optional.of(request));

        service.retainAffiliationDraft(advisorUser.getUuid(), request.getUuid(), true, null);

        assertThat(request.isRetainFromCleanup()).isTrue();
        assertThat(request.getRetainNote()).isNull();
        assertThat(request.getRetainedBy()).isEqualTo(advisorUser.getUuid());
    }

    @Test
    void retainAffiliationDraft_afterTheSubmit_isRefused() {
        AffiliationRequest request = draft(user, false);
        request.setStatus(AffiliationRequest.Status.PENDING.name());
        when(affiliationRequests.findForUpdateByUuid(request.getUuid())).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.retainAffiliationDraft(advisorUser.getUuid(), request.getUuid(), true, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("affiliation_request.retain.invalid_state");
        assertThat(request.isRetainFromCleanup()).isFalse();
    }

    @Test
    void retainAdvisorAllyDraft_byItsAdvisor_setsTheFlag() {
        Ally ally = allyDraft(Ally.RegistrationChannel.ADVISOR);
        when(allyDraftAccess.requireOwnDraft(ally.getUuid(), advisorUser.getUuid())).thenReturn(ally);
        when(allyRepository.findForUpdateByUuid(ally.getUuid())).thenReturn(Optional.of(ally));

        DraftRetentionDto dto = service.retainAdvisorAllyDraft(advisorUser.getUuid(), ally.getUuid(), true, "negotiating");

        assertThat(ally.isRetainFromCleanup()).isTrue();
        assertThat(ally.getRetainNote()).isEqualTo("negotiating");
        assertThat(ally.getRetainedBy()).isEqualTo(advisorUser.getUuid());
        assertThat(dto.retainNote()).isEqualTo("negotiating");
        verify(allyRepository).save(ally);
    }

    @Test
    void retainAdvisorAllyDraft_ofAnotherAdvisor_isNotFound() {
        UUID allyUuid = UUID.randomUUID();
        when(allyDraftAccess.requireOwnDraft(allyUuid, advisorUser.getUuid()))
                .thenThrow(new NoSuchElementException("ally_request.not_found"));

        assertThatThrownBy(() -> service.retainAdvisorAllyDraft(advisorUser.getUuid(), allyUuid, true, null))
                .isInstanceOf(NoSuchElementException.class);
        verify(allyRepository, never()).save(any(Ally.class));
    }

    @Test
    void retainAllyRegistration_byStaff_worksOnASelfServiceRegistration() {
        Ally ally = allyDraft(Ally.RegistrationChannel.SELF_SERVICE);
        when(allyRepository.findForUpdateByUuid(ally.getUuid())).thenReturn(Optional.of(ally));

        service.retainAllyRegistration(advisorUser.getUuid(), ally.getUuid(), true, null);

        assertThat(ally.isRetainFromCleanup()).isTrue();
    }

    @Test
    void retainAllyRegistration_afterTheSubmitOrOfAnAdminAlly_isRefused() {
        Ally submitted = allyDraft(Ally.RegistrationChannel.SELF_SERVICE);
        submitted.setRegistrationSubmittedAt(Instant.now());
        Ally admin = allyDraft(Ally.RegistrationChannel.ADMIN);
        admin.setUuid(UUID.randomUUID());
        when(allyRepository.findForUpdateByUuid(submitted.getUuid())).thenReturn(Optional.of(submitted));
        when(allyRepository.findForUpdateByUuid(admin.getUuid())).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.retainAllyRegistration(advisorUser.getUuid(), submitted.getUuid(), true, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ally_request.retain.invalid_state");
        assertThatThrownBy(() -> service.retainAllyRegistration(advisorUser.getUuid(), admin.getUuid(), true, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(submitted.isRetainFromCleanup()).isFalse();
    }
}
