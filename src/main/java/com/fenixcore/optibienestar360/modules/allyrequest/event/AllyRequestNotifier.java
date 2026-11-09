package com.fenixcore.optibienestar360.modules.allyrequest.event;

import com.fenixcore.optibienestar360.common.service.EmailService;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType.Segment;
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
 * Emails of the ally request lifecycle (hub ADR 0036), after commit and
 * best-effort like the affiliation request notifiers: a mail failure is
 * logged and never undoes the request.
 * <ul>
 *   <li>{@code RECEIVED} — staff ({@code app.ally-requests.notify-to}, defaults
 *       to the affiliation request recipients / {@code mail.admin}; blank
 *       disables it) get a link to the review screen; the applicant gets a
 *       "we received your request" with the portal link.</li>
 *   <li>{@code APPROVED} / {@code REJECTED} — the applicant, with the reason
 *       when rejected.</li>
 *   <li>{@code INVITED} — the imported ally's contact, with the
 *       "create your password" link.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AllyRequestNotifier {

    static final String STAFF_TEMPLATE = "ally-request-staff-new";

    private final EmailService emailService;
    private final MessageSource messageSource;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${app.ally-requests.notify-to:${app.affiliation-requests.notify-to:${mail.admin:}}}")
    private String notifyTo;

    @Value("${app.ally-requests.notify-locale:es}")
    private String notifyLocale = "es";

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEvent(AllyRequestEvent event) {
        if (event.kind() == AllyRequestEvent.Kind.RECEIVED) {
            notifyStaff(event);
        }
        notifyApplicant(event);
    }

    private void notifyStaff(AllyRequestEvent event) {
        if (notifyTo == null || notifyTo.isBlank()) {
            return;
        }
        Locale locale = Locale.forLanguageTag(notifyLocale == null || notifyLocale.isBlank() ? "es" : notifyLocale);
        try {
            String subject = messageSource.getMessage("email.ally_request.staff_new.subject",
                    new Object[]{nullToEmpty(event.allyName())}, locale);
            emailService.sendTemplated(notifyTo.trim(), subject, STAFF_TEMPLATE, locale, variables(event, locale));
        } catch (RuntimeException ex) {
            log.error("Failed to notify staff of ally request {}", event.allyUuid(), ex);
        }
    }

    private void notifyApplicant(AllyRequestEvent event) {
        if (event.email() == null || event.email().isBlank()) {
            return;
        }
        String template = switch (event.kind()) {
            case RECEIVED -> "ally-request-received";
            case APPROVED -> "ally-request-approved";
            case REJECTED -> "ally-request-rejected";
            case INVITED -> "ally-request-invitation";
        };
        Locale locale = Locale.forLanguageTag(event.locale() == null || event.locale().isBlank() ? "es" : event.locale());
        try {
            String subject = messageSource.getMessage("email.ally_request." + event.kind().name().toLowerCase(Locale.ROOT)
                    + ".subject", new Object[]{nullToEmpty(event.allyName())}, locale);
            emailService.sendTemplated(event.email().trim(), subject, template, locale, variables(event, locale));
        } catch (RuntimeException ex) {
            log.error("Failed to email {} of ally request {}", event.kind(), event.allyUuid(), ex);
        }
    }

    Map<String, Object> variables(AllyRequestEvent event, Locale locale) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("allyName", nullToEmpty(event.allyName()));
        vars.put("contactName", nullToEmpty(event.contactName()));
        vars.put("contactEmail", event.email());
        vars.put("segmentLabel", event.segment() == null ? null : messageSource.getMessage(
                "display.enum.ally_type.segment." + event.segment().name(), null,
                event.segment() == Segment.B2P ? "B2P" : "B2B", locale));
        vars.put("selfService", event.channel() != null && "SELF_SERVICE".equals(event.channel().name()));
        vars.put("promoterName", event.promoterName());
        vars.put("note", event.note());
        vars.put("reason", event.reason());
        vars.put("activationUrl", event.activationUrl());
        vars.put("published", event.published());
        vars.put("portalUrl", base() + "/allies");
        vars.put("reviewUrl", base() + "/dashboard/ally-requests/" + event.allyUuid());
        return vars;
    }

    private String base() {
        String base = frontendUrl == null ? "" : frontendUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
