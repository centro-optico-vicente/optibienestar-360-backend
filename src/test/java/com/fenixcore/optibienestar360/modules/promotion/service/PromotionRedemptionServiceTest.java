package com.fenixcore.optibienestar360.modules.promotion.service;

import com.fenixcore.optibienestar360.modules.campaign.dto.CampaignQuotaDto;
import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign.LifecycleStatus;
import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign.QuotaTrigger;
import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign.UsageWindow;
import com.fenixcore.optibienestar360.modules.campaign.repository.CampaignRepository;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentRepository;
import com.fenixcore.optibienestar360.modules.promotion.entity.MembershipPromotion;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.Kind;
import com.fenixcore.optibienestar360.modules.promotion.entity.PromotionRedemption;
import com.fenixcore.optibienestar360.modules.promotion.entity.PromotionRedemption.RedemptionStatus;
import com.fenixcore.optibienestar360.modules.promotion.repository.PromotionRedemptionRepository;
import com.fenixcore.optibienestar360.modules.promotion.repository.PromotionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Slot lifecycle of a campaign quota (V257, hub ADR 0038 §5-§6). */
@ExtendWith(MockitoExtension.class)
class PromotionRedemptionServiceTest {

    @Mock private PromotionRedemptionRepository repository;
    @Mock private CampaignRepository campaignRepository;
    @Mock private PromotionRepository promotionRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private MembershipBenefitGrantService grantService;

    private static final long MEMBERSHIP_ID = 10L;

    private Campaign campaign;
    private Promotion promotion;
    private long nextId = 500;

    private PromotionRedemptionService service() {
        return new PromotionRedemptionService(repository, campaignRepository, promotionRepository, paymentRepository,
                grantService);
    }

