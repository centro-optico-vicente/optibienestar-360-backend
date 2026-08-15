package com.fenixcore.optibienestar360.modules.ally.service;

import com.fenixcore.optibienestar360.modules.ally.dto.AllyServiceReviewLogDto;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyService;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyService.ReviewStatus;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyServiceReviewLog;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser.AllyRole;
import com.fenixcore.optibienestar360.modules.ally.mapper.AllyMapper;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyServiceRepository;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyServiceReviewLogRepository;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyUserRepository;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyServiceDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AllyServiceReviewService} — the v2 ally-service approval
 * state machine. Locks the legal transitions, the reviewer/reason stamping, the
 * un-publish on removal, the membership guard for ally-owner removal, and the
 * anti-enumeration 404 on the ally-side log.
 */
@ExtendWith(MockitoExtension.class)
class AllyServiceReviewServiceTest {

    @Mock private AllyServiceRepository serviceRepository;
    @Mock private AllyServiceReviewLogRepository logRepository;
    @Mock private AllyUserRepository allyUserRepository;
    @Mock private UserRepository userRepository;
    @Mock private AllyMapper mapper;
    @Mock private AllyServiceImageService imageService;

    private AllyServiceReviewService sut() {
        return new AllyServiceReviewService(serviceRepository, logRepository,
                allyUserRepository, userRepository, mapper, imageService);
    }

    private final UUID actorUuid = UUID.randomUUID();

    // ─── pending queue (?q=) ────────────────────────────────────────────────────

    @Test
    void pendingQueue_withQ_composesSpecAndDelegatesToRepository() {
        AllyServiceDto dto = new AllyServiceDto(UUID.randomUUID(), UUID.randomUUID(), null,
                "Consulta oftalmológica", null, null, null, false,
                ReviewStatus.PROPOSED, null, null, null, false, null, null, true, null, null, null);
        AllyService service = allyService(ReviewStatus.PROPOSED);
        Pageable pageable = PageRequest.of(0, 20);
        when(serviceRepository.findAll(ArgumentMatchers.<Specification<AllyService>>any(), ArgumentMatchers.eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(service)));
        when(mapper.toServiceDto(service, null)).thenReturn(dto);

        var page = sut().pendingQueue(null, null, "lente", pageable);

        assertThat(page.getContent()).containsExactly(dto);
        verify(serviceRepository).findAll(ArgumentMatchers.<Specification<AllyService>>any(), ArgumentMatchers.eq(pageable));
    }

