package com.fenixcore.optibienestar360.modules.notification.service;

import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.notification.dto.UserNotificationDto;
import com.fenixcore.optibienestar360.modules.notification.entity.UserNotification;
import com.fenixcore.optibienestar360.modules.notification.entity.UserNotification.Category;
import com.fenixcore.optibienestar360.modules.notification.entity.UserNotification.Severity;
import com.fenixcore.optibienestar360.modules.notification.repository.UserNotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UserNotificationService}: every call is scoped to the
 * caller's user id, read / read-all / unread-count, the fixed newest-first
 * order and the DTO mapping. The SQL filters themselves (expiry, unread,
 * own rows) run against Postgres in {@code UserNotificationInboxIT}.
 */
@ExtendWith(MockitoExtension.class)
class UserNotificationServiceTest {

    private static final UUID CALLER = UUID.randomUUID();
    private static final Long CALLER_ID = 7L;

    @Mock private UserNotificationRepository repository;
    @Mock private UserRepository userRepository;

    private UserNotificationService sut() {
        return new UserNotificationService(repository, userRepository);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listMine_scopesToTheCaller_newestFirst_andMapsTheDto() {
        when(userRepository.findIdByUuid(CALLER)).thenReturn(Optional.of(CALLER_ID));
        UserNotification row = row(null);
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row)));

        Page<UserNotificationDto> page = sut().listMine(CALLER, false, "account",
                PageRequest.of(1, 500, Sort.by("type")));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(UserNotificationService.MAX_PAGE_SIZE);
        assertThat(pageable.getValue().getSort()).isEqualTo(UserNotificationService.NEWEST_FIRST);

        UserNotificationDto dto = page.getContent().getFirst();
        assertThat(dto.uuid()).isEqualTo(row.getUuid());
        assertThat(dto.type()).isEqualTo("REGISTRATION_APPROVED");
        assertThat(dto.category()).isEqualTo(Category.ACCOUNT);
        assertThat(dto.severity()).isEqualTo(Severity.SUCCESS);
        assertThat(dto.params()).containsEntry("kind", "ALLY");
        assertThat(dto.title()).isEqualTo("¡Ya eres parte de OptiBienestar!");
        assertThat(dto.body()).isEqualTo("Tu solicitud fue aprobada");
        assertThat(dto.actionUrl()).isEqualTo("/allies");
        assertThat(dto.read()).isFalse();
        assertThat(dto.readAt()).isNull();
    }

    @Test
    void listMine_unpaged_fallsBackToTheFirstCappedPage() {
        Pageable fixed = UserNotificationService.fixedOrder(Pageable.unpaged());

        assertThat(fixed.getPageNumber()).isZero();
        assertThat(fixed.getPageSize()).isEqualTo(UserNotificationService.MAX_PAGE_SIZE);
        assertThat(fixed.getSort()).isEqualTo(UserNotificationService.NEWEST_FIRST);
    }

    @Test
    void listMine_unknownCategory_isRejected() {
        when(userRepository.findIdByUuid(CALLER)).thenReturn(Optional.of(CALLER_ID));

        assertThatThrownBy(() -> sut().listMine(CALLER, false, "NOPE", PageRequest.of(0, 20)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("user_notification.category.invalid");
        verifyNoInteractions(repository);
    }

    @Test
    void parseCategory_isCaseInsensitive_andBlankMeansAll() {
        assertThat(UserNotificationService.parseCategory("payments")).isEqualTo(Category.PAYMENTS);
        assertThat(UserNotificationService.parseCategory(" ")).isNull();
        assertThat(UserNotificationService.parseCategory(null)).isNull();
    }

    @Test
    void unreadCount_countsTheCallersUnreadRows() {
        when(userRepository.findIdByUuid(CALLER)).thenReturn(Optional.of(CALLER_ID));
        when(repository.countUnread(eq(CALLER_ID), any(Instant.class))).thenReturn(3L);

        assertThat(sut().unreadCount(CALLER)).isEqualTo(3L);
    }

    @Test
    void markRead_setsReadAtOnTheCallersRow() {
        when(userRepository.findIdByUuid(CALLER)).thenReturn(Optional.of(CALLER_ID));
        UserNotification row = row(null);
        when(repository.findByUuidAndRecipientUserIdAndActiveTrue(row.getUuid(), CALLER_ID))
                .thenReturn(Optional.of(row));

        sut().markRead(CALLER, row.getUuid());

        assertThat(row.getReadAt()).isNotNull();
    }

    @Test
    void markRead_isIdempotent_keepsTheFirstReadAt() {
        when(userRepository.findIdByUuid(CALLER)).thenReturn(Optional.of(CALLER_ID));
        Instant firstRead = Instant.parse("2026-11-01T10:00:00Z");
        UserNotification row = row(firstRead);
        when(repository.findByUuidAndRecipientUserIdAndActiveTrue(row.getUuid(), CALLER_ID))
                .thenReturn(Optional.of(row));

        sut().markRead(CALLER, row.getUuid());

        assertThat(row.getReadAt()).isEqualTo(firstRead);
    }

    @Test
    void markRead_anotherUsersRow_is404() {
        when(userRepository.findIdByUuid(CALLER)).thenReturn(Optional.of(CALLER_ID));
        UUID foreign = UUID.randomUUID();
        when(repository.findByUuidAndRecipientUserIdAndActiveTrue(foreign, CALLER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut().markRead(CALLER, foreign))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("user_notification.not_found");
    }

    @Test
    void markAllRead_marksOnlyTheCallersRows() {
        when(userRepository.findIdByUuid(CALLER)).thenReturn(Optional.of(CALLER_ID));
        when(repository.markAllRead(eq(CALLER_ID), any(Instant.class))).thenReturn(2);

        assertThat(sut().markAllRead(CALLER)).isEqualTo(2);
    }

    @Test
    void unknownCaller_is404() {
        when(userRepository.findIdByUuid(CALLER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut().unreadCount(CALLER))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("user.not_found");
    }

    private static UserNotification row(Instant readAt) {
        UserNotification n = new UserNotification();
        n.setUuid(UUID.randomUUID());
        n.setRecipientUserId(CALLER_ID);
        n.setType("REGISTRATION_APPROVED");
        n.setCategory(Category.ACCOUNT);
        n.setSeverity(Severity.SUCCESS);
        n.setParams(Map.of("kind", "ALLY", "name", "Óptica Sol"));
        n.setFallbackTitle("¡Ya eres parte de OptiBienestar!");
        n.setFallbackBody("Tu solicitud fue aprobada");
        n.setActionUrl("/allies");
        n.setCreatedAt(Instant.parse("2026-11-01T09:00:00Z"));
        n.setReadAt(readAt);
        return n;
    }
}
