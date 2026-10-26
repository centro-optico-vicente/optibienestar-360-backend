package com.fenixcore.optibienestar360.modules.affiliation.event;

import com.fenixcore.optibienestar360.common.service.EmailService;
import com.fenixcore.optibienestar360.modules.affiliation.entity.AffiliationRequest;
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
 * Tells staff that a new affiliation request (public or assisted) is waiting
 * for review: one email to {@code app.affiliation-requests.notify-to}
 * (defaults to {@code mail.admin}; blank disables it) with the applicant, the
 * plan, the channel, the advisor and a link to the review screen
 * ({@code {app.frontend-url}/dashboard/affiliation-requests/{uuid}}).
 * After-commit and best-effort, like {@link AffiliationRequestNotifier}: a
 * mail failure is logged and never undoes the request. Staff read Spanish
 * ({@code app.affiliation-requests.notify-locale}, default {@code es}).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AffiliationRequestStaffNotifier {

    static final String TEMPLATE = "affiliation-request-staff-new";
    static final String SUBJECT_KEY = "email.affiliation_request.staff_new.subject";

    private final EmailService emailService;
    private final MessageSource messageSource;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${app.affiliation-requests.notify-to:${mail.admin:}}")
    private String notifyTo;

    @Value("${app.affiliation-requests.notify-locale:es}")
    private String notifyLocale = "es";

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEvent(AffiliationRequestEvent event) {
        if (event.kind() != AffiliationRequestEvent.Kind.RECEIVED || notifyTo == null || notifyTo.isBlank()) {
            return;
        }
        Locale locale = Locale.forLanguageTag(notifyLocale == null || notifyLocale.isBlank() ? "es" : notifyLocale);
        try {
            String subject = messageSource.getMessage(SUBJECT_KEY,
                    new Object[]{event.fullName() != null ? event.fullName() : ""}, locale);
            emailService.sendTemplated(notifyTo.trim(), subject, TEMPLATE, locale, variables(event));
        } catch (RuntimeException ex) {
            log.error("Failed to notify staff of affiliation request {}", event.requestUuid(), ex);
        }
    }

    Map<String, Object> variables(AffiliationRequestEvent event) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("fullName", event.fullName() != null ? event.fullName() : "");
        vars.put("applicantEmail", event.email());
        vars.put("planName", event.planName());
        vars.put("advisorName", event.advisorName());
        vars.put("channel", event.channel());
        vars.put("assisted", AffiliationRequest.Channel.ASSISTED.name().equals(event.channel()));
        vars.put("reviewUrl", reviewUrl(event));
        return vars;
    }

    String reviewUrl(AffiliationRequestEvent event) {
        String base = frontendUrl == null ? "" : frontendUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/dashboard/affiliation-requests/" + event.requestUuid();
    }
}
