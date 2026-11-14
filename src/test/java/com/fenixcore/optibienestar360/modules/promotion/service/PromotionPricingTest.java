package com.fenixcore.optibienestar360.modules.promotion.service;

import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.currency.service.CurrencyConversionService;
import com.fenixcore.optibienestar360.modules.currency.service.CurrencyConversionService.ConversionResult;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.entity.MembershipCharge.DiscountSource;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import com.fenixcore.optibienestar360.modules.promotion.entity.MembershipPromotion;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.AppliesTo;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.DiscountType;
import com.fenixcore.optibienestar360.modules.promotion.repository.MembershipPromotionRepository;
import com.fenixcore.optibienestar360.modules.subsidy.service.SubsidyResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionPricingTest {

    @Mock private MembershipPromotionRepository membershipPromotionRepository;
    @Mock private SubsidyResolver subsidyResolver;
    @Mock private CurrencyConversionService currencyConversionService;

    private static final LocalDate PERIOD = LocalDate.of(2026, 11, 1);

    private PromotionPricing pricing() {
        return new PromotionPricing(membershipPromotionRepository, subsidyResolver, currencyConversionService);
    }

    private static Currency currency(long id, String code) {
        Currency c = new Currency();
        c.setId(id);
        c.setCode(code);
        c.setDecimalPlaces((short) 2);
        return c;
    }

    /** Turns an ongoing promotion into a FLAT one of {@code amount} in {@code currency}. */
    private static void flat(MembershipPromotion mp, String amount, Currency currency) {
        Promotion promotion = mp.getPromotion();
        promotion.setDiscountType(DiscountType.FLAT);
        promotion.setDiscountPct(null);
        promotion.setDiscountAmount(new BigDecimal(amount));
        promotion.setDiscountCurrency(currency);
    }

    private void rate(Currency from, Currency to, String rate) {
        when(currencyConversionService.convert(any(BigDecimal.class), eq(from), eq(to), any(Instant.class)))
                .thenAnswer(inv -> {
                    BigDecimal amount = inv.getArgument(0);
                    BigDecimal converted = amount.multiply(new BigDecimal(rate));
                    return new ConversionResult(amount, converted, new BigDecimal(rate), PERIOD);
                });
    }

    private Membership membership() {
        Member member = new Member();
        member.setId(5L);
        Membership m = new Membership();
        m.setId(10L);
        m.setMember(member);
        m.setMonthlyFee(new BigDecimal("25.00"));
        return m;
    }

    private MembershipPromotion ongoing(String pct, AppliesTo appliesTo, Integer cyclesRemaining) {
        Campaign campaign = new Campaign();
        campaign.setEndsAt(OffsetDateTime.now().plusMonths(6));
        Promotion promotion = new Promotion();
        promotion.setName("Octubre 20%");
        promotion.setCampaign(campaign);
        promotion.setDiscountPct(new BigDecimal(pct));
        promotion.setAppliesTo(appliesTo);
        MembershipPromotion mp = new MembershipPromotion();
        mp.setPromotion(promotion);
        mp.setCyclesRemaining(cyclesRemaining);
        mp.setStatus("ACTIVE");
        when(membershipPromotionRepository.findOngoing(10L)).thenReturn(Optional.of(mp));
        return mp;
    }

    private void subsidy(String pct) {
        lenient().when(subsidyResolver.monthlyPercentage(anyLong(), any()))
                .thenReturn(pct == null ? Optional.empty() : Optional.of(new BigDecimal(pct)));
    }

    @Test
    void promotionWins_whenHigherThanTheSubsidy_andConsumesACycle() {
        MembershipPromotion mp = ongoing("20.00", AppliesTo.MONTHLY, 3);
        subsidy("10.00");

        var price = pricing().priceMonthly(membership(), PERIOD);

        assertThat(price.discount()).isEqualByComparingTo("5.00");
        assertThat(price.net()).isEqualByComparingTo("20.00");
        assertThat(price.source()).isEqualTo(DiscountSource.PROMOTION);
        assertThat(mp.getCyclesRemaining()).isEqualTo(2);
    }

    @Test
    void subsidyWins_whenHigher_andTheCycleIsKept() {
        MembershipPromotion mp = ongoing("20.00", AppliesTo.MONTHLY, 3);
        subsidy("50.00");

        var price = pricing().priceMonthly(membership(), PERIOD);

        assertThat(price.net()).isEqualByComparingTo("12.50");
        assertThat(price.source()).isEqualTo(DiscountSource.SUBSIDY);
        assertThat(mp.getCyclesRemaining()).isEqualTo(3);
    }

    @Test
    void partialSubsidyAlone_nowReducesTheCharge() {
        when(membershipPromotionRepository.findOngoing(10L)).thenReturn(Optional.empty());
        subsidy("50.00");

        var price = pricing().priceMonthly(membership(), PERIOD);

        assertThat(price.net()).isEqualByComparingTo("12.50");
        assertThat(price.source()).isEqualTo(DiscountSource.SUBSIDY);
    }

    @Test
    void lastCycle_marksThePromotionConsumed() {
        MembershipPromotion mp = ongoing("100.00", AppliesTo.MONTHLY, 1);
        subsidy(null);

        var price = pricing().priceMonthly(membership(), PERIOD);

        assertThat(price.net()).isEqualByComparingTo("0.00");
        assertThat(mp.getCyclesRemaining()).isZero();
        assertThat(mp.getStatus()).isEqualTo("CONSUMED");
        assertThat(mp.getEndedAt()).isNotNull();
    }

    @Test
    void inscriptionOnlyPromotion_doesNotTouchMonthlyCharges() {
        ongoing("50.00", AppliesTo.INSCRIPTION, null);
        subsidy(null);

        assertThat(pricing().priceMonthly(membership(), PERIOD).discount()).isEqualByComparingTo("0");
    }

    @Test
    void inscriptionDiscount_isAppliedOnce_withAReadableReason() {
        MembershipPromotion mp = ongoing("50.00", AppliesTo.BOTH, 3);
        Payment payment = new Payment();
        payment.setMembership(membership());
        payment.setInscription(true);
        payment.setAmount(new BigDecimal("10.00"));

        pricing().applyInscriptionDiscount(payment, null);

        assertThat(payment.getDiscountAmount()).isEqualByComparingTo("5.00");
        assertThat(payment.getDiscountReason()).isEqualTo("Promoción: Octubre 20% (50%)");
        assertThat(payment.getDiscountedAt()).isNotNull();
        assertThat(mp.isInscriptionApplied()).isTrue();

        Payment second = new Payment();
        second.setMembership(payment.getMembership());
        second.setInscription(true);
        second.setAmount(new BigDecimal("10.00"));
        pricing().applyInscriptionDiscount(second, null);
        assertThat(second.getDiscountAmount()).isNull();
    }

    @Test
    void extraBeneficiaryDiscount_onlyWhenThePromotionCoversThem() {
        MembershipPromotion mp = ongoing("50.00", AppliesTo.INSCRIPTION, null);
        Payment payment = new Payment();
        payment.setMembership(membership());
        payment.setInscription(true);
        payment.setAmount(new BigDecimal("5.00"));

        pricing().applyExtraBeneficiaryDiscount(payment);
        assertThat(payment.getDiscountAmount()).isNull();

        mp.getPromotion().setCoversExtraBeneficiaries(true);
        pricing().applyExtraBeneficiaryDiscount(payment);
        assertThat(payment.getDiscountAmount()).isEqualByComparingTo("2.50");
    }

    @Test
    void tie_goesToThePromotion() {
        MembershipPromotion mp = ongoing("50.00", AppliesTo.MONTHLY, 3);
        subsidy("50.00");

        var price = pricing().priceMonthly(membership(), PERIOD);

        assertThat(price.source()).isEqualTo(DiscountSource.PROMOTION);
        assertThat(price.discount()).isEqualByComparingTo("12.50");
        assertThat(mp.getCyclesRemaining()).isEqualTo(2);
    }

    @Test
    void flatMonthly_inAnotherCurrency_isConvertedToTheChargeCurrency() {
        Currency usd = currency(1L, "USD");
        Currency ves = currency(2L, "VES");
        MembershipPromotion mp = ongoing("1.00", AppliesTo.MONTHLY, null);
        flat(mp, "2.00", usd);
        rate(usd, ves, "40");
        subsidy(null);
        Membership membership = membership();
        membership.setCurrency(ves);
        membership.setMonthlyFee(new BigDecimal("1000.00"));

        var price = pricing().priceMonthly(membership, PERIOD);

        assertThat(price.discount()).isEqualByComparingTo("80.00");
        assertThat(price.net()).isEqualByComparingTo("920.00");
        assertThat(price.source()).isEqualTo(DiscountSource.PROMOTION);
    }

    @Test
    void flatAmount_isCappedAtTheGross() {
        Currency usd = currency(1L, "USD");
        MembershipPromotion mp = ongoing("1.00", AppliesTo.MONTHLY, null);
        flat(mp, "40.00", usd);
        when(currencyConversionService.convert(any(BigDecimal.class), eq(usd), eq(usd), any(Instant.class)))
                .thenAnswer(inv -> new ConversionResult(inv.getArgument(0), inv.getArgument(0), BigDecimal.ONE, PERIOD));
        subsidy(null);
        Membership membership = membership();
        membership.setCurrency(usd);

        var price = pricing().priceMonthly(membership, PERIOD);

        assertThat(price.discount()).isEqualByComparingTo("25.00");
        assertThat(price.net()).isEqualByComparingTo("0.00");
    }

    @Test
    void flatVsSubsidy_theLargerAmountWins() {
        Currency usd = currency(1L, "USD");
        MembershipPromotion mp = ongoing("1.00", AppliesTo.MONTHLY, 3);
        flat(mp, "5.00", usd);
        when(currencyConversionService.convert(any(BigDecimal.class), eq(usd), eq(usd), any(Instant.class)))
                .thenAnswer(inv -> new ConversionResult(inv.getArgument(0), inv.getArgument(0), BigDecimal.ONE, PERIOD));
        Membership membership = membership();
        membership.setCurrency(usd);

        // 30% of 25.00 = 7.50 > 5.00 → the subsidy wins and the cycle is kept.
        subsidy("30.00");
        var subsidyWins = pricing().priceMonthly(membership, PERIOD);
        assertThat(subsidyWins.source()).isEqualTo(DiscountSource.SUBSIDY);
        assertThat(subsidyWins.discount()).isEqualByComparingTo("7.50");
        assertThat(mp.getCyclesRemaining()).isEqualTo(3);

        // 10% of 25.00 = 2.50 < 5.00 → the flat promotion wins.
        subsidy("10.00");
        var promotionWins = pricing().priceMonthly(membership, PERIOD);
        assertThat(promotionWins.source()).isEqualTo(DiscountSource.PROMOTION);
        assertThat(promotionWins.discount()).isEqualByComparingTo("5.00");
        assertThat(mp.getCyclesRemaining()).isEqualTo(2);
    }

    @Test
    void flatInscription_inAnotherCurrency_isConverted_withTheAmountInTheReason() {
        Currency usd = currency(1L, "USD");
        Currency ves = currency(2L, "VES");
        MembershipPromotion mp = ongoing("1.00", AppliesTo.BOTH, null);
        flat(mp, "5.00", usd);
        rate(usd, ves, "40");
        Payment payment = new Payment();
        payment.setMembership(membership());
        payment.setInscription(true);
        payment.setCurrency(ves);
        payment.setPaymentDate(Instant.now());
        payment.setAmount(new BigDecimal("400.00"));

        pricing().applyInscriptionDiscount(payment, null);

        assertThat(payment.getDiscountAmount()).isEqualByComparingTo("200.00");
        assertThat(payment.getDiscountReason()).isEqualTo("Promoción: Octubre 20% (5 USD)");
        assertThat(mp.isInscriptionApplied()).isTrue();
    }
}
