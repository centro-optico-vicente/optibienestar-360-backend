package com.fenixcore.optibienestar360.modules.ally.service;

import com.fenixcore.optibienestar360.modules.ally.dto.AllyProductReviewLogDto;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyProduct;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyProduct.ReviewStatus;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyProductReviewLog;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser.AllyRole;
import com.fenixcore.optibienestar360.modules.ally.mapper.AllyMapper;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyProductRepository;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyProductReviewLogRepository;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyUserRepository;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyProductDto;
import com.fenixcore.optibienestar360.modules.product.entity.Product;
import com.fenixcore.optibienestar360.modules.product.service.ProductImageService;
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
 * Unit tests for {@link AllyProductReviewService} — the v2 ally-service approval
 * state machine. Locks the legal transitions, the reviewer/reason stamping, the
 * un-publish on removal, the membership guard for ally-owner removal, and the
 * anti-enumeration 404 on the ally-side log.
 */
@ExtendWith(MockitoExtension.class)
class AllyProductReviewServiceTest {

    @Mock private AllyProductRepository serviceRepository;
    @Mock private AllyProductReviewLogRepository logRepository;
    @Mock private AllyUserRepository allyUserRepository;
    @Mock private UserRepository userRepository;
    @Mock private AllyMapper mapper;
    @Mock private ProductImageService imageService;

    private AllyProductReviewService sut() {
        return new AllyProductReviewService(serviceRepository, logRepository,
                allyUserRepository, userRepository, mapper, imageService);
    }

    private final UUID actorUuid = UUID.randomUUID();

    // ─── pending queue (?q=) ────────────────────────────────────────────────────

    @Test
    void pendingQueue_withQ_composesSpecAndDelegatesToRepository() {
        AllyProductDto dto = new AllyProductDto(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), Product.ProductType.SERVICE, true, null,
                "Consulta oftalmológica", null, false, null,
                null, null, null, null,
                ReviewStatus.PROPOSED, null, null, null, false, null, true, null, null, null);
        AllyProduct service = allyProduct(ReviewStatus.PROPOSED);
        Pageable pageable = PageRequest.of(0, 20);
        when(serviceRepository.findAll(ArgumentMatchers.<Specification<AllyProduct>>any(), ArgumentMatchers.eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(service)));
        when(mapper.toAllyProductDto(service, null)).thenReturn(dto);

        var page = sut().pendingQueue(null, null, "lente", pageable);