    @Test
    void pendingQueue_blankQ_stillDelegatesToRepository() {
        Pageable pageable = PageRequest.of(0, 20);
        when(serviceRepository.findAll(ArgumentMatchers.<Specification<AllyService>>any(), ArgumentMatchers.eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        var page = sut().pendingQueue(null, null, "  ", pageable);

        assertThat(page.getContent()).isEmpty();
    }

    // ─── approve ──────────────────────────────────────────────────────────────

    @Test
    void approve_movesToApproved_setsReviewerClearsReasonAndLogs() {
        AllyService service = allyService(ReviewStatus.PROPOSED);
        service.setReviewReason("stale reject reason");
        User actor = user();
        when(serviceRepository.findByUuid(service.getUuid())).thenReturn(Optional.of(service));
        when(userRepository.findByUuid(actorUuid)).thenReturn(Optional.of(actor));

        sut().approve(service.getUuid(), actorUuid, "looks good");

        assertThat(service.getReviewStatus()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(service.getReviewedBy()).isSameAs(actor);
        assertThat(service.getReviewedAt()).isNotNull();
        assertThat(service.getReviewReason()).isNull();
        AllyServiceReviewLog logged = captureLog();
        assertThat(logged.getFromStatus()).isEqualTo(ReviewStatus.PROPOSED);
        assertThat(logged.getToStatus()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(logged.getActor()).isSameAs(actor);
        assertThat(logged.getComment()).isEqualTo("looks good");
    }

    @Test
    void approve_invalidState_whenAlreadyApproved() {
        AllyService service = allyService(ReviewStatus.APPROVED);
        when(serviceRepository.findByUuid(service.getUuid())).thenReturn(Optional.of(service));

        assertThatThrownBy(() -> sut().approve(service.getUuid(), actorUuid, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ally_service.approve.invalid_state");
        verify(logRepository, never()).save(any());
    }

    // ─── reject ───────────────────────────────────────────────────────────────

    @Test
    void reject_setsReasonReviewerAndLogs() {
        AllyService service = allyService(ReviewStatus.IN_REVIEW);
        User actor = user();
        when(serviceRepository.findByUuid(service.getUuid())).thenReturn(Optional.of(service));
        when(userRepository.findByUuid(actorUuid)).thenReturn(Optional.of(actor));

        sut().reject(service.getUuid(), actorUuid, "incomplete pricing");

        assertThat(service.getReviewStatus()).isEqualTo(ReviewStatus.REJECTED);
        assertThat(service.getReviewReason()).isEqualTo("incomplete pricing");
        assertThat(service.getReviewedBy()).isSameAs(actor);
        AllyServiceReviewLog logged = captureLog();
        assertThat(logged.getFromStatus()).isEqualTo(ReviewStatus.IN_REVIEW);
        assertThat(logged.getToStatus()).isEqualTo(ReviewStatus.REJECTED);
    }

    // ─── admin remove ─────────────────────────────────────────────────────────

    @Test
    void adminRemove_fromApproved_unpublishesAndLogs() {
        AllyService service = allyService(ReviewStatus.APPROVED);
        service.setPublished(true);
        User actor = user();
        when(serviceRepository.findByUuid(service.getUuid())).thenReturn(Optional.of(service));
        when(userRepository.findByUuid(actorUuid)).thenReturn(Optional.of(actor));

        sut().adminRemove(service.getUuid(), actorUuid, "contract ended");

        assertThat(service.getReviewStatus()).isEqualTo(ReviewStatus.REMOVED);
        assertThat(service.isPublished()).isFalse();
        assertThat(service.getReviewReason()).isEqualTo("contract ended");
        assertThat(service.getReviewedBy()).isSameAs(actor);
        assertThat(captureLog().getToStatus()).isEqualTo(ReviewStatus.REMOVED);
    }

    @Test
    void adminRemove_invalidState_whenNotApproved() {
        AllyService service = allyService(ReviewStatus.PROPOSED);
        when(serviceRepository.findByUuid(service.getUuid())).thenReturn(Optional.of(service));
        when(userRepository.findByUuid(actorUuid)).thenReturn(Optional.of(user()));

        assertThatThrownBy(() -> sut().adminRemove(service.getUuid(), actorUuid, "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ally_service.remove.invalid_state");
        verify(logRepository, never()).save(any());
    }

    // ─── ally-owner remove ────────────────────────────────────────────────────

    @Test
    void allyRemove_ownerMembership_blankReason_usesDefaultAndLogs() {
        AllyService service = allyService(ReviewStatus.APPROVED);
        User owner = user();
        AllyUser membership = membership(AllyRole.OWNER, owner);
        when(serviceRepository.findByUuid(service.getUuid())).thenReturn(Optional.of(service));
        when(allyUserRepository.findActiveByAllyUuidAndUserUuid(service.getAlly().getUuid(), actorUuid))
                .thenReturn(Optional.of(membership));

        sut().allyRemove(service.getUuid(), actorUuid, "  ");

        assertThat(service.getReviewStatus()).isEqualTo(ReviewStatus.REMOVED);
        assertThat(service.getReviewedBy()).isSameAs(owner);   // actor = the membership's user
        assertThat(service.getReviewReason()).isEqualTo("Retirado por el aliado");
        assertThat(captureLog().getToStatus()).isEqualTo(ReviewStatus.REMOVED);
    }

    @Test
    void allyRemove_deniedForViewerMembership() {
        AllyService service = allyService(ReviewStatus.APPROVED);
        when(serviceRepository.findByUuid(service.getUuid())).thenReturn(Optional.of(service));
        when(allyUserRepository.findActiveByAllyUuidAndUserUuid(service.getAlly().getUuid(), actorUuid))
                .thenReturn(Optional.of(membership(AllyRole.VIEWER, user())));

        assertThatThrownBy(() -> sut().allyRemove(service.getUuid(), actorUuid, "bye"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("ally_service.remove.not_allowed");
        verify(logRepository, never()).save(any());
    }

    @Test
    void allyRemove_deniedWhenNoMembership() {
        AllyService service = allyService(ReviewStatus.APPROVED);
        when(serviceRepository.findByUuid(service.getUuid())).thenReturn(Optional.of(service));
        when(allyUserRepository.findActiveByAllyUuidAndUserUuid(service.getAlly().getUuid(), actorUuid))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut().allyRemove(service.getUuid(), actorUuid, "bye"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("ally_service.remove.not_allowed");
    }

    // ─── log scoping ──────────────────────────────────────────────────────────

    @Test
    void allyLog_404_whenCallerHasNoMembership() {
        AllyService service = allyService(ReviewStatus.APPROVED);
        when(serviceRepository.findByUuid(service.getUuid())).thenReturn(Optional.of(service));
        when(allyUserRepository.findActiveByAllyUuidAndUserUuid(service.getAlly().getUuid(), actorUuid))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut().allyLog(service.getUuid(), actorUuid))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("ally_service.not_found");
        verify(logRepository, never()).findByAllyServiceIdOrderByActionAtDesc(any());
    }

    @Test
    void adminLog_returnsMappedHistoryNewestFirst() {
        AllyService service = allyService(ReviewStatus.APPROVED);
        when(serviceRepository.findByUuid(service.getUuid())).thenReturn(Optional.of(service));
        AllyServiceReviewLog entry = new AllyServiceReviewLog();
        entry.setFromStatus(ReviewStatus.PROPOSED);
        entry.setToStatus(ReviewStatus.APPROVED);
        entry.setActionAt(Instant.parse("2026-07-25T12:00:00Z"));
        entry.setComment("ok");
        when(logRepository.findByAllyServiceIdOrderByActionAtDesc(service.getId()))
                .thenReturn(List.of(entry));

        List<AllyServiceReviewLogDto> log = sut().adminLog(service.getUuid());

        assertThat(log).hasSize(1);
        assertThat(log.getFirst().toStatus()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(log.getFirst().actorUuid()).isNull();   // no actor set → null-safe
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private AllyServiceReviewLog captureLog() {
        ArgumentCaptor<AllyServiceReviewLog> captor = ArgumentCaptor.forClass(AllyServiceReviewLog.class);
        verify(logRepository).save(captor.capture());
        return captor.getValue();
    }

    private AllyService allyService(ReviewStatus status) {
        AllyService service = new AllyService();
        service.setId(1L);
        service.setUuid(UUID.randomUUID());
        service.setReviewStatus(status);
        service.setAlly(ally());
        return service;
    }

    private Ally ally() {
        Ally ally = new Ally();
        ally.setUuid(UUID.randomUUID());
        return ally;
    }

    private User user() {
        User u = new User();
        u.setId(9L);
        u.setUuid(UUID.randomUUID());
        return u;
    }

    private AllyUser membership(AllyRole role, User u) {
        AllyUser m = new AllyUser();
        m.setAllyRole(role);
        m.setUser(u);
        return m;
    }
}
