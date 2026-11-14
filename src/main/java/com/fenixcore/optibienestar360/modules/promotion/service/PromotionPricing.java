package com.fenixcore.optibienestar360.modules.promotion.service;

import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.currency.exception.NoExchangeRateAvailableException;
import com.fenixcore.optibienestar360.modules.currency.service.CurrencyConversionService;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.entity.MembershipCharge.DiscountSource;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import com.fenixcore.optibienestar360.modules.promotion.entity.MembershipPromotion;
import com.fenixcore.optibienestar360.modules.promotion.entity.MembershipPromotion.Status;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion;
import com.fenixcore.optibienestar360.modules.promotion.repository.MembershipPromotionRepository;
import com.fenixcore.optibienestar360.modules.subsidy.service.SubsidyResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Prices what a member owes under their promotion or subsidy (hub ADR 0018).
 * The two never stack: per charge, the larger discount AMOUNT wins (a tie
 * goes to the promotion, hub ADR 0038). A promotion cycle is consumed only
 * when the promotion is the one applied.
 *
 * <p>A FLAT promotion (V255) discounts a fixed amount from each charge it
 * covers, converted from the promotion currency to the charge/payment
 * currency with {@link CurrencyConversionService} at the charge/payment date
 * and capped at the gross amount. A missing rate degrades (ADR 0015 §7): the
 * promotion gives no discount on that charge instead of blocking it.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PromotionPricing {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final MembershipPromotionRepository membershipPromotionRepository;
    private final SubsidyResolver subsidyResolver;
    private final CurrencyConversionService currencyConversionService;

    public record MonthlyPrice(BigDecimal gross, BigDecimal discount, DiscountSource source, MembershipPromotion promotion) {
        public BigDecimal net() {
            return gross.subtract(discount);
        }
    }

    /** Prices one monthly charge of {@code membership} for the period starting on {@code periodStart}. */
    public MonthlyPrice priceMonthly(Membership membership, LocalDate periodStart) {
        BigDecimal gross = membership.getMonthlyFee();
        MembershipPromotion promotion = usableMonthlyPromotion(membership);
        BigDecimal promotionDiscount = promotion != null
                ? discountFor(promotion.getPromotion(), gross, membership.getCurrency(), Instant.now())
                : null;
        BigDecimal subsidyPct = membership.getMember() != null && membership.getMember().getId() != null
                ? subsidyResolver.monthlyPercentage(membership.getMember().getId(), periodStart).orElse(null)
                : null;
        BigDecimal subsidyDiscount = subsidyPct != null && subsidyPct.signum() > 0 ? discount(gross, subsidyPct) : null;

        if (promotionDiscount != null && (subsidyDiscount == null || promotionDiscount.compareTo(subsidyDiscount) >= 0)) {
            consumeCycle(promotion);
            return new MonthlyPrice(gross, promotionDiscount, DiscountSource.PROMOTION, promotion);
        }
        if (subsidyDiscount != null) {
            return new MonthlyPrice(gross, subsidyDiscount, DiscountSource.SUBSIDY, null);
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
                    if (setDiscount(payment, mp.getPromotion(), actor)) {
                        mp.setInscriptionApplied(true);
                    }
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

    /** Sets the promotion discount on {@code payment}; {@code false} when a FLAT amount could not be converted. */
    private boolean setDiscount(Payment payment, Promotion promotion, User actor) {
        Instant asOf = payment.getPaymentDate() != null ? payment.getPaymentDate() : Instant.now();
        BigDecimal amount = discountFor(promotion, payment.getAmount(), payment.getCurrency(), asOf);
        if (amount == null) return false;
        payment.setDiscountAmount(amount);
        payment.setDiscountReason("Promoción: " + promotion.getName() + " (" + discountLabel(promotion) + ")");
        payment.setDiscountedBy(actor);
        payment.setDiscountedAt(Instant.now());
        return true;
    }

    /**
     * The discount {@code promotion} gives on a charge of {@code gross} in
     * {@code currency}: a percentage of it, or the FLAT amount converted to
     * {@code currency} at {@code asOf}, capped at {@code gross}. {@code null}
     * when the FLAT amount has no exchange rate (the promotion is skipped).
     */
    BigDecimal discountFor(Promotion promotion, BigDecimal gross, Currency currency, Instant asOf) {
        if (!promotion.isFlat()) {
            return discount(gross, promotion.getDiscountPct());
        }
        try {
            BigDecimal amount = flatAmountIn(promotion, currency, asOf).setScale(2, RoundingMode.HALF_UP);
            return amount.min(gross);
        } catch (NoExchangeRateAvailableException ex) {
            log.warn("Promotion {}: no exchange rate {} -> {} at {}; no discount applied",
                    promotion.getUuid(), promotion.getDiscountCurrency().getCode(),
                    currency != null ? currency.getCode() : null, asOf);
            return null;
        }
    }

    private BigDecimal flatAmountIn(Promotion promotion, Currency target, Instant asOf) {
        Currency from = promotion.getDiscountCurrency();
        if (from == null || target == null) {
            return promotion.getDiscountAmount();
        }
        return currencyConversionService.convert(promotion.getDiscountAmount(), from, target, asOf).convertedAmount();
    }

    /** "20%" or "5 USD" — the discount as a member reads it (payment reason, notices). */
    public static String discountLabel(Promotion promotion) {
        if (promotion.isFlat()) {
            String code = promotion.getDiscountCurrency() != null ? promotion.getDiscountCurrency().getCode() : "";
            return (promotion.getDiscountAmount().stripTrailingZeros().toPlainString() + " " + code).trim();
        }
        return promotion.getDiscountPct().stripTrailingZeros().toPlainString() + "%";
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
        OffsetDateTime campaignEnd = mp.getPromotion().getCampaign().effectiveEndsAt();
        if (mp.getCyclesRemaining() == null && campaignEnd != null && OffsetDateTime.now().isAfter(campaignEnd)) {
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
