package com.fenixcore.optibienestar360.modules.member.event;

import com.fenixcore.optibienestar360.common.service.EmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberPromoterReassignedNotifierTest {

    @Mock private EmailService emailService;
    @Mock private MessageSource messageSource;

    private MemberPromoterReassignedNotifier notifier() {
        return new MemberPromoterReassignedNotifier(emailService, messageSource);
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendsTemplatedEmail_withTheNewAdvisorsContact_inTheMembersLocale() {
        when(messageSource.getMessage(eq("email.member.promoter_reassigned.subject"), any(), any(Locale.class)))
                .thenReturn("Tienes un nuevo asesor");

        notifier().onReassigned(new MemberPromoterReassignedEvent(
                "juan@example.com", "Juan Pérez", "es", "María Gómez", "+58 412 0000000", "maria@example.com"));

        ArgumentCaptor<Map<String, Object>> vars = ArgumentCaptor.forClass(Map.class);
        verify(emailService).sendTemplated(eq("juan@example.com"), eq("Tienes un nuevo asesor"),
                eq("promoter-reassigned"), eq(Locale.forLanguageTag("es")), vars.capture());
        assertThat(vars.getValue())
                .containsEntry("fullName", "Juan Pérez")
                .containsEntry("promoterName", "María Gómez")
                .containsEntry("promoterPhone", "+58 412 0000000");
    }

    @Test
    void skips_whenTheMemberHasNoEmail() {
        notifier().onReassigned(new MemberPromoterReassignedEvent(null, "Juan", "es", "María", null, null));

        verifyNoInteractions(emailService, messageSource);
    }

    @Test
    void neverPropagates_aMailFailure() {
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("s");
        doThrow(new RuntimeException("smtp down")).when(emailService)
                .sendTemplated(anyString(), anyString(), anyString(), any(Locale.class), anyMap());

        assertThatCode(() -> notifier().onReassigned(new MemberPromoterReassignedEvent(
                "juan@example.com", "Juan", null, "María", null, null))).doesNotThrowAnyException();
    }
}
