package com.fenixcore.optibienestar360.modules.notification.service;

import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRoleRepository;
import com.fenixcore.optibienestar360.modules.notification.dto.NotificationEnqueueCommand;
import com.fenixcore.optibienestar360.modules.notification.dto.UserNotificationCommand;
import com.fenixcore.optibienestar360.modules.notification.entity.UserNotification;
import com.fenixcore.optibienestar360.modules.notification.entity.UserNotification.Category;
import com.fenixcore.optibienestar360.modules.notification.entity.UserNotification.Severity;
import com.fenixcore.optibienestar360.modules.notification.repository.UserNotificationRepository;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UserNotificationPublisher}: recipient resolution
 * (explicit ids + role / permission fan-out, duplicates collapsed), dedup by
 * key, the optional email copy, validation, and the after-commit variant.
 */
@ExtendWith(MockitoExtension.class)
class UserNotificationPublisherTest {

    @Mock private UserNotificationRepository repository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;
    @Mock private PlatformTransactionManager transactionManager;

    private UserNotificationPublisher sut() {
        return new UserNotificationPublisher(repository, userRoleRepository, userRepository,
                notificationService, transactionManager);
    }

    private static UserNotificationCommand.UserNotificationCommandBuilder base() {
        return UserNotificationCommand.builder()
                .type("PAYMENT_APPROVED")
                .category(Category.PAYMENTS)
                .severity(Severity.SUCCESS)
                .params(Map.of("amount", "10.00"))
                .fallbackTitle("Pago aprobado")
                .fallbackBody("Tu pago fue aprobado")
                .actionUrl("/members/payments")
                .sourceModule("payment")
                .sourceEntityUuid(UUID.randomUUID());
    }

    // ─── Recipients ─────────────────────────────────────────────────────────

    @Test
    void publish_explicitRecipients_writesOneRowEach() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int created = sut().publish(base().recipientUserIds(new LinkedHashSet<>(List.of(1L, 2L))).build());

