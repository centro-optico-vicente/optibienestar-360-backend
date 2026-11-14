package com.fenixcore.optibienestar360.modules.promotion.service;

import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign.QuotaTrigger;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentRepository;
import com.fenixcore.optibienestar360.modules.promotion.dto.PaymentPromotionSlotDto;
import com.fenixcore.optibienestar360.modules.promotion.entity.MembershipPromotion;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.Kind;
import com.fenixcore.optibienestar360.modules.promotion.entity.PromotionRedemption;
import com.fenixcore.optibienestar360.modules.promotion.entity.PromotionRedemption.RedemptionStatus;
import com.fenixcore.optibienestar360.modules.promotion.repository.PromotionRedemptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Payment-side rules of a campaign slot (V260, hub ADR 0038 §9): frozen deadline, confirming payment, payment detail. */
@ExtendWith(MockitoExtension.class)
class PromotionSlotStatusServiceTest {

    private static final long MEMBERSHIP_ID = 10L;

    @Mock private PaymentRepository paymentRepository;
    @Mock private PromotionRedemptionRepository repository;

    private Campaign campaign;
    private Promotion promotion;
    private Membership membership;

    private PromotionSlotStatusService service() {
        return new PromotionSlotStatusService(paymentRepository, repository);
    }

    @BeforeEach
    void setUp() {
        campaign = new Campaign();
        campaign.setId(1L);
        campaign.setUuid(UUID.randomUUID());
        campaign.setQuotaTrigger(QuotaTrigger.ON_PAYMENT_CONFIRMED);
        promotion = new Promotion();
        promotion.setId(2L);
        promotion.setUuid(UUID.randomUUID());
        promotion.setName("Navidad");
        promotion.setKind(Kind.ACQUISITION);
        promotion.setCampaign(campaign);
        membership = new Membership();
        membership.setId(MEMBERSHIP_ID);
    }

    private PromotionRedemption pending(Instant expiresAt) {
        MembershipPromotion mp = new MembershipPromotion();
        mp.setId(50L);
        mp.setMembership(membership);
        mp.setPromotion(promotion);
        PromotionRedemption r = new PromotionRedemption();
        r.setUuid(UUID.randomUUID());
        r.setMembershipPromotion(mp);
        r.setPromotion(promotion);
        r.setCampaign(campaign);
        r.setRedemptionStatus(RedemptionStatus.PENDING);
        r.setReservedAt(Instant.now().minus(Duration.ofDays(3)));
        r.setExpiresAt(expiresAt);
        return r;
    }

    private Payment inscription(long id, String status, Instant registeredAt) {
        Payment p = new Payment();
        p.setId(id);
        p.setUuid(UUID.randomUUID());
        p.setDirection("IN");
        p.setInscription(true);
        p.setStatus(status);
        p.setCreatedAt(registeredAt);
        p.setMembership(membership);
        return p;
    }

    @Test
    void aPaymentInReviewRegisteredBeforeTheDeadline_freezesTheSlot_evenAfterTheDeadline() {
        Instant deadline = Instant.now().minus(Duration.ofHours(1));
        PromotionRedemption r = pending(deadline);
        Payment inReview = inscription(1L, "PENDING", deadline.minus(Duration.ofDays(1)));
        when(paymentRepository.findCollectionsOfMembership(MEMBERSHIP_ID, true, List.of("PENDING")))
                .thenReturn(List.of(inReview));

        assertThat(service().freezingPayment(r)).isSameAs(inReview);
        assertThat(service().isFrozen(r)).isTrue();
    }

    @Test
    void aPaymentRegisteredAfterTheDeadline_doesNotFreeze() {
        Instant deadline = Instant.now().minus(Duration.ofHours(1));
        PromotionRedemption r = pending(deadline);
        when(paymentRepository.findCollectionsOfMembership(MEMBERSHIP_ID, true, List.of("PENDING")))
                .thenReturn(List.of(inscription(2L, "PENDING", Instant.now())));

        assertThat(service().isFrozen(r)).isFalse();
    }

