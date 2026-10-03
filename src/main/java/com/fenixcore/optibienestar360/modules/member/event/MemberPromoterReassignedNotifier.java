package com.fenixcore.optibienestar360.modules.member.event;

import com.fenixcore.optibienestar360.common.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Tells a member who their new advisor is after a portfolio reassignment
 * ("warm handoff"). After-commit and best-effort: a mail failure is logged and
 * never undoes the reassignment.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MemberPromoterReassignedNotifier {

    private static final String TEMPLATE = "promoter-reassigned";
    private static final String SUBJECT_KEY = "email.member.promoter_reassigned.subject";

    private final EmailService emailService;
    private final MessageSource messageSource;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReassigned(MemberPromoterReassignedEvent event) {
        if (event.memberEmail() == null || event.memberEmail().isBlank()) {
            return;
        }
        Locale locale = resolveLocale(event.memberLocale());
        Map<String, Object> vars = new HashMap<>();
        vars.put("fullName", event.memberFullName() != null ? event.memberFullName() : "");
        vars.put("promoterName", event.promoterName());
        vars.put("promoterPhone", event.promoterPhone());
        vars.put("promoterEmail", event.promoterEmail());
        try {
            String subject = messageSource.getMessage(SUBJECT_KEY, null, locale);
            emailService.sendTemplated(event.memberEmail(), subject, TEMPLATE, locale, vars);
        } catch (RuntimeException ex) {
            log.error("Failed to notify member {} of their new promoter", event.memberEmail(), ex);
        }
    }

    private static Locale resolveLocale(String tag) {
        if (tag == null || tag.isBlank()) return Locale.forLanguageTag("es");
        return Locale.forLanguageTag(tag);
    }
}
