package com.fenixcore.optibienestar360.modules.promotion.service;

import com.fenixcore.optibienestar360.common.service.EmailService;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentRepository;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import com.fenixcore.optibienestar360.modules.promoter.service.ReferralService;
import com.fenixcore.optibienestar360.modules.promotion.entity.MembershipPromotion;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion;
import com.fenixcore.optibienestar360.modules.promotion.repository.MembershipPromotionRepository;
import com.fenixcore.optibienestar360.modules.subsidy.entity.Subsidy;
import com.fenixcore.optibienestar360.modules.subsidy.repository.SubsidyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The referring member's side of a promotion code (hub ADR 0018 §7): records
 * the referral when the code is used, and — once the referred member makes a
 * first real payment — grants the promotion's optional referrer reward as a
 * subsidy on the referrer's own monthly fee. Promoter and ally codes carry no
 * reward here (promoters already earn commissions).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReferrerRewardService {

    private static final String REWARD_TEMPLATE = "referral-reward";
    private static final String REWARD_SUBJECT_KEY = "email.referral.reward.subject";

    private final MembershipPromotionRepository membershipPromotionRepository;
    private final PaymentRepository paymentRepository;
    private final SubsidyRepository subsidyRepository;
    private final ReferralService referralService;
    private final EmailService emailService;
    private final MessageSource messageSource;

    /** Called when a member's code is used: keeps the existing {@code referrals} ledger in sync. */
    @Transactional
    public void registerReferral(MembershipPromotion mp) {
        if (mp.getCodeOwnerMember() == null) return;
        referralService.registerOnEnrollment(mp.getMembership().getMember(), mp.getCodeUsed())
                .ifPresent(referral -> referral.setRewardPct(mp.getPromotion().getReferrerRewardPct()));
    }

    /**
     * Grants the referrer reward on the referred member's first approved
     * payment with something actually collected (a fully discounted payment
     * does not count). Idempotent through {@code referrer_reward_granted}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void grantFor(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null || !"IN".equals(payment.getDirection()) || payment.getMembership() == null
                || payment.netAmount().signum() <= 0) {
            return;
        }
        MembershipPromotion mp = membershipPromotionRepository
                .findFirstByMembership_IdAndCodeOwnerMemberIsNotNullAndReferrerRewardGrantedFalseAndActiveTrue(
                        payment.getMembership().getId())
                .orElse(null);
        if (mp == null) return;
        Promotion promotion = mp.getPromotion();
        mp.setReferrerRewardGranted(true);
        if (promotion.getReferrerRewardPct() == null) return;

        Member referrer = mp.getCodeOwnerMember();
        LocalDate validFrom = LocalDate.now().withDayOfMonth(1).plusMonths(1);
        Subsidy reward = new Subsidy();
        reward.setMember(referrer);
        reward.setMonthlyPercentage(promotion.getReferrerRewardPct());
        reward.setValidFrom(validFrom);
        reward.setValidUntil(validFrom.plusMonths(promotion.getReferrerRewardCycles()).minusDays(1));
        reward.setReason("Recompensa por recomendar a "
                + nameOf(payment.getMembership().getMember()) + " (promoción " + promotion.getName() + ")");
        subsidyRepository.save(reward);
        log.info("Referrer reward granted: referrer={} pct={} months={} promotion={}",
                referrer.getUuid(), promotion.getReferrerRewardPct(), promotion.getReferrerRewardCycles(),
                promotion.getUuid());
        notifyReferrer(referrer, promotion);
    }

    private void notifyReferrer(Member referrer, Promotion promotion) {
        Person person = referrer.getPerson();
        if (person == null || person.getEmail() == null || person.getEmail().isBlank()) return;
        Locale locale = person.getLocale() == null || person.getLocale().isBlank()
                ? Locale.forLanguageTag("es") : Locale.forLanguageTag(person.getLocale());
        Map<String, Object> vars = new HashMap<>();
        vars.put("fullName", person.getFullName() != null ? person.getFullName() : "");
        vars.put("rewardDescription", promotion.getReferrerRewardPct().stripTrailingZeros().toPlainString()
                + "% en " + promotion.getReferrerRewardCycles() + " mensualidad(es)");
        try {
            emailService.sendTemplated(person.getEmail(),
                    messageSource.getMessage(REWARD_SUBJECT_KEY, null, locale), REWARD_TEMPLATE, locale, vars);
        } catch (RuntimeException ex) {
            log.error("Failed to notify referrer {} of their reward", referrer.getUuid(), ex);
        }
    }

    private static String nameOf(Member member) {
        return member != null && member.getPerson() != null && member.getPerson().getFullName() != null
                ? member.getPerson().getFullName() : "un afiliado";
    }
}
