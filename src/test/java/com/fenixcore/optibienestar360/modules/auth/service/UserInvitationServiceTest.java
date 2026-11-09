package com.fenixcore.optibienestar360.modules.auth.service;

import com.fenixcore.optibienestar360.common.service.EmailService;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@link UserInvitationService}: the "create your password" email of a user created without one (hub ADR 0037 §5). */
@ExtendWith(MockitoExtension.class)
class UserInvitationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private AuthService authService;
    @Mock private EmailService emailService;
    @Mock private MessageSource messageSource;

    @InjectMocks
    private UserInvitationService service;

    @Test
    void invite_emailsTheSetupLink_inTheRecipientsLanguage() {
        Person person = new Person();
        person.setLocale("en");
        person.setFullName("Ana Pérez");
        User user = new User();
        user.setUuid(UUID.randomUUID());
        user.setEmail("ana@example.test");
        user.setPerson(person);
        when(userRepository.findByUuid(user.getUuid())).thenReturn(Optional.of(user));
        when(authService.issuePasswordSetupLink(eq(user), eq(Duration.ofHours(72)), eq(Locale.forLanguageTag("en"))))
                .thenReturn("https://app.example.test/en/reset-password?token=abc");
        when(messageSource.getMessage(eq("email.user_invitation.subject"), isNull(), any(Locale.class)))
                .thenReturn("subject");

        service.invite(user.getUuid());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> model = ArgumentCaptor.forClass(Map.class);
        verify(emailService).sendTemplated(eq("ana@example.test"), eq("subject"), eq("user-invitation"),
                eq(Locale.forLanguageTag("en")), model.capture());
        assertThat(model.getValue()).containsEntry("setupUrl", "https://app.example.test/en/reset-password?token=abc")
                .containsEntry("fullName", "Ana Pérez")
                .containsEntry("ttlHours", 72L);
    }

    @Test
    void invite_forAnUnknownUser_is404() {
        UUID unknown = UUID.randomUUID();
        when(userRepository.findByUuid(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.invite(unknown)).isInstanceOf(NoSuchElementException.class);
    }
}
