package com.fenixcore.optibienestar360.modules.promotion.service;

import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.entity.MembershipCharge.DiscountSource;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import com.fenixcore.optibienestar360.modules.promotion.entity.MembershipPromotion;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.AppliesTo;
import com.fenixcore.optibienestar360.modules.promotion.repository.MembershipPromotionRepository;
import com.fenixcore.optibienestar360.modules.subsidy.service.SubsidyResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionPricingTest {

    @Mock private MembershipPromotionRepository membershipPromotionRepository;
    @Mock private SubsidyResolver subsidyResolver;

    private static final LocalDate PERIOD = LocalDate.of(2026, 11, 1);

    private PromotionPricing pricing() {
        return new PromotionPricing(membershipPromotionRepository, subsidyResolver);
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
    void subsidyWins_whenHigherOrEqual_andTheCycleIsKept() {
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
        assertThat(payment.getDiscountReason()).isEqualTo("Promoción: Octubre 20%");
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
}
