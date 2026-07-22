package com.fenixcore.optibienestar360.common.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    /**
     * Languages with a dedicated template file (e.g. {@code password-recovery_es.html}).
     * Anything else falls back to the canonical base file (English).
     */
    private static final Set<String> LOCALIZED_TEMPLATE_LANGS = Set.of("es", "en");

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;

    @Value("${mail.from}")
    private String from;

    @Value("${mail.from-name}")
    private String fromName;

    @Async("emailExecutor")
    @Retryable(
            retryFor = MailException.class,
            maxAttemptsExpression = "${mail.retry.max-attempts:4}",
            backoff = @Backoff(
                delayExpression = "${mail.retry.initial-delay-ms:1000}",
                multiplierExpression = "${mail.retry.multiplier:2}"
            )
    )
    public void sendSimple(String to, String subject, String text) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(String.format("%s <%s>", fromName, from));
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(text);
            mailSender.send(msg);
            log.debug("Simple email sent to {}", to);
        } catch (MailException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
            throw e;
        }
    }

    @Async("emailExecutor")
    @Retryable(
            retryFor = MailException.class,
            maxAttemptsExpression = "${mail.retry.max-attempts:4}",
            backoff = @Backoff(
                delayExpression = "${mail.retry.initial-delay-ms:1000}",
                multiplierExpression = "${mail.retry.multiplier:2}"
            )
    )
    public void sendTemplated(String to, String subject, String template, Locale locale,
                              Map<String, Object> variables) {
        String resolvedTemplate = resolveLocalizedTemplate(template, locale);
        try {
            Context ctx = new Context(locale);
            ctx.setVariables(variables);
            String html = templateEngine.process(resolvedTemplate, ctx);

            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(from, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(msg);
            log.debug("Templated email [{}] sent to {}", resolvedTemplate, to);
        } catch (MailException e) {
            log.error("Failed to send templated email [{}] to {}: {}", resolvedTemplate, to, e.getMessage());
            throw e;
        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            // Message *preparation* failed (bad address, encoding, template output) —
            // everything before mailSender.send(). These used to be logged and
            // swallowed, so @Retryable never saw them and the caller was told the
            // mail went out. Wrap in MailPreparationException, which is a
            // MailException, so the retry policy applies and the failure surfaces
            // instead of vanishing.
            log.error("Failed to prepare templated email [{}] to {}: {}", resolvedTemplate, to, e.getMessage());
            throw new MailPreparationException(
                    "Failed to prepare templated email [" + resolvedTemplate + "] to " + to, e);
        }
    }

    /**
     * Picks the locale-specific template file ({@code <base>_<lang>.html}) when
     * the language is in the whitelist; otherwise falls back to the base file
     * which lives in English as the canonical fallback (ADR 0009).
     */
    private String resolveLocalizedTemplate(String baseName, Locale locale) {
        if (locale == null) {
            return baseName;
        }
        String lang = locale.getLanguage();
        if (LOCALIZED_TEMPLATE_LANGS.contains(lang)) {
            return baseName + "_" + lang;
        }
        return baseName;
    }
}
