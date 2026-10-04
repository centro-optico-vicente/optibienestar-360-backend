package com.fenixcore.optibienestar360.modules.promotion.service;

import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.entity.MembershipCharge.DiscountSource;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import com.fenixcore.optibienestar360.modules.promotion.entity.MembershipPromotion;
import com.fenixcore.optibienestar360.modules.promotion.entity.MembershipPromotion.Status;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion;
import com.fenixcore.optibienestar360.modules.promotion.repository.MembershipPromotionRepository;
import com.fenixcore.optibienestar360.modules.subsidy.service.SubsidyResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Prices what a member owes under their promotion or subsidy (hub ADR 0018).
 * The two never stack: per charge, the higher % wins. A promotion cycle is
 * consumed only when the promotion is the one applied.
 */
@Component
@RequiredArgsConstructor
public class PromotionPricing {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final MembershipPromotionRepository membershipPromotionRepository;
    private final SubsidyResolver subsidyResolver;

    public record MonthlyPrice(BigDecimal gross, BigDecimal discount, DiscountSource source, MembershipPromotion promotion) {
        public BigDecimal net() {
            return gross.subtract(discount);
        }
    }

    /** Prices one monthly charge of {@code membership} for the period starting on {@code periodStart}. */
    public MonthlyPrice priceMonthly(Membership membership, LocalDate periodStart) {
        BigDecimal gross = membership.getMonthlyFee();
        MembershipPromotion promotion = usableMonthlyPromotion(membership);
        BigDecimal promotionPct = promotion != null ? promotion.getPromotion().getDiscountPct() : null;
        BigDecimal subsidyPct = membership.getMember() != null && membership.getMember().getId() != null
                ? subsidyResolver.monthlyPercentage(membership.getMember().getId(), periodStart).orElse(null)
                : null;

        if (promotionPct != null && (subsidyPct == null || promotionPct.compareTo(subsidyPct) > 0)) {
            consumeCycle(promotion);
            return new MonthlyPrice(gross, discount(gross, promotionPct), DiscountSource.PROMOTION, promotion);
        }
        if (subsidyPct != null && subsidyPct.signum() > 0) {
            return new MonthlyPrice(gross, discount(gross, subsidyPct), DiscountSource.SUBSIDY, null);
        }
        return new MonthlyPrice(gross, BigDecimal.ZERO, null, null);
    }

    /**
     * Titular inscription: discounts the not-yet-saved inscription payment of a
     * membership whose promotion covers the inscription, once.
     */
    public void applyInscriptionDiscount(Payment payment, User actor) {
        if (!eligibleInscriptionPayment(payment)) return;
        membershipPromotionRepository.findOngoing(payment.getMembership().getId())
                .filter(mp -> mp.getPromotion().discountsInscription() && !mp.isInscriptionApplied())
                .ifPresent(mp -> {
                    setDiscount(payment, mp.getPromotion(), actor);
                    mp.setInscriptionApplied(true);
                });
    }

    /** Extra-beneficiary inscription fee, when the promotion opts in ({@code covers_extra_beneficiaries}). */
    public void applyExtraBeneficiaryDiscount(Payment payment) {
        if (!eligibleInscriptionPayment(payment)) return;
        membershipPromotionRepository.findOngoing(payment.getMembership().getId())
                .map(MembershipPromotion::getPromotion)
                .filter(p -> p.discountsInscription() && p.isCoversExtraBeneficiaries())
                .ifPresent(p -> setDiscount(payment, p, null));
    }

    private static boolean eligibleInscriptionPayment(Payment payment) {
        return payment.isInscription()
                && payment.getAmount() != null
                && payment.getDiscountAmount() == null
                && payment.getMembership() != null
                && payment.getMembership().getId() != null;
    }

    private static void setDiscount(Payment payment, Promotion promotion, User actor) {
        payment.setDiscountAmount(discount(payment.getAmount(), promotion.getDiscountPct()));
        payment.setDiscountReason("Promoción: " + promotion.getName());
        payment.setDiscountedBy(actor);
        payment.setDiscountedAt(Instant.now());
    }

    /** The ongoing promotion when it still discounts monthly fees; closes it when it has run out. */
    private MembershipPromotion usableMonthlyPromotion(Membership membership) {
        if (membership.getId() == null) return null;
        MembershipPromotion mp = membershipPromotionRepository.findOngoing(membership.getId()).orElse(null);
        if (mp == null || !mp.getPromotion().discountsMonthly()) return null;
        if (mp.getCyclesRemaining() != null && mp.getCyclesRemaining() <= 0) {
            PromotionAssignmentService.end(mp, Status.CONSUMED, "Ciclos agotados");
            return null;
        }
        if (mp.getCyclesRemaining() == null
                && OffsetDateTime.now().isAfter(mp.getPromotion().getCampaign().getEndsAt())) {
            PromotionAssignmentService.end(mp, Status.CONSUMED, "La campaña terminó");
            return null;
        }
        return mp;
    }

    private static void consumeCycle(MembershipPromotion mp) {
        if (mp.getCyclesRemaining() == null) return;
        int left = mp.getCyclesRemaining() - 1;
        mp.setCyclesRemaining(left);
        if (left == 0) {
            PromotionAssignmentService.end(mp, Status.CONSUMED, "Ciclos agotados");
        }
    }

    static BigDecimal discount(BigDecimal amount, BigDecimal pct) {
        BigDecimal value = amount.multiply(pct).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        return value.min(amount);
    }
}
