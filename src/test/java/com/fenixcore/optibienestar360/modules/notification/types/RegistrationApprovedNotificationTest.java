package com.fenixcore.optibienestar360.modules.notification.types;

import com.fenixcore.optibienestar360.modules.notification.dto.UserNotificationCommand;
import com.fenixcore.optibienestar360.modules.notification.entity.UserNotification.Category;
import com.fenixcore.optibienestar360.modules.notification.entity.UserNotification.Severity;
import com.fenixcore.optibienestar360.modules.notification.service.UserNotificationPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Shape of the {@code REGISTRATION_APPROVED} command (the frontend contract of the first type). */
@ExtendWith(MockitoExtension.class)
class RegistrationApprovedNotificationTest {

    @Mock private UserNotificationPublisher publisher;
    @Mock private MessageSource messageSource;

    private RegistrationApprovedNotification sut() {
        return new RegistrationApprovedNotification(publisher, messageSource);
    }

    @Test
    void ally_buildsTheContractCommand_inTheApplicantsLocale() {
        when(messageSource.getMessage(eq("user_notification.registration_approved.title"), any(), anyString(),
                eq(Locale.forLanguageTag("es")))).thenReturn("¡Ya eres parte de OptiBienestar!");
        when(messageSource.getMessage(eq("user_notification.registration_approved.body.ally"), any(), anyString(),
                eq(Locale.forLanguageTag("es")))).thenReturn("Tu solicitud como aliado Óptica Sol fue aprobada.");
        UUID allyUuid = UUID.randomUUID();

        sut().publishAfterCommit(RegistrationApprovedNotification.Kind.ALLY, allyUuid, " Óptica Sol ",
                List.of(5L, 6L), "ally_request", null);

        ArgumentCaptor<UserNotificationCommand> cmd = ArgumentCaptor.forClass(UserNotificationCommand.class);
        verify(publisher).publishAfterCommit(cmd.capture());
        UserNotificationCommand c = cmd.getValue();
        assertThat(c.type()).isEqualTo("REGISTRATION_APPROVED");
        assertThat(c.category()).isEqualTo(Category.ACCOUNT);
        assertThat(c.severity()).isEqualTo(Severity.SUCCESS);
        assertThat(c.params()).containsEntry("kind", "ALLY").containsEntry("name", "Óptica Sol");
        assertThat(c.actionUrl()).isEqualTo("/allies");
        assertThat(c.dedupKey()).isEqualTo("registration-approved:" + allyUuid);
        assertThat(c.sourceModule()).isEqualTo("ally_request");
        assertThat(c.sourceEntityUuid()).isEqualTo(allyUuid);
        assertThat(c.fallbackTitle()).isEqualTo("¡Ya eres parte de OptiBienestar!");
        assertThat(c.fallbackBody()).isEqualTo("Tu solicitud como aliado Óptica Sol fue aprobada.");
        assertThat(c.recipientUserIds()).containsExactly(5L, 6L);
        assertThat(c.email()).isNull();
    }

    @Test
    void member_pointsToTheMemberPortal() {
        when(messageSource.getMessage(anyString(), any(), anyString(), any(Locale.class))).thenReturn("x");
        UUID requestUuid = UUID.randomUUID();

        UserNotificationCommand c = sut().command(RegistrationApprovedNotification.Kind.MEMBER, requestUuid,
                "Juan Pérez", List.of(9L), "affiliation_request", "en");

        assertThat(c.params()).containsEntry("kind", "MEMBER").containsEntry("name", "Juan Pérez");
        assertThat(c.actionUrl()).isEqualTo("/members");
        assertThat(c.dedupKey()).isEqualTo("registration-approved:" + requestUuid);
        verify(messageSource).getMessage(eq("user_notification.registration_approved.body.member"), any(),
                anyString(), eq(Locale.forLanguageTag("en")));
    }

    @Test
    void withoutRecipients_publishesNothing() {
        when(messageSource.getMessage(anyString(), any(), anyString(), any(Locale.class))).thenReturn("x");

        sut().publishAfterCommit(RegistrationApprovedNotification.Kind.MEMBER, UUID.randomUUID(), "Juan",
                List.of(), "affiliation_request", "es");

        verifyNoInteractions(publisher);
    }
}
