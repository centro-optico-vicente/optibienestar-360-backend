package com.fenixcore.optibienestar360.modules.promotion.service;

import com.fenixcore.optibienestar360.common.service.EmailService;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import com.fenixcore.optibienestar360.modules.promotion.entity.MembershipPromotion;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.Kind;
import com.fenixcore.optibienestar360.modules.promotion.repository.MembershipPromotionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Daily promotion reminders (hub ADR 0018), {@code daysBeforeDue} days ahead
 * of the next payment: "pay on time to keep your discount" for ACQUISITION
 * promotions (they are lost on any delay), and "your promotion ends with
 * this payment" when the upcoming charge is the last discounted one.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PromotionNoticeService {

    private static final String TEMPLATE = "promotion-notice";
    private static final String SUBJECT_KEY = "email.promotion.notice.subject";

    private final MembershipPromotionRepository membershipPromotionRepository;
    private final EmailService emailService;
    private final MessageSource messageSource;

    @Transactional(readOnly = true)
    public int sendDueNotices(LocalDate today, int daysBeforeDue) {
        LocalDate dueDate = today.plusDays(daysBeforeDue);
        int sent = 0;
        for (MembershipPromotion mp : membershipPromotionRepository.findOngoingWithPaymentDueOn(dueDate)) {
            Promotion promotion = mp.getPromotion();
            boolean keepDiscount = promotion.getKind() == Kind.ACQUISITION;
            boolean lastCycle = mp.getCyclesRemaining() != null && mp.getCyclesRemaining() == 1;
            if ((keepDiscount || lastCycle) && notify(mp.getMembership(), promotion, keepDiscount, lastCycle)) {
                sent++;
            }
        }
        return sent;
    }

    private boolean notify(Membership membership, Promotion promotion, boolean keepDiscount, boolean lastCycle) {
        Person person = membership.getMember() != null ? membership.getMember().getPerson() : null;
        if (person == null || person.getEmail() == null || person.getEmail().isBlank()) return false;
        Locale locale = person.getLocale() == null || person.getLocale().isBlank()
                ? Locale.forLanguageTag("es") : Locale.forLanguageTag(person.getLocale());
        Map<String, Object> vars = new HashMap<>();
        vars.put("fullName", person.getFullName() != null ? person.getFullName() : "");
        vars.put("promotionName", promotion.getName());
        vars.put("discountPct", promotion.getDiscountPct().stripTrailingZeros().toPlainString());
        vars.put("nextDueDate", membership.getNextDueDate());
        vars.put("keepDiscount", keepDiscount);
        vars.put("lastCycle", lastCycle);
        try {
            emailService.sendTemplated(person.getEmail(), messageSource.getMessage(SUBJECT_KEY, null, locale),
                    TEMPLATE, locale, vars);
            return true;
        } catch (RuntimeException ex) {
            log.error("Failed to send promotion notice for membership {}", membership.getUuid(), ex);
            return false;
        }
    }
}