    @Test
    void onlyACampaignThatWaitsForTheApproval_freezes_andASlotWithoutDeadlineNever() {
        campaign.setQuotaTrigger(QuotaTrigger.ON_PAYMENT_REGISTERED);
        assertThat(service().isFrozen(pending(Instant.now().plusSeconds(60)))).isFalse();

        campaign.setQuotaTrigger(QuotaTrigger.ON_PAYMENT_CONFIRMED);
        assertThat(service().isFrozen(pending(null))).isFalse();
    }

    @Test
    void aMonthlyFeeDoesNotFreezeAnAcquisitionSlot_butFreezesARecoveryOne() {
        promotion.setKind(Kind.RECOVERY);
        Instant deadline = Instant.now().plus(Duration.ofDays(1));
        PromotionRedemption r = pending(deadline);
        Payment monthly = inscription(3L, "PENDING", Instant.now().minus(Duration.ofHours(2)));
        monthly.setInscription(false);
        when(paymentRepository.findCollectionsOfMembership(MEMBERSHIP_ID, false, List.of("PENDING")))
                .thenReturn(List.of(monthly));

        assertThat(service().isFrozen(r)).isTrue();
        assertThat(PromotionSlotStatusService.ofTheKind(inscription(4L, "PENDING", Instant.now()), r)).isFalse();
    }

    @Test
    void confirmingPayment_isAnApprovedPaymentRegisteredInTime() {
        Instant deadline = Instant.now().minus(Duration.ofHours(1));
        PromotionRedemption r = pending(deadline);
        Payment approved = inscription(5L, "APPROVED", deadline.minus(Duration.ofDays(1)));
        when(paymentRepository.findCollectionsOfMembership(MEMBERSHIP_ID, true, List.of("APPROVED")))
                .thenReturn(List.of(approved));

        assertThat(service().confirmingPayment(r)).isSameAs(approved);
    }

    @Test
    void paymentDetail_saysApprovingItConfirmsThePendingSlot() {
        Instant deadline = Instant.now().plus(Duration.ofDays(2));
        PromotionRedemption r = pending(deadline);
        Payment inReview = inscription(6L, "PENDING", Instant.now().minus(Duration.ofHours(1)));
        when(repository.findFirstByConfirmedByPayment_IdOrderByConfirmedAtDesc(6L)).thenReturn(Optional.empty());
        when(repository.findByMembershipAndStatus(MEMBERSHIP_ID, RedemptionStatus.PENDING)).thenReturn(List.of(r));
        when(paymentRepository.findCollectionsOfMembership(MEMBERSHIP_ID, true, List.of("PENDING")))
                .thenReturn(List.of(inReview));

        PaymentPromotionSlotDto slot = service().slotFor(inReview);

        assertThat(slot).isNotNull();
        assertThat(slot.slotUuid()).isEqualTo(r.getUuid());
        assertThat(slot.redemptionStatus()).isEqualTo(RedemptionStatus.PENDING);
        assertThat(slot.quotaTrigger()).isEqualTo(QuotaTrigger.ON_PAYMENT_CONFIRMED);
        assertThat(slot.confirmsOnApproval()).isTrue();
        assertThat(slot.deadlineFrozen()).isTrue();
        assertThat(slot.effectiveDeadline()).isEqualTo(deadline);
        assertThat(slot.campaign().uuid()).isEqualTo(campaign.getUuid());
    }

    @Test
    void paymentDetail_ofAPayout_orAPaymentWithoutSlot_hasNone() {
        Payment payout = inscription(7L, "APPROVED", Instant.now());
        payout.setDirection("OUT");
        assertThat(service().slotFor(payout)).isNull();

        Payment other = inscription(8L, "APPROVED", Instant.now());
        when(repository.findFirstByConfirmedByPayment_IdOrderByConfirmedAtDesc(8L)).thenReturn(Optional.empty());
        when(repository.findByMembershipAndStatus(MEMBERSHIP_ID, RedemptionStatus.PENDING)).thenReturn(List.of());
        assertThat(service().slotFor(other)).isNull();
    }
}
