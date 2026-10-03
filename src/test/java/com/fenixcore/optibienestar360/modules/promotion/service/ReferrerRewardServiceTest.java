package com.fenixcore.optibienestar360.modules.promotion.service;

import com.fenixcore.optibienestar360.common.service.EmailService;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentRepository;
import com.fenixcore.optibienestar360.modules.promoter.service.ReferralService;
import com.fenixcore.optibienestar360.modules.promotion.entity.MembershipPromotion;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion;
import com.fenixcore.optibienestar360.modules.promotion.repository.MembershipPromotionRepository;
import com.fenixcore.optibienestar360.modules.subsidy.entity.Subsidy;
import com.fenixcore.optibienestar360.modules.subsidy.repository.SubsidyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReferrerRewardServiceTest {

    @Mock private MembershipPromotionRepository membershipPromotionRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private SubsidyRepository subsidyRepository;
    @Mock private ReferralService referralService;
    @Mock private EmailService emailService;
    @Mock private MessageSource messageSource;

    private ReferrerRewardService service() {
        return new ReferrerRewardService(membershipPromotionRepository, paymentRepository, subsidyRepository,
                referralService, emailService, messageSource);
    }

    private Payment payment(String amount, String discount) {
        Membership membership = new Membership();
        membership.setId(10L);
        membership.setMember(new Member());
        Payment p = new Payment();
        p.setId(1L);
        p.setDirection("IN");
        p.setMembership(membership);
        p.setAmount(new BigDecimal(amount));
        p.setDiscountAmount(discount == null ? null : new BigDecimal(discount));
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(p));
        return p;
    }

    private MembershipPromotion owedReward(String pct, Integer months) {
        Promotion promotion = new Promotion();
        promotion.setUuid(UUID.randomUUID());
        promotion.setName("Trae un amigo");
        promotion.setReferrerRewardPct(pct == null ? null : new BigDecimal(pct));
        promotion.setReferrerRewardCycles(months);
        Member referrer = new Member();
        referrer.setUuid(UUID.randomUUID());
        MembershipPromotion mp = new MembershipPromotion();
        mp.setPromotion(promotion);
        mp.setCodeOwnerMember(referrer);
        when(membershipPromotionRepository
                .findFirstByMembership_IdAndCodeOwnerMemberIsNotNullAndReferrerRewardGrantedFalseAndActiveTrue(10L))
                .thenReturn(Optional.of(mp));
        return mp;
    }

    @Test
    void grantsTheReferrerASubsidy_forTheConfiguredMonths_startingNextMonth() {
        payment("25.00", null);
        MembershipPromotion mp = owedReward("100.00", 1);

        service().grantFor(1L);

        ArgumentCaptor<Subsidy> saved = ArgumentCaptor.forClass(Subsidy.class);
        verify(subsidyRepository).save(saved.capture());
        Subsidy reward = saved.getValue();
        LocalDate nextMonth = LocalDate.now().withDayOfMonth(1).plusMonths(1);
        assertThat(reward.getMember()).isSameAs(mp.getCodeOwnerMember());
        assertThat(reward.getMonthlyPercentage()).isEqualByComparingTo("100.00");
        assertThat(reward.getValidFrom()).isEqualTo(nextMonth);
        assertThat(reward.getValidUntil()).isEqualTo(nextMonth.plusMonths(1).minusDays(1));
        assertThat(mp.isReferrerRewardGranted()).isTrue();
    }

    @Test
    void aFullyDiscountedPayment_doesNotTriggerTheReward() {
        payment("10.00", "10.00");

        service().grantFor(1L);

        verify(subsidyRepository, never()).save(any());
    }

    @Test
    void aPromotionWithoutReferrerReward_onlyMarksItSettled() {
        payment("25.00", null);
        MembershipPromotion mp = owedReward(null, null);

        service().grantFor(1L);

        verify(subsidyRepository, never()).save(any());
        assertThat(mp.isReferrerRewardGranted()).isTrue();
    }
}
