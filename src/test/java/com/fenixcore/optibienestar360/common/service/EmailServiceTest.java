package com.fenixcore.optibienestar360.common.service;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EmailService#sendTemplated}, focused on what the method
 * does when it fails.
 *
 * <p>The behaviour under test is not cosmetic: {@code @Retryable(retryFor =
 * MailException.class)} only fires on a {@code MailException} that actually
 * leaves the method. Preparation failures used to be logged and swallowed, so
 * the retry policy never saw them <em>and</em> the caller was told the mail had
 * gone out. Both paths below must therefore throw.</p>
 */
@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock private JavaMailSender mailSender;
    @Mock private SpringTemplateEngine templateEngine;

    private EmailService service;

    @BeforeEach
    void setup() {
        service = new EmailService(mailSender, templateEngine);
        // @Value fields — no Spring context in a unit test.
        ReflectionTestUtils.setField(service, "from", "no-reply@optibienestar360.com");
        ReflectionTestUtils.setField(service, "fromName", "OptiBienestar 360");
    }

    @Test
    void sendTemplated_whenPreparationFails_throwsMailPreparationException() {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((jakarta.mail.Session) null));
        when(templateEngine.process(anyString(), any(org.thymeleaf.context.IContext.class)))
                .thenReturn("<p>hola</p>");

        // Strict address parsing rejects this, so MimeMessageHelper.setTo raises a
        // MessagingException — the case that used to be swallowed.
        assertThatThrownBy(() -> service.sendTemplated(
                "not a valid address", "Asunto", "welcome", Locale.forLanguageTag("es"), Map.of()))
                .isInstanceOf(MailPreparationException.class);

        // And the half-built message is never handed to the transport.
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void sendTemplated_whenTransportFails_rethrowsSoRetryCanSeeIt() {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((jakarta.mail.Session) null));
        when(templateEngine.process(anyString(), any(org.thymeleaf.context.IContext.class)))
                .thenReturn("<p>hola</p>");
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> service.sendTemplated(
                "afiliado@example.com", "Asunto", "welcome", Locale.forLanguageTag("es"), Map.of()))
                .isInstanceOf(MailSendException.class);
    }

    @Test
    void sendTemplated_whenAllGoesWell_sendsTheMessage() {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((jakarta.mail.Session) null));
        when(templateEngine.process(anyString(), any(org.thymeleaf.context.IContext.class)))
                .thenReturn("<p>hola</p>");

        service.sendTemplated("afiliado@example.com", "Asunto", "welcome",
                Locale.forLanguageTag("es"), Map.of());

        verify(mailSender).send(any(MimeMessage.class));
    }
}