    @BeforeEach
    void setUp() {
        campaign = new Campaign();
        campaign.setId(1L);
        campaign.setUuid(UUID.randomUUID());
        campaign.setStartsAt(OffsetDateTime.now().minusDays(5));
        campaign.setEndsAt(OffsetDateTime.now().plusDays(30));
        promotion = new Promotion();
        promotion.setId(2L);
        promotion.setUuid(UUID.randomUUID());
        promotion.setName("Navidad");
        promotion.setKind(Kind.ACQUISITION);
        promotion.setCampaign(campaign);
        lenient().when(campaignRepository.findForUpdateById(1L)).thenReturn(Optional.of(campaign));
        lenient().when(promotionRepository.findForUpdateById(2L)).thenReturn(Optional.of(promotion));
        lenient().when(repository.save(any(PromotionRedemption.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private MembershipPromotion membershipPromotion() {
        Membership membership = new Membership();
        membership.setId(MEMBERSHIP_ID);
        MembershipPromotion mp = new MembershipPromotion();
        mp.setId(nextId++);
        mp.setMembership(membership);
        mp.setPromotion(promotion);
        mp.setStatus("ACTIVE");
        mp.setAssignedAt(Instant.now());
        return mp;
    }

    private PromotionRedemption reserve(MembershipPromotion mp) {
        PromotionRedemption r = service().reserve(campaign, promotion, mp, Instant.now());
        lenient().when(repository.findByMembershipPromotion_Id(mp.getId())).thenReturn(Optional.of(r));
        return r;
    }

    private Payment payment(long id, boolean inscription) {
        Membership membership = new Membership();
        membership.setId(MEMBERSHIP_ID);
        Payment payment = new Payment();
        payment.setId(id);
        payment.setDirection("IN");
        payment.setInscription(inscription);
        payment.setMembership(membership);
        when(paymentRepository.findById(id)).thenReturn(Optional.of(payment));
        return payment;
    }

    private void pendingOfMembership(PromotionRedemption... rows) {
        when(repository.findByMembershipAndStatus(MEMBERSHIP_ID, RedemptionStatus.PENDING))
                .thenReturn(new ArrayList<>(List.of(rows)));
    }

    // ─── Triggers ───────────────────────────────────────────────────────────

    @Test
    void onEnrollment_confirmsAtOnce_withTheCampaignEndAsUsageEnd() {
        PromotionRedemption r = reserve(membershipPromotion());

        assertThat(r.getRedemptionStatus()).isEqualTo(RedemptionStatus.CONFIRMED);
        assertThat(r.getConfirmedAt()).isNotNull();
        assertThat(r.getUsageEndsAt()).isEqualTo(campaign.getEndsAt().toInstant());
        assertThat(campaign.getRedemptionsCount()).isEqualTo(1);
        assertThat(campaign.getConfirmedCount()).isEqualTo(1);
        assertThat(promotion.getConfirmedCount()).isEqualTo(1);
    }

    @Test
    void onPaymentRegistered_staysPendingWithTtl_untilTheInscriptionPaymentIsRegistered() {
        campaign.setQuotaTrigger(QuotaTrigger.ON_PAYMENT_REGISTERED);
        campaign.setPendingTtlDays(3);
        campaign.setUsageWindow(UsageWindow.DAYS_AFTER_CONFIRMATION);
        campaign.setUsageWindowDays(60);
        PromotionRedemption r = reserve(membershipPromotion());

        assertThat(r.getRedemptionStatus()).isEqualTo(RedemptionStatus.PENDING);
        assertThat(r.getExpiresAt()).isAfter(Instant.now().plus(Duration.ofDays(2)));
        assertThat(campaign.getRedemptionsCount()).isEqualTo(1);
        assertThat(campaign.getConfirmedCount()).isZero();

        Payment inscription = payment(900L, true);
        pendingOfMembership(r);
        assertThat(service().onPaymentRegistered(900L)).isEqualTo(1);

        assertThat(r.getRedemptionStatus()).isEqualTo(RedemptionStatus.CONFIRMED);
        assertThat(r.getConfirmedByPayment()).isSameAs(inscription);
        assertThat(r.getUsageEndsAt()).isAfter(Instant.now().plus(Duration.ofDays(59)));
        assertThat(campaign.getConfirmedCount()).isEqualTo(1);
    }

    @Test
    void onPaymentConfirmed_ignoresTheRegistration_andConfirmsOnApproval() {
        campaign.setQuotaTrigger(QuotaTrigger.ON_PAYMENT_CONFIRMED);
        PromotionRedemption r = reserve(membershipPromotion());
        payment(901L, true);
        pendingOfMembership(r);

        assertThat(service().onPaymentRegistered(901L)).isZero();
        assertThat(r.getRedemptionStatus()).isEqualTo(RedemptionStatus.PENDING);

        assertThat(service().onPaymentApproved(901L)).isEqualTo(1);
        assertThat(r.getRedemptionStatus()).isEqualTo(RedemptionStatus.CONFIRMED);
    }

    @Test
    void recoveryPromotion_isConfirmedByAMonthlyFeePayment_notAnInscription() {
        campaign.setQuotaTrigger(QuotaTrigger.ON_PAYMENT_CONFIRMED);
        promotion.setKind(Kind.RECOVERY);
        PromotionRedemption r = reserve(membershipPromotion());
        payment(902L, true);
        payment(903L, false);
        pendingOfMembership(r);

        assertThat(service().onPaymentApproved(902L)).isZero();
        assertThat(service().onPaymentApproved(903L)).isEqualTo(1);
        assertThat(r.getRedemptionStatus()).isEqualTo(RedemptionStatus.CONFIRMED);
    }

    @Test
    void confirmedCount_reachingTheMax_closesWithQuotaFilled() {
        campaign.setQuotaTrigger(QuotaTrigger.ON_PAYMENT_REGISTERED);
        campaign.setMaxRedemptions(1);
        campaign.setCloseOnQuotaFilled(true);
        PromotionRedemption r = reserve(membershipPromotion());
        assertThat(campaign.getLifecycleStatus()).isEqualTo(LifecycleStatus.SOLD_OUT);

        payment(904L, true);
        pendingOfMembership(r);
        service().onPaymentRegistered(904L);

        assertThat(campaign.getLifecycleStatus()).isEqualTo(LifecycleStatus.CLOSED);
        assertThat(campaign.getClosedReason()).isEqualTo(Campaign.CloseReason.QUOTA_FILLED);
    }

    // ─── Expiry / release ───────────────────────────────────────────────────

    @Test
    void pendingExpiry_freesTheSlot_soldOutBackToActive_andCancelsThePromotion() {
        campaign.setQuotaTrigger(QuotaTrigger.ON_PAYMENT_CONFIRMED);
        campaign.setPendingTtlDays(1);
        campaign.setMaxRedemptions(1);
        MembershipPromotion mp = membershipPromotion();
        PromotionRedemption r = reserve(mp);
        assertThat(campaign.getLifecycleStatus()).isEqualTo(LifecycleStatus.SOLD_OUT);
        r.setExpiresAt(Instant.now().minusSeconds(60));
        when(repository.findPendingExpiryCandidates(any(Instant.class))).thenReturn(List.of(r));

        assertThat(service().expirePending(Instant.now())).isEqualTo(1);

        assertThat(r.getRedemptionStatus()).isEqualTo(RedemptionStatus.EXPIRED);
        assertThat(r.getReleasedAt()).isNotNull();
        assertThat(campaign.getRedemptionsCount()).isZero();
        assertThat(promotion.getRedemptionsCount()).isZero();
        assertThat(campaign.getLifecycleStatus()).isEqualTo(LifecycleStatus.ACTIVE);
        assertThat(mp.getStatus()).isEqualTo("CANCELED");
        assertThat(mp.getEndedReason()).isEqualTo(PromotionRedemptionService.REASON_QUOTA_EXPIRED);
    }

    @Test
    void release_beforeAnyDiscount_freesPendingAndUnusedConfirmedSlots_butKeepsUsedOnes() {
        campaign.setQuotaTrigger(QuotaTrigger.ON_PAYMENT_CONFIRMED);
        MembershipPromotion pendingMp = membershipPromotion();
        PromotionRedemption pending = reserve(pendingMp);
        assertThat(service().releaseIfUnused(pendingMp)).isTrue();
        assertThat(pending.getRedemptionStatus()).isEqualTo(RedemptionStatus.RELEASED);

        campaign.setQuotaTrigger(QuotaTrigger.ON_ENROLLMENT);
        MembershipPromotion unusedMp = membershipPromotion();
        PromotionRedemption unused = reserve(unusedMp);
        when(repository.anyDiscountedCharge(unusedMp.getId())).thenReturn(false);
        assertThat(service().releaseIfUnused(unusedMp)).isTrue();
        assertThat(unused.getRedemptionStatus()).isEqualTo(RedemptionStatus.RELEASED);

        MembershipPromotion usedMp = membershipPromotion();
        usedMp.setInscriptionApplied(true);
        PromotionRedemption used = reserve(usedMp);
        assertThat(service().releaseIfUnused(usedMp)).isFalse();
        assertThat(used.getRedemptionStatus()).isEqualTo(RedemptionStatus.CONFIRMED);

        assertThat(campaign.getRedemptionsCount()).isEqualTo(1);
        assertThat(campaign.getConfirmedCount()).isEqualTo(1);
    }

    @Test
    void graceAfterClose_cutsThePendingDeadline_andStillAllowsConfirmationInsideIt() {
        campaign.setQuotaTrigger(QuotaTrigger.ON_PAYMENT_REGISTERED);
        campaign.setConfirmGraceDays(2);
        MembershipPromotion insideMp = membershipPromotion();
        PromotionRedemption inside = reserve(insideMp);

        // Closed yesterday with 2 days of grace: confirmation still allowed.
        campaign.close(Campaign.CloseReason.MANUAL, null, null, OffsetDateTime.now().minusDays(1));
        payment(905L, true);
        pendingOfMembership(inside);
        assertThat(service().onPaymentRegistered(905L)).isEqualTo(1);
        assertThat(inside.getRedemptionStatus()).isEqualTo(RedemptionStatus.CONFIRMED);

        // Closed 3 days ago: the grace is over, the job expires the slot even without its own deadline.
        campaign.setLifecycleStatus(LifecycleStatus.ACTIVE);
        campaign.clearClose();
        MembershipPromotion lateMp = membershipPromotion();
        PromotionRedemption late = reserve(lateMp);
        assertThat(late.getExpiresAt()).isNull();
        campaign.close(Campaign.CloseReason.MANUAL, null, null, OffsetDateTime.now().minusDays(3));
        when(repository.findPendingExpiryCandidates(any(Instant.class))).thenReturn(List.of(late));

        assertThat(service().expirePending(Instant.now())).isEqualTo(1);
        assertThat(late.getRedemptionStatus()).isEqualTo(RedemptionStatus.EXPIRED);
    }

    @Test
    void cancelPending_releasesEveryPendingSlot_andCancelsTheirPromotions() {
        campaign.setQuotaTrigger(QuotaTrigger.ON_PAYMENT_CONFIRMED);
        MembershipPromotion mp = membershipPromotion();
        PromotionRedemption r = reserve(mp);
        when(repository.findByCampaignAndRedemptionStatus(campaign, RedemptionStatus.PENDING)).thenReturn(List.of(r));

        assertThat(service().cancelPending(campaign)).isEqualTo(1);

        assertThat(r.getRedemptionStatus()).isEqualTo(RedemptionStatus.RELEASED);
        assertThat(mp.getStatus()).isEqualTo("CANCELED");
        assertThat(mp.getEndedReason()).isEqualTo(PromotionRedemptionService.REASON_CAMPAIGN_CLOSED);
        assertThat(campaign.getRedemptionsCount()).isZero();
    }

    @Test
    void usageWindowEnd_consumesThePromotion_andKeepsTheSlotConfirmed() {
        MembershipPromotion mp = membershipPromotion();
        PromotionRedemption r = reserve(mp);
        when(repository.findConfirmedWithUsageEnded(any(Instant.class))).thenReturn(List.of(r));

        assertThat(service().endUsageWindows(Instant.now())).isEqualTo(1);

        assertThat(mp.getStatus()).isEqualTo("CONSUMED");
        assertThat(mp.getEndedReason()).isEqualTo(PromotionRedemptionService.REASON_USAGE_WINDOW_ENDED);
        assertThat(r.getRedemptionStatus()).isEqualTo(RedemptionStatus.CONFIRMED);
    }

    // ─── Quota report ───────────────────────────────────────────────────────

    @Test
    void quota_reportsCampaignAndPromotionCounters() {
        campaign.setMaxRedemptions(10);
        campaign.setRedemptionsCount(4);
        campaign.setConfirmedCount(3);
        promotion.setOwnQuota(true);
        promotion.setMaxRedemptions(5);
        promotion.setRedemptionsCount(4);
        promotion.setConfirmedCount(3);
        when(campaignRepository.findByUuid(campaign.getUuid())).thenReturn(Optional.of(campaign));
        when(promotionRepository.findByCampaignAndActiveTrueOrderByCreatedAtAsc(campaign)).thenReturn(List.of(promotion));
        when(repository.countByPromotionAndStatus(eq(campaign))).thenReturn(List.of(
                new Object[]{2L, RedemptionStatus.EXPIRED, 2L},
                new Object[]{2L, RedemptionStatus.RELEASED, 1L}));

        CampaignQuotaDto quota = service().quota(campaign.getUuid());

        assertThat(quota.maxRedemptions()).isEqualTo(10);
        assertThat(quota.confirmed()).isEqualTo(3);
        assertThat(quota.pending()).isEqualTo(1);
        assertThat(quota.free()).isEqualTo(6);
        assertThat(quota.expired()).isEqualTo(2);
        assertThat(quota.released()).isEqualTo(1);
        assertThat(quota.promotions()).hasSize(1);
        CampaignQuotaDto.PromotionQuota p = quota.promotions().get(0);
        assertThat(p.free()).isEqualTo(1);
        assertThat(p.pending()).isEqualTo(1);
        assertThat(p.expired()).isEqualTo(2);
    }

    // ─── Ally benefits (V257) ───────────────────────────────────────────────

    @Test
    void confirmingAnAllyBenefitSlot_grantsTheVoucher_aDiscountDoesNot() {
        PromotionRedemption discount = reserve(membershipPromotion());
        verify(grantService, never()).grantFor(discount);

        promotion.setBenefitKind(Promotion.BenefitKind.ALLY_BENEFIT);
        PromotionRedemption allyBenefit = reserve(membershipPromotion());
        verify(grantService).grantFor(allyBenefit);
    }

    @Test
    void releasingAnAllyBenefit_dependsOnTheVoucherUse() {
        promotion.setBenefitKind(Promotion.BenefitKind.ALLY_BENEFIT);
        MembershipPromotion unusedMp = membershipPromotion();
        unusedMp.setBenefitKind(Promotion.BenefitKind.ALLY_BENEFIT);
        PromotionRedemption unused = reserve(unusedMp);
        when(grantService.anyRedeemed(unusedMp)).thenReturn(false);
        assertThat(service().releaseIfUnused(unusedMp)).isTrue();
        assertThat(unused.getRedemptionStatus()).isEqualTo(RedemptionStatus.RELEASED);
        verify(grantService).cancelFor(unusedMp);

        MembershipPromotion usedMp = membershipPromotion();
        usedMp.setBenefitKind(Promotion.BenefitKind.ALLY_BENEFIT);
        PromotionRedemption used = reserve(usedMp);
        when(grantService.anyRedeemed(usedMp)).thenReturn(true);
        assertThat(service().releaseIfUnused(usedMp)).isFalse();
        assertThat(used.getRedemptionStatus()).isEqualTo(RedemptionStatus.CONFIRMED);
        verify(grantService).cancelFor(usedMp);
    }
}
