package com.fenixcore.optibienestar360.modules.promotion.service;

import com.fenixcore.optibienestar360.common.service.EmailService;
import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promotion.entity.MembershipPromotion;
import com.fenixcore.optibienestar360.modules.promotion.entity.PromotionRedemption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Emails about a campaign slot and its payment (V260, hub ADR 0038 §9):
 * <ul>
 *   <li>to the member and their advisor when a PENDING slot expires within
 *       48 h, and when it expired ({@code promotion-slot-notice});</li>
 *   <li>internal reminder to the advisor and whoever approves payments that a
 *       payment in review confirms a slot ({@code campaign-payment-reminder}).</li>
 * </ul>
 * Best-effort: a failed email is logged and never fails the caller.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PromotionSlotNoticeService {

    static final String SLOT_TEMPLATE = "promotion-slot-notice";
    static final String REMINDER_TEMPLATE = "campaign-payment-reminder";
    private static final String EXPIRING_SUBJECT_KEY = "email.promotion_slot.expiring.subject";
    private static final String EXPIRED_SUBJECT_KEY = "email.promotion_slot.expired.subject";
    private static final String REMINDER_SUBJECT_KEY = "email.campaign_payment.reminder.subject";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Caracas");
    private static final DateTimeFormatter DEADLINE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
    private static final Locale DEFAULT_LOCALE = Locale.forLanguageTag("es");

    private final EmailService emailService;
    private final MessageSource messageSource;

    /** The PENDING slot expires at {@code deadline} (within 48 h). @return how many emails were sent */
    public int notifyExpiring(PromotionRedemption r, Instant deadline) {
        return notifySlot(r, deadline, false);
    }

    /** The PENDING slot just expired. @return how many emails were sent */
    public int notifyExpired(PromotionRedemption r) {
        return notifySlot(r, r.getReleasedAt(), true);
    }

    private int notifySlot(PromotionRedemption r, Instant deadline, boolean expired) {
        Membership membership = membershipOf(r);
        Member member = membership != null ? membership.getMember() : null;
        if (member == null) {
            return 0;
        }
        Person person = member.getPerson();
        String memberName = person != null && person.getFullName() != null ? person.getFullName() : "";
        Map<String, Object> vars = new HashMap<>();
        vars.put("memberName", memberName);
        vars.put("promotionName", r.getPromotion() != null ? r.getPromotion().getName() : "");
        vars.put("campaignName", r.getCampaign() != null ? r.getCampaign().getName() : "");
        vars.put("deadline", format(deadline));
        vars.put("expired", expired);
        String subjectKey = expired ? EXPIRED_SUBJECT_KEY : EXPIRING_SUBJECT_KEY;
        int sent = 0;
        if (person != null && hasText(person.getEmail())) {
            Locale locale = localeOf(person);
            Map<String, Object> mine = new HashMap<>(vars);
            mine.put("forAdvisor", false);
            if (send(person.getEmail(), subjectKey, SLOT_TEMPLATE, locale, mine)) sent++;
        }
        Promoter advisor = member.getPromoter();
        if (advisor != null && hasText(advisor.getEmail())) {
            Map<String, Object> theirs = new HashMap<>(vars);
            theirs.put("forAdvisor", true);
            if (send(advisor.getEmail(), subjectKey, SLOT_TEMPLATE, DEFAULT_LOCALE, theirs)) sent++;
        }
        return sent;
    }

    /**
     * Internal reminder that {@code payment} (in review) confirms the slot of
     * its campaign: to the member's advisor and to every address in
     * {@code approverEmails}, once each.
     *
     * @return how many emails were sent
     */
    public int remindPayment(PromotionRedemption r, Payment payment, Instant deadline, String note,
                             Collection<String> approverEmails) {
        Membership membership = membershipOf(r);
        Member member = membership != null ? membership.getMember() : null;
        Set<String> recipients = new LinkedHashSet<>();
        Promoter advisor = member != null ? member.getPromoter() : null;
        if (advisor != null && hasText(advisor.getEmail())) {
            recipients.add(advisor.getEmail().trim().toLowerCase(Locale.ROOT));
        }
        if (approverEmails != null) {
            approverEmails.stream().filter(PromotionSlotNoticeService::hasText)
                    .map(e -> e.trim().toLowerCase(Locale.ROOT))
                    .forEach(recipients::add);
        }
        Person person = member != null ? member.getPerson() : null;
        Map<String, Object> vars = new HashMap<>();
        vars.put("memberName", person != null && person.getFullName() != null ? person.getFullName() : "");
        vars.put("promotionName", r.getPromotion() != null ? r.getPromotion().getName() : "");
        Campaign campaign = r.getCampaign();
        vars.put("campaignName", campaign != null ? campaign.getName() : "");
        vars.put("deadline", format(deadline));
        vars.put("amount", payment.getAmount() != null ? payment.getAmount().toPlainString() : "");
        vars.put("currency", payment.getCurrency() != null ? payment.getCurrency().getCode() : "");
        vars.put("reference", referenceOf(payment));
        vars.put("note", hasText(note) ? note.trim() : null);
        int sent = 0;
        for (String to : recipients) {
            if (send(to, REMINDER_SUBJECT_KEY, REMINDER_TEMPLATE, DEFAULT_LOCALE, vars)) sent++;
        }
        return sent;
    }

    private boolean send(String to, String subjectKey, String template, Locale locale, Map<String, Object> vars) {
        try {
            emailService.sendTemplated(to, messageSource.getMessage(subjectKey, null, locale), template, locale, vars);
            return true;
        } catch (RuntimeException ex) {
            log.error("Failed to send {} to {}", template, to, ex);
            return false;
        }
    }

    static String referenceOf(Payment payment) {
        return payment.getLines().stream()
                .findFirst()
                .map(com.fenixcore.optibienestar360.modules.payment.entity.PaymentLine::getReferenceNumber)
                .orElse(null);
    }

    private static String format(Instant instant) {
        return instant == null ? null : DEADLINE_FORMAT.format(instant.atZone(BUSINESS_ZONE));
    }

    private static Locale localeOf(Person person) {
        return person.getLocale() == null || person.getLocale().isBlank()
                ? DEFAULT_LOCALE : Locale.forLanguageTag(person.getLocale());
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static Membership membershipOf(PromotionRedemption r) {
        MembershipPromotion mp = r.getMembershipPromotion();
        return mp != null ? mp.getMembership() : null;
    }
}
