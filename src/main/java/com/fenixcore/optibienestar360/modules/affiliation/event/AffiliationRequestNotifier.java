package com.fenixcore.optibienestar360.modules.affiliation.event;

import com.fenixcore.optibienestar360.common.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Emails the applicant of an affiliation request: received, approved (with
 * the link to log in — or, for an assisted request without password, the
 * link to create it) or rejected (with the reason). After-commit and
 * best-effort, like {@code MemberPromoterReassignedNotifier}: a mail failure
 * is logged and never undoes the request or the review.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AffiliationRequestNotifier {

    static final String TEMPLATE_RECEIVED = "affiliation-request-received";
    static final String TEMPLATE_APPROVED = "affiliation-request-approved";
    static final String TEMPLATE_REJECTED = "affiliation-request-rejected";

    private static final Locale DEFAULT_LOCALE = Locale.forLanguageTag("es");

    private final EmailService emailService;
    private final MessageSource messageSource;

    /** Base URL of the frontend (no trailing slash needed); the login page is its root ({@code /en} in English). */
    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    /** Shown in the approval email next to the activation link; same property the service uses for the TTL. */
    @Value("${app.affiliation-requests.activation-ttl-hours:72}")
    private long activationTtlHours = 72;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEvent(AffiliationRequestEvent event) {
        if (event.email() == null || event.email().isBlank()) {
            return;
        }
        Locale locale = resolveLocale(event.locale());
        Map<String, Object> vars = new HashMap<>();
        vars.put("fullName", event.fullName() != null ? event.fullName() : "");
        vars.put("planName", event.planName());
        vars.put("advisorName", event.advisorName());
        vars.put("reason", event.reason());
        vars.put("loginUrl", loginUrl(locale));
        // Assisted approval without password: "create your password" link instead of the login one.
        vars.put("activationUrl", event.activationUrl());
        vars.put("activationTtlHours", activationTtlHours);
        String template = switch (event.kind()) {
            case RECEIVED -> TEMPLATE_RECEIVED;
            case APPROVED -> TEMPLATE_APPROVED;
            case REJECTED -> TEMPLATE_REJECTED;
        };
        try {
            String subject = messageSource.getMessage(
                    "email.affiliation_request." + event.kind().name().toLowerCase(Locale.ROOT) + ".subject",
                    null, locale);
            emailService.sendTemplated(event.email(), subject, template, locale, vars);
        } catch (RuntimeException ex) {
            log.error("Failed to email affiliation request {} notice to {}", event.kind(), event.email(), ex);
        }
    }

    String loginUrl(Locale locale) {
        String base = frontendUrl == null ? "" : frontendUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return "en".equals(locale.getLanguage()) ? base + "/en" : base + "/";
    }

    private static Locale resolveLocale(String tag) {
        if (tag == null || tag.isBlank()) return DEFAULT_LOCALE;
        Locale parsed = Locale.forLanguageTag(tag);
        return parsed.getLanguage().isEmpty() ? DEFAULT_LOCALE : parsed;
    }
}
