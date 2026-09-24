package com.fenixcore.optibienestar360.modules.scheduling.service.runners;

import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.notification.dto.NotificationEnqueueCommand;
import com.fenixcore.optibienestar360.modules.notification.service.NotificationChannelResolver;
import com.fenixcore.optibienestar360.modules.notification.service.NotificationChannelResolver.RecipientType;
import com.fenixcore.optibienestar360.modules.notification.service.NotificationService;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Shared helper for the two membership-reminder job runners (payment-reminder
 * and payment-overdue). Turns a {@link Membership} into a queue entry:
 * resolves the titular's email + locale, renders the localized subject, and
 * pushes a {@link NotificationEnqueueCommand} onto the persistent queue (the
 * {@code NotificationDispatchJobRunner} does the actual send).
 *
 * <p>Enqueue is non-idempotent by design — the reminder jobs fire on an exact
 * day per membership cycle (3 days before due / the grace nudge day), so the
 * calendar match is the dedup, and the queue key (module, entity, template)
 * has no date component to distinguish successive cycles.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MembershipReminderEnqueuer {

    static final String SOURCE_MODULE = "membership";

    private final NotificationService notificationService;
    private final MessageSource messageSource;
    private final NotificationChannelResolver channelResolver;

    /**
     * @return {@code true} when a notification was enqueued; {@code false} when
     *         the titular has no email to send to (skipped).
     */
    public boolean enqueue(Membership membership, String templateCode, String subjectKey) {
        Person person = personFor(membership);
        if (person == null) {
            log.warn("Membership {} has no person — skipping reminder", membership.getUuid());
            return false;
        }
        String email = person.getEmail();
        if (email == null || email.isBlank()) {
            log.debug("Member {} has no email — skipping reminder for membership {}",
                    person.getUuid(), membership.getUuid());
            return false;
        }

        return enqueueToRecipient(email, person.getLocale(), membership, templateCode, subjectKey, RecipientType.MEMBER);
    }

    /**
     * Same reminder content, sent to an explicit recipient (e.g. the member's
     * promoter, V153) instead of resolving it from {@code membership.member.person}.
     * Shared by both membership-reminder job runners.
     *
     * @return {@code true} when a notification was enqueued; {@code false}
     *         when {@code email} is null/blank (skipped).
     */
    public boolean enqueueToRecipient(String email, String locale, Membership membership,
                                       String templateCode, String subjectKey, RecipientType recipientType) {
        if (email == null || email.isBlank()) {
            return false;
        }

        // No-op today (EMAIL-only) — the extension point for a future SMS/WhatsApp channel.
        channelResolver.resolve(recipientType, templateCode);

        Locale resolvedLocale = localeOf(locale);
        String subject = messageSource.getMessage(subjectKey, null, resolvedLocale);

        Map<String, Object> vars = new HashMap<>();
        Person person = personFor(membership);
        vars.put("fullName", Optional.ofNullable(person != null ? person.getFullName() : null).orElse(""));
        vars.put("planName", membership.getPlan().getName());
        vars.put("nextDueDate", membership.getNextDueDate());
        vars.put("monthlyFee", membership.getMonthlyFee());
        vars.put("gracePeriodDays", membership.getGracePeriodDays());

        notificationService.enqueue(new NotificationEnqueueCommand(
                email, null, resolvedLocale.getLanguage(), templateCode, subject, vars,
                SOURCE_MODULE, membership.getUuid(), null, false));
        return true;
    }

    private static Person personFor(Membership membership) {
        Member member = membership.getMember();
        return member != null ? member.getPerson() : null;
    }

    private static Locale localeOf(String tag) {
        if (tag == null || tag.isBlank()) return Locale.forLanguageTag("es");
        try {
            return Locale.forLanguageTag(tag);
        } catch (RuntimeException ex) {
            return Locale.forLanguageTag("es");
        }
    }
}