        assertThat(page.getContent()).containsExactly(dto);
        verify(serviceRepository).findAll(ArgumentMatchers.<Specification<AllyProduct>>any(), ArgumentMatchers.eq(pageable));
    }

    @Test
    void pendingQueue_blankQ_stillDelegatesToRepository() {
        Pageable pageable = PageRequest.of(0, 20);
        when(serviceRepository.findAll(ArgumentMatchers.<Specification<AllyProduct>>any(), ArgumentMatchers.eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        var page = sut().pendingQueue(null, null, "  ", pageable);

        assertThat(page.getContent()).isEmpty();
    }

    // ─── approve ──────────────────────────────────────────────────────────────

    @Test
    void approve_movesToApproved_setsReviewerClearsReasonAndLogs() {
        AllyProduct service = allyProduct(ReviewStatus.PROPOSED);
        service.setReviewReason("stale reject reason");
        User actor = user();
        when(serviceRepository.findByUuid(service.getUuid())).thenReturn(Optional.of(service));
        when(userRepository.findByUuid(actorUuid)).thenReturn(Optional.of(actor));

        sut().approve(service.getUuid(), actorUuid, "looks good");

        assertThat(service.getReviewStatus()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(service.getReviewedBy()).isSameAs(actor);
        assertThat(service.getReviewedAt()).isNotNull();
        assertThat(service.getReviewReason()).isNull();
        AllyProductReviewLog logged = captureLog();
        assertThat(logged.getFromStatus()).isEqualTo(ReviewStatus.PROPOSED);
        assertThat(logged.getToStatus()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(logged.getActor()).isSameAs(actor);
        assertThat(logged.getComment()).isEqualTo("looks good");
    }

    @Test
    void approve_invalidState_whenAlreadyApproved() {
        AllyProduct service = allyProduct(ReviewStatus.APPROVED);
        when(serviceRepository.findByUuid(service.getUuid())).thenReturn(Optional.of(service));

        assertThatThrownBy(() -> sut().approve(service.getUuid(), actorUuid, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ally_product.approve.invalid_state");
        verify(logRepository, never()).save(any());
    }

    // ─── reject ───────────────────────────────────────────────────────────────

    @Test
    void reject_setsReasonReviewerAndLogs() {
        AllyProduct service = allyProduct(ReviewStatus.IN_REVIEW);
        User actor = user();
        when(serviceRepository.findByUuid(service.getUuid())).thenReturn(Optional.of(service));
        when(userRepository.findByUuid(actorUuid)).thenReturn(Optional.of(actor));

        sut().reject(service.getUuid(), actorUuid, "incomplete pricing");

        assertThat(service.getReviewStatus()).isEqualTo(ReviewStatus.REJECTED);
        assertThat(service.getReviewReason()).isEqualTo("incomplete pricing");
        assertThat(service.getReviewedBy()).isSameAs(actor);
        AllyProductReviewLog logged = captureLog();
        assertThat(logged.getFromStatus()).isEqualTo(ReviewStatus.IN_REVIEW);
        assertThat(logged.getToStatus()).isEqualTo(ReviewStatus.REJECTED);
    }

    // ─── admin remove ─────────────────────────────────────────────────────────

    @Test
    void adminRemove_fromApproved_unpublishesAndLogs() {
        AllyProduct service = allyProduct(ReviewStatus.APPROVED);
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
        // The exclusive product has no other offer left — its public image is retracted.
        verify(imageService).unpublishIfPresent(service.getProduct().getUuid());
    }

    @Test
    void adminRemove_generalProduct_keepsItsImage() {
        AllyProduct service = allyProduct(ReviewStatus.APPROVED);
        service.getProduct().setOwnerAlly(null);
        when(serviceRepository.findByUuid(service.getUuid())).thenReturn(Optional.of(service));
        when(userRepository.findByUuid(actorUuid)).thenReturn(Optional.of(user()));

        sut().adminRemove(service.getUuid(), actorUuid, "contract ended");

        assertThat(service.getReviewStatus()).isEqualTo(ReviewStatus.REMOVED);
        verify(imageService, never()).unpublishIfPresent(any());
    }

    @Test
    void adminRemove_invalidState_whenNotApproved() {
        AllyProduct service = allyProduct(ReviewStatus.PROPOSED);
        when(serviceRepository.findByUuid(service.getUuid())).thenReturn(Optional.of(service));
        when(userRepository.findByUuid(actorUuid)).thenReturn(Optional.of(user()));

        assertThatThrownBy(() -> sut().adminRemove(service.getUuid(), actorUuid, "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ally_product.remove.invalid_state");
        verify(logRepository, never()).save(any());
    }

    // ─── ally-owner remove ────────────────────────────────────────────────────

    @Test
    void allyRemove_ownerMembership_blankReason_usesDefaultAndLogs() {
        AllyProduct service = allyProduct(ReviewStatus.APPROVED);
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
        AllyProduct service = allyProduct(ReviewStatus.APPROVED);
        when(serviceRepository.findByUuid(service.getUuid())).thenReturn(Optional.of(service));
        when(allyUserRepository.findActiveByAllyUuidAndUserUuid(service.getAlly().getUuid(), actorUuid))
                .thenReturn(Optional.of(membership(AllyRole.VIEWER, user())));

        assertThatThrownBy(() -> sut().allyRemove(service.getUuid(), actorUuid, "bye"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("ally_product.remove.not_allowed");
        verify(logRepository, never()).save(any());
    }

    @Test
    void allyRemove_deniedWhenNoMembership() {
        AllyProduct service = allyProduct(ReviewStatus.APPROVED);
        when(serviceRepository.findByUuid(service.getUuid())).thenReturn(Optional.of(service));
        when(allyUserRepository.findActiveByAllyUuidAndUserUuid(service.getAlly().getUuid(), actorUuid))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut().allyRemove(service.getUuid(), actorUuid, "bye"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("ally_product.remove.not_allowed");
    }

    // ─── log scoping ──────────────────────────────────────────────────────────

    @Test
    void allyLog_404_whenCallerHasNoMembership() {
        AllyProduct service = allyProduct(ReviewStatus.APPROVED);
        when(serviceRepository.findByUuid(service.getUuid())).thenReturn(Optional.of(service));
        when(allyUserRepository.findActiveByAllyUuidAndUserUuid(service.getAlly().getUuid(), actorUuid))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut().allyLog(service.getUuid(), actorUuid))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("ally_product.not_found");
        verify(logRepository, never()).findByAllyProductIdOrderByActionAtDesc(any());
    }

    @Test
    void adminLog_returnsMappedHistoryNewestFirst() {
        AllyProduct service = allyProduct(ReviewStatus.APPROVED);
        when(serviceRepository.findByUuid(service.getUuid())).thenReturn(Optional.of(service));
        AllyProductReviewLog entry = new AllyProductReviewLog();
        entry.setFromStatus(ReviewStatus.PROPOSED);
        entry.setToStatus(ReviewStatus.APPROVED);
        entry.setActionAt(Instant.parse("2026-07-25T12:00:00Z"));
        entry.setComment("ok");
        when(logRepository.findByAllyProductIdOrderByActionAtDesc(service.getId()))
                .thenReturn(List.of(entry));

        List<AllyProductReviewLogDto> log = sut().adminLog(service.getUuid());

        assertThat(log).hasSize(1);
        assertThat(log.getFirst().toStatus()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(log.getFirst().actorUuid()).isNull();   // no actor set → null-safe
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private AllyProductReviewLog captureLog() {
        ArgumentCaptor<AllyProductReviewLog> captor = ArgumentCaptor.forClass(AllyProductReviewLog.class);
        verify(logRepository).save(captor.capture());
        return captor.getValue();
    }

    private AllyProduct allyProduct(ReviewStatus status) {
        AllyProduct service = new AllyProduct();
        service.setId(1L);
        service.setUuid(UUID.randomUUID());
        service.setReviewStatus(status);
        Ally ally = ally();
        service.setAlly(ally);
        Product product = new Product();
        product.setUuid(UUID.randomUUID());
        product.setName("Consulta oftalmológica");
        product.setOwnerAlly(ally);   // exclusive — the V197 backfill shape
        service.setProduct(product);
        return service;
    }

    private Ally ally() {
        Ally ally = new Ally();
        ally.setId(10L);
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