        assertThat(created).isEqualTo(2);
        ArgumentCaptor<UserNotification> rows = ArgumentCaptor.forClass(UserNotification.class);
        verify(repository, times(2)).save(rows.capture());
        assertThat(rows.getAllValues()).extracting(UserNotification::getRecipientUserId).containsExactly(1L, 2L);
        UserNotification first = rows.getAllValues().getFirst();
        assertThat(first.getType()).isEqualTo("PAYMENT_APPROVED");
        assertThat(first.getCategory()).isEqualTo(Category.PAYMENTS);
        assertThat(first.getSeverity()).isEqualTo(Severity.SUCCESS);
        assertThat(first.getParams()).containsEntry("amount", "10.00");
        assertThat(first.getFallbackTitle()).isEqualTo("Pago aprobado");
        assertThat(first.getActionUrl()).isEqualTo("/members/payments");
        assertThat(first.getReadAt()).isNull();
        verifyNoInteractions(userRoleRepository, notificationService);
    }

    @Test
    void publish_roleAndPermissionFanOut_unionsRecipientsWithoutDuplicates() {
        when(userRoleRepository.findEffectiveUserIdsByRoleNames(eq(List.of("ADMINISTRADOR")), any()))
                .thenReturn(List.of(2L, 3L));
        when(userRoleRepository.findEffectiveUserIdsByPermissionNames(eq(List.of("PAYMENT_APPROVE")), any()))
                .thenReturn(List.of(3L, 4L));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int created = sut().publish(base()
                .recipientUserIds(Set.of(2L))
                .recipientRoles(Set.of("ADMINISTRADOR"))
                .recipientPermissions(Set.of("PAYMENT_APPROVE"))
                .build());

        assertThat(created).isEqualTo(3);
        ArgumentCaptor<UserNotification> rows = ArgumentCaptor.forClass(UserNotification.class);
        verify(repository, times(3)).save(rows.capture());
        assertThat(rows.getAllValues()).extracting(UserNotification::getRecipientUserId)
                .containsExactlyInAnyOrder(2L, 3L, 4L);
    }

    @Test
    void publish_roleWithoutUsers_createsNothing() {
        when(userRoleRepository.findEffectiveUserIdsByRoleNames(anyCollection(), any())).thenReturn(List.of());

        assertThat(sut().publish(base().recipientRoles(Set.of("FINANZAS")).build())).isZero();
        verify(repository, never()).save(any());
    }

    // ─── Dedup ──────────────────────────────────────────────────────────────

    @Test
    void publish_dedupKey_skipsRecipientsThatAlreadyHaveTheNotice() {
        when(repository.existsByRecipientUserIdAndTypeAndDedupKey(1L, "PAYMENT_APPROVED", "payment:42"))
                .thenReturn(true);
        when(repository.existsByRecipientUserIdAndTypeAndDedupKey(2L, "PAYMENT_APPROVED", "payment:42"))
                .thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int created = sut().publish(base()
                .recipientUserIds(new LinkedHashSet<>(List.of(1L, 2L)))
                .dedupKey("payment:42")
                .build());

        assertThat(created).isEqualTo(1);
        ArgumentCaptor<UserNotification> row = ArgumentCaptor.forClass(UserNotification.class);
        verify(repository).save(row.capture());
        assertThat(row.getValue().getRecipientUserId()).isEqualTo(2L);
        assertThat(row.getValue().getDedupKey()).isEqualTo("payment:42");
    }

    @Test
    void publish_withoutDedupKey_neverChecksForDuplicates() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sut().publish(base().recipientUserIds(Set.of(1L)).build());

        verify(repository, never()).existsByRecipientUserIdAndTypeAndDedupKey(anyLong(), anyString(), anyString());
    }

    // ─── Email copy ─────────────────────────────────────────────────────────

    @Test
    void publish_withEmailCopy_enqueuesOneEmailPerCreatedRow() {
        when(repository.existsByRecipientUserIdAndTypeAndDedupKey(1L, "PAYMENT_APPROVED", "k")).thenReturn(true);
        when(repository.existsByRecipientUserIdAndTypeAndDedupKey(2L, "PAYMENT_APPROVED", "k")).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findAllById(List.of(2L))).thenReturn(List.of(user(2L, "ana@example.com", "en")));

        sut().publish(base()
                .recipientUserIds(new LinkedHashSet<>(List.of(1L, 2L)))
                .dedupKey("k")
                .email(new UserNotificationCommand.EmailCopy("payment-approved", "Pago aprobado", Map.of("x", 1)))
                .build());

        ArgumentCaptor<NotificationEnqueueCommand> mail = ArgumentCaptor.forClass(NotificationEnqueueCommand.class);
        verify(notificationService).enqueue(mail.capture());
        assertThat(mail.getValue().recipientEmail()).isEqualTo("ana@example.com");
        assertThat(mail.getValue().recipientUserId()).isEqualTo(2L);
        assertThat(mail.getValue().recipientLocale()).isEqualTo("en");
        assertThat(mail.getValue().templateCode()).isEqualTo("payment-approved");
        assertThat(mail.getValue().subject()).isEqualTo("Pago aprobado");
        assertThat(mail.getValue().idempotent()).isFalse();
    }

    @Test
    void publish_withoutEmailCopy_neverTouchesTheEmailQueue() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sut().publish(base().recipientUserIds(Set.of(1L)).build());

        verifyNoInteractions(notificationService, userRepository);
    }

    // ─── Validation ─────────────────────────────────────────────────────────

    @Test
    void publish_withoutTypeCategoryOrTitle_isRejected() {
        assertThatThrownBy(() -> sut().publish(base().type(" ").recipientUserIds(Set.of(1L)).build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("user_notification.command.invalid");
        assertThatThrownBy(() -> sut().publish(base().category(null).recipientUserIds(Set.of(1L)).build()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sut().publish(base().fallbackTitle(null).recipientUserIds(Set.of(1L)).build()))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void publish_defaultsSeverityToInfo() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sut().publish(base().severity(null).recipientUserIds(Set.of(1L)).build());

        ArgumentCaptor<UserNotification> row = ArgumentCaptor.forClass(UserNotification.class);
        verify(repository).save(row.capture());
        assertThat(row.getValue().getSeverity()).isEqualTo(Severity.INFO);
    }

    // ─── After commit ───────────────────────────────────────────────────────

    @Test
    void publishAfterCommit_withoutTransaction_publishesRightAway() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sut().publishAfterCommit(base().recipientUserIds(Set.of(1L)).build());

        verify(repository).save(any());
    }

    @Test
    void publishAfterCommit_insideTransaction_waitsForTheCommit() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        TransactionSynchronizationManager.initSynchronization();
        try {
            sut().publishAfterCommit(base().recipientUserIds(Set.of(1L)).build());
            verify(repository, never()).save(any());

            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

            verify(repository).save(any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void publishAfterCommit_neverPropagatesAFailure() {
        when(repository.save(any())).thenThrow(new IllegalStateException("db down"));

        assertThatCode(() -> sut().publishAfterCommit(base().recipientUserIds(Set.of(1L)).build()))
                .doesNotThrowAnyException();
        assertThatCode(() -> sut().publishAfterCommit(base().type(null).recipientUserIds(Set.of(1L)).build()))
                .doesNotThrowAnyException();
    }

    private static User user(Long id, String email, String locale) {
        Person person = new Person();
        person.setLocale(locale);
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setPerson(person);
        return user;
    }
}
