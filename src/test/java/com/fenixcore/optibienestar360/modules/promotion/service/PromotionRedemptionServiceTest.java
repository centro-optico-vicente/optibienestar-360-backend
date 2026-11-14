package com.fenixcore.optibienestar360.modules.promotion.service;

import com.fenixcore.optibienestar360.modules.campaign.dto.CampaignQuotaDto;
import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign.LifecycleStatus;
import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign.QuotaTrigger;
import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign.UsageWindow;
import com.fenixcore.optibienestar360.modules.campaign.repository.CampaignRepository;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.service.MembershipChargeService;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentRepository;
import com.fenixcore.optibienestar360.modules.promotion.entity.MembershipPromotion;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.Kind;
import com.fenixcore.optibienestar360.modules.promotion.entity.PromotionRedemption;
import com.fenixcore.optibienestar360.modules.promotion.entity.PromotionRedemption.RedemptionStatus;
import com.fenixcore.optibienestar360.modules.promotion.repository.PromotionRedemptionRepository;
import com.fenixcore.optibienestar360.modules.promotion.repository.PromotionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

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
import static org.mockito.Mockito.doAnswer;
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
    @Mock private EntityManager entityManager;
    @Mock private MembershipChargeService chargeService;
    @Mock private ObjectProvider<MembershipChargeService> chargeServiceProvider;
    @Mock private PromotionSlotStatusService slotStatus;
    @Mock private PromotionSlotNoticeService noticeService;

    private static final long MEMBERSHIP_ID = 10L;

    private Campaign campaign;
    private Promotion promotion;
    private long nextId = 500;
    private PromotionRedemption lastSaved;

    private PromotionRedemptionService service() {
        return new PromotionRedemptionService(repository, campaignRepository, promotionRepository, paymentRepository,
                grantService, entityManager, chargeServiceProvider, slotStatus, noticeService);
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
        lenient().when(chargeServiceProvider.getObject()).thenReturn(chargeService);
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

    // ─── Payment already there when the slot is reserved ─────────────────────

    private static Payment collection(long id, boolean inscription, Instant registeredAt) {
        Payment payment = new Payment();
        payment.setId(id);
        payment.setDirection("IN");
        payment.setInscription(inscription);
        payment.setCreatedAt(registeredAt);
        return payment;
    }

    @Test
    void reserve_afterTheInscriptionWasRegistered_confirmsAtOnceWithThatPayment() {
        campaign.setQuotaTrigger(QuotaTrigger.ON_PAYMENT_REGISTERED);
        campaign.setPendingTtlDays(3);
        Payment inscription = collection(910L, true, Instant.now().minus(Duration.ofDays(1)));
        when(paymentRepository.findCollectionsOfMembership(MEMBERSHIP_ID, true, List.of("PENDING", "APPROVED")))
                .thenReturn(List.of(inscription));

        PromotionRedemption r = reserve(membershipPromotion());

        assertThat(r.getRedemptionStatus()).isEqualTo(RedemptionStatus.CONFIRMED);
        assertThat(r.getConfirmedByPayment()).isSameAs(inscription);
        assertThat(campaign.getRedemptionsCount()).isEqualTo(1);
        assertThat(campaign.getConfirmedCount()).isEqualTo(1);
        assertThat(promotion.getConfirmedCount()).isEqualTo(1);
    }

    @Test
    void reserve_onPaymentConfirmed_onlyAnApprovedPaymentConfirmsAtOnce() {
        campaign.setQuotaTrigger(QuotaTrigger.ON_PAYMENT_CONFIRMED);
        when(paymentRepository.findCollectionsOfMembership(MEMBERSHIP_ID, true, List.of("APPROVED")))
                .thenReturn(List.of());

        PromotionRedemption r = reserve(membershipPromotion());

        assertThat(r.getRedemptionStatus()).isEqualTo(RedemptionStatus.PENDING);
        assertThat(campaign.getConfirmedCount()).isZero();
    }

    @Test
    void reserve_recovery_onlyAMonthlyFeeRegisteredAfterTheDelayConfirmsAtOnce() {
        campaign.setQuotaTrigger(QuotaTrigger.ON_PAYMENT_REGISTERED);
        promotion.setKind(Kind.RECOVERY);
        Instant overdueSince = Instant.now().minus(Duration.ofDays(10));

        MembershipPromotion beforeDelay = membershipPromotion();
        beforeDelay.getMembership().setLastStatusChangeAt(overdueSince);
        when(paymentRepository.findCollectionsOfMembership(MEMBERSHIP_ID, false, List.of("PENDING", "APPROVED")))
                .thenReturn(List.of(collection(911L, false, overdueSince.minus(Duration.ofDays(20)))));
        assertThat(reserve(beforeDelay).getRedemptionStatus()).isEqualTo(RedemptionStatus.PENDING);

        MembershipPromotion afterDelay = membershipPromotion();
        afterDelay.getMembership().setLastStatusChangeAt(overdueSince);
        Payment monthly = collection(912L, false, Instant.now().minus(Duration.ofDays(1)));
        when(paymentRepository.findCollectionsOfMembership(MEMBERSHIP_ID, false, List.of("PENDING", "APPROVED")))
                .thenReturn(List.of(monthly));
        PromotionRedemption confirmed = reserve(afterDelay);
        assertThat(confirmed.getRedemptionStatus()).isEqualTo(RedemptionStatus.CONFIRMED);
        assertThat(confirmed.getConfirmedByPayment()).isSameAs(monthly);
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

    @Test
    void confirmation_reReadsTheSlotUnderLock_andSkipsOneTheJobJustExpired() {
        campaign.setQuotaTrigger(QuotaTrigger.ON_PAYMENT_REGISTERED);
        PromotionRedemption r = reserve(membershipPromotion());
        payment(906L, true);
        pendingOfMembership(r);
        // The expiry job committed first: the locked re-read sees the slot EXPIRED.
        doAnswer(inv -> {
            r.setRedemptionStatus(RedemptionStatus.EXPIRED);
            return null;
        }).when(entityManager).refresh(r, LockModeType.PESSIMISTIC_WRITE);

        assertThat(service().onPaymentRegistered(906L)).isZero();

        assertThat(r.getRedemptionStatus()).isEqualTo(RedemptionStatus.EXPIRED);
        assertThat(campaign.getConfirmedCount()).isZero();
        assertThat(r.getConfirmedByPayment()).isNull();
    }

    @Test
    void expiry_reReadsTheSlotUnderLock_andSkipsOneJustConfirmed() {
        campaign.setQuotaTrigger(QuotaTrigger.ON_PAYMENT_REGISTERED);
        campaign.setPendingTtlDays(1);
        PromotionRedemption r = reserve(membershipPromotion());
        r.setExpiresAt(Instant.now().minusSeconds(60));
        when(repository.findPendingExpiryCandidates(any(Instant.class))).thenReturn(List.of(r));
        doAnswer(inv -> {
            r.setRedemptionStatus(RedemptionStatus.CONFIRMED);
            return null;
        }).when(entityManager).refresh(r, LockModeType.PESSIMISTIC_WRITE);

        assertThat(service().expirePending(Instant.now())).isZero();

        assertThat(r.getRedemptionStatus()).isEqualTo(RedemptionStatus.CONFIRMED);
        assertThat(campaign.getRedemptionsCount()).isEqualTo(1);
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

    private static Payment discountedInscription(String status, String reason) {
        Payment payment = new Payment();
        payment.setDirection("IN");
        payment.setInscription(true);
        payment.setStatus(status);
        payment.setAmount(new java.math.BigDecimal("20.00"));
        payment.setDiscountAmount(new java.math.BigDecimal("10.00"));
        payment.setDiscountReason(reason);
        payment.setDiscountedAt(Instant.now());
        return payment;
    }

    @Test
    void pendingExpiry_undoesTheDiscountOnWhatIsStillOpen() {
        campaign.setQuotaTrigger(QuotaTrigger.ON_PAYMENT_CONFIRMED);
        campaign.setPendingTtlDays(1);
        MembershipPromotion mp = membershipPromotion();
        mp.setInscriptionApplied(true);
        PromotionRedemption r = reserve(mp);
        r.setExpiresAt(Instant.now().minusSeconds(60));
        when(repository.findPendingExpiryCandidates(any(Instant.class))).thenReturn(List.of(r));
        Payment underReview = discountedInscription("PENDING", "Promoción: Navidad (50%)");
        Payment typedByHand = discountedInscription("PENDING", "Descuento especial");
        when(paymentRepository.findCollectionsOfMembership(MEMBERSHIP_ID, true, List.of("DRAFT", "PENDING")))
                .thenReturn(List.of(underReview, typedByHand));

        service().expirePending(Instant.now());

        // The system discount of the lost promotion goes; a hand-made one stays.
        assertThat(underReview.getDiscountAmount()).isNull();
        assertThat(underReview.getDiscountReason()).isNull();
        assertThat(underReview.getDiscountedAt()).isNull();
        assertThat(typedByHand.getDiscountAmount()).isEqualByComparingTo("10.00");
        assertThat(mp.isInscriptionApplied()).isFalse();
        // Open monthly charges are re-priced once the promotion has ended.
        assertThat(mp.getStatus()).isEqualTo("CANCELED");
        verify(chargeService).repriceOpenCharges(mp.getMembership());
    }

    @Test
    void approvedPayments_keepTheirDiscount() {
        Payment approved = discountedInscription("APPROVED", "Promoción: Navidad (50%)");

        assertThat(PromotionPricing.clearPromotionDiscount(approved, promotion)).isFalse();
        assertThat(approved.getDiscountAmount()).isEqualByComparingTo("10.00");
    }

    @Test
    void cancelPending_andRelease_alsoRepriceTheOpenCharges_butAnAllyVoucherDoesNot() {
        campaign.setQuotaTrigger(QuotaTrigger.ON_PAYMENT_CONFIRMED);
        MembershipPromotion closedMp = membershipPromotion();
        PromotionRedemption closedSlot = reserve(closedMp);
        when(repository.findByCampaignAndRedemptionStatus(campaign, RedemptionStatus.PENDING)).thenReturn(List.of(closedSlot));
        service().cancelPending(campaign);
        verify(chargeService).repriceOpenCharges(closedMp.getMembership());

        MembershipPromotion releasedMp = membershipPromotion();
        reserve(releasedMp);
        assertThat(service().releaseIfUnused(releasedMp)).isTrue();
        verify(chargeService).repriceOpenCharges(releasedMp.getMembership());

        promotion.setBenefitKind(Promotion.BenefitKind.ALLY_BENEFIT);
        MembershipPromotion voucherMp = membershipPromotion();
        voucherMp.setBenefitKind(Promotion.BenefitKind.ALLY_BENEFIT);
        reserve(voucherMp);
        assertThat(service().releaseIfUnused(voucherMp)).isTrue();
        verify(chargeService, never()).repriceOpenCharges(voucherMp.getMembership());
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
    void closedWithoutGrace_aPendingSlotWithoutOwnDeadline_endsWithTheClose() {
        campaign.setQuotaTrigger(QuotaTrigger.ON_PAYMENT_REGISTERED);
        MembershipPromotion mp = membershipPromotion();
        PromotionRedemption r = reserve(mp);
        assertThat(r.getExpiresAt()).isNull();
        OffsetDateTime closedAt = OffsetDateTime.now().minusMinutes(5);
        campaign.close(Campaign.CloseReason.MANUAL, null, null, closedAt);
        assertThat(campaign.pendingDeadline(null)).isEqualTo(closedAt.toInstant());

        // A payment after the close no longer confirms it; the job expires it.
        payment(907L, true);
        pendingOfMembership(r);
        assertThat(service().onPaymentRegistered(907L)).isZero();
        when(repository.findPendingExpiryCandidates(any(Instant.class))).thenReturn(List.of(r));
        assertThat(service().expirePending(Instant.now())).isEqualTo(1);
        assertThat(r.getRedemptionStatus()).isEqualTo(RedemptionStatus.EXPIRED);
        assertThat(mp.getEndedReason()).isEqualTo(PromotionRedemptionService.REASON_QUOTA_EXPIRED);
    }

    @Test
    void closedWithoutGrace_aSlotWithItsOwnDeadline_keepsIt() {
        Instant own = Instant.now().plus(Duration.ofDays(2));
        campaign.close(Campaign.CloseReason.MANUAL, null, null, OffsetDateTime.now().minusDays(1));

        assertThat(campaign.pendingDeadline(own)).isEqualTo(own);
    }

    @Test
    void campaignClose_endsOpenEndedUsageWindows_andVouchersWithoutExpiry() {
        campaign.setEndsAt(null);
        PromotionRedemption openEnded = reserve(membershipPromotion());
        assertThat(openEnded.getUsageEndsAt()).isNull();
        PromotionRedemption bounded = reserve(membershipPromotion());
        Instant boundedEnd = Instant.now().plus(Duration.ofDays(3));
        bounded.setUsageEndsAt(boundedEnd);
        OffsetDateTime closedAt = OffsetDateTime.now();
        campaign.close(Campaign.CloseReason.MANUAL, null, null, closedAt);
        when(repository.findByCampaignAndRedemptionStatus(campaign, RedemptionStatus.CONFIRMED))
                .thenReturn(List.of(openEnded, bounded));

        assertThat(service().onCampaignClosed(campaign)).isEqualTo(1);

        assertThat(openEnded.getUsageEndsAt()).isEqualTo(closedAt.toInstant());
        assertThat(bounded.getUsageEndsAt()).isEqualTo(boundedEnd);
        verify(grantService).expireAtClose(campaign, closedAt.toInstant());
    }

    @Test
    void campaignClose_leavesDaysAfterConfirmationWindowsAlone() {
        campaign.setUsageWindow(UsageWindow.DAYS_AFTER_CONFIRMATION);
        campaign.setUsageWindowDays(30);
        campaign.close(Campaign.CloseReason.MANUAL, null, null, OffsetDateTime.now());

        assertThat(service().onCampaignClosed(campaign)).isZero();
        verify(grantService, never()).expireAtClose(any(), any());
    }

    @Test
    void quotaFilledClose_endsTheUsageOfAnOpenEndedCampaign_includingTheSlotThatFilledIt() {
        campaign.setEndsAt(null);
        campaign.setMaxRedemptions(1);
        campaign.setCloseOnQuotaFilled(true);
        // The slot is saved before the state is re-derived, so the close finds it.
        when(repository.findByCampaignAndRedemptionStatus(eq(campaign), eq(RedemptionStatus.CONFIRMED)))
                .thenAnswer(inv -> List.of(lastSaved));
        when(repository.save(any(PromotionRedemption.class))).thenAnswer(inv -> lastSaved = inv.getArgument(0));

        PromotionRedemption r = reserve(membershipPromotion());

        assertThat(campaign.getClosedReason()).isEqualTo(Campaign.CloseReason.QUOTA_FILLED);
        assertThat(r.getUsageEndsAt()).isEqualTo(campaign.getClosedAt().toInstant());
    }

    @Test
    void confirmingDuringTheGrace_ofAClosedOpenEndedCampaign_usesTheCloseAsUsageEnd() {
        campaign.setEndsAt(null);
        campaign.setQuotaTrigger(QuotaTrigger.ON_PAYMENT_REGISTERED);
        campaign.setConfirmGraceDays(3);
        PromotionRedemption r = reserve(membershipPromotion());
        campaign.close(Campaign.CloseReason.MANUAL, null, null, OffsetDateTime.now().minusDays(1));
        payment(908L, true);
        pendingOfMembership(r);

        assertThat(service().onPaymentRegistered(908L)).isEqualTo(1);
        assertThat(r.getUsageEndsAt()).isEqualTo(campaign.getClosedAt().toInstant());
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

    // ─── Reconciliation with the payments (V260, hub ADR 0038 §9) ────────────

    private PromotionRedemption pendingPastDeadline(MembershipPromotion mp) {
        campaign.setQuotaTrigger(QuotaTrigger.ON_PAYMENT_CONFIRMED);
        campaign.setPendingTtlDays(1);
        PromotionRedemption r = reserve(mp);
        r.setExpiresAt(Instant.now().minusSeconds(60));
        return r;
    }

    @Test
    void lateApproval_ofAPaymentRegisteredBeforeTheDeadline_confirmsTheFrozenSlot() {
        PromotionRedemption r = pendingPastDeadline(membershipPromotion());
        Payment inscription = payment(920L, true);
        inscription.setCreatedAt(Instant.now().minus(Duration.ofDays(2)));
        pendingOfMembership(r);

        assertThat(service().onPaymentApproved(920L)).isEqualTo(1);

        assertThat(r.getRedemptionStatus()).isEqualTo(RedemptionStatus.CONFIRMED);
        assertThat(r.getConfirmedByPayment()).isSameAs(inscription);
        assertThat(campaign.getConfirmedCount()).isEqualTo(1);
    }

    @Test
    void lateApproval_ofAPaymentRegisteredAfterTheDeadline_leavesTheSlotForTheJob() {
        PromotionRedemption r = pendingPastDeadline(membershipPromotion());
        Payment inscription = payment(921L, true);
        inscription.setCreatedAt(Instant.now().minusSeconds(1));
        pendingOfMembership(r);

        assertThat(service().onPaymentApproved(921L)).isZero();
        assertThat(r.getRedemptionStatus()).isEqualTo(RedemptionStatus.PENDING);
    }

    @Test
    void rejection_afterTheDeadline_expiresTheSlot_andUndoesTheOpenDiscount() {
        MembershipPromotion mp = membershipPromotion();
        mp.setInscriptionApplied(true);
        PromotionRedemption r = pendingPastDeadline(mp);
        payment(930L, true);
        pendingOfMembership(r);
        Payment otherInReview = discountedInscription("PENDING", "Promoción: Navidad (50%)");
        when(paymentRepository.findCollectionsOfMembership(MEMBERSHIP_ID, true, List.of("DRAFT", "PENDING")))
                .thenReturn(List.of(otherInReview));

        assertThat(service().onPaymentRejected(930L)).isEqualTo(1);

        assertThat(r.getRedemptionStatus()).isEqualTo(RedemptionStatus.EXPIRED);
        assertThat(campaign.getRedemptionsCount()).isZero();
        assertThat(mp.getStatus()).isEqualTo("CANCELED");
        assertThat(mp.getEndedReason()).isEqualTo(PromotionRedemptionService.REASON_QUOTA_EXPIRED);
        assertThat(otherInReview.getDiscountAmount()).isNull();
        assertThat(mp.isInscriptionApplied()).isFalse();
        verify(noticeService).notifyExpired(r);
    }

    @Test
    void rejection_beforeTheDeadline_letsTheDeadlineRunAgain() {
        campaign.setQuotaTrigger(QuotaTrigger.ON_PAYMENT_CONFIRMED);
        campaign.setPendingTtlDays(3);
        PromotionRedemption r = reserve(membershipPromotion());
        payment(931L, true);
        pendingOfMembership(r);

        assertThat(service().onPaymentRejected(931L)).isZero();
        assertThat(r.getRedemptionStatus()).isEqualTo(RedemptionStatus.PENDING);
    }

    @Test
    void rejection_withAnotherQualifyingPaymentInReview_keepsTheSlotFrozen() {
        PromotionRedemption r = pendingPastDeadline(membershipPromotion());
        payment(932L, true);
        pendingOfMembership(r);
        when(slotStatus.isFrozen(r)).thenReturn(true);

        assertThat(service().onPaymentRejected(932L)).isZero();
        assertThat(r.getRedemptionStatus()).isEqualTo(RedemptionStatus.PENDING);
    }

    @Test
    void reconcile_confirmsAPendingSlotThatAlreadyHasItsPayment() {
        campaign.setQuotaTrigger(QuotaTrigger.ON_PAYMENT_REGISTERED);
        PromotionRedemption r = reserve(membershipPromotion());
        Payment registered = collection(940L, true, Instant.now().minus(Duration.ofHours(2)));
        when(repository.findAllInStatus(RedemptionStatus.PENDING)).thenReturn(List.of(r));
        when(slotStatus.confirmingPayment(r)).thenReturn(registered);

        assertThat(service().reconcilePending(Instant.now())).isEqualTo(1);

        assertThat(r.getRedemptionStatus()).isEqualTo(RedemptionStatus.CONFIRMED);
        assertThat(r.getConfirmedByPayment()).isSameAs(registered);
        assertThat(campaign.getConfirmedCount()).isEqualTo(1);
    }

    @Test
    void expiry_confirmsInsteadWhenThePaymentIsThere_andSkipsAFrozenSlot() {
        PromotionRedemption paid = pendingPastDeadline(membershipPromotion());
        PromotionRedemption frozen = pendingPastDeadline(membershipPromotion());
        Payment approved = collection(950L, true, Instant.now().minus(Duration.ofDays(2)));
        when(repository.findPendingExpiryCandidates(any(Instant.class))).thenReturn(List.of(paid, frozen));
        when(slotStatus.confirmingPayment(paid)).thenReturn(approved);
        when(slotStatus.isFrozen(frozen)).thenReturn(true);

        assertThat(service().expirePending(Instant.now())).isZero();

        assertThat(paid.getRedemptionStatus()).isEqualTo(RedemptionStatus.CONFIRMED);
        assertThat(paid.getConfirmedByPayment()).isSameAs(approved);
        assertThat(frozen.getRedemptionStatus()).isEqualTo(RedemptionStatus.PENDING);
        verify(noticeService, never()).notifyExpired(any());
    }

    @Test
    void expiryNotice_isSentOnce_andNeverForAFrozenSlot() {
        campaign.setQuotaTrigger(QuotaTrigger.ON_PAYMENT_CONFIRMED);
        PromotionRedemption soon = reserve(membershipPromotion());
        soon.setExpiresAt(Instant.now().plus(Duration.ofHours(20)));
        PromotionRedemption frozen = reserve(membershipPromotion());
        frozen.setExpiresAt(Instant.now().plus(Duration.ofHours(20)));
        when(repository.findExpiryNoticeCandidates(any(Instant.class))).thenReturn(List.of(frozen, soon));
        when(slotStatus.isFrozen(frozen)).thenReturn(true);

        assertThat(service().sendExpiryNotices(Instant.now())).isEqualTo(1);
        assertThat(soon.getExpiryNoticeSentAt()).isNotNull();
        assertThat(frozen.getExpiryNoticeSentAt()).isNull();

        assertThat(service().sendExpiryNotices(Instant.now())).isZero();
        verify(noticeService, org.mockito.Mockito.times(1)).notifyExpiring(eq(soon), any(Instant.class));
        verify(noticeService, never()).notifyExpiring(eq(frozen), any(Instant.class));
    }

    @Test
    void manualConfirm_confirmsAPendingSlotWithTheNote_andRejectsOtherStatesOrABlankNote() {
        campaign.setQuotaTrigger(QuotaTrigger.ON_PAYMENT_CONFIRMED);
        PromotionRedemption r = reserve(membershipPromotion());
        com.fenixcore.optibienestar360.modules.auth.entity.User admin = new com.fenixcore.optibienestar360.modules.auth.entity.User();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service().confirmManually(r, admin, "  "))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("promotion_slot.note.required");

        service().confirmManually(r, admin, " Pago verificado en banco ");
        assertThat(r.getRedemptionStatus()).isEqualTo(RedemptionStatus.CONFIRMED);
        assertThat(r.getManualNote()).isEqualTo("Pago verificado en banco");
        assertThat(r.getManualBy()).isSameAs(admin);
        assertThat(r.getConfirmedByPayment()).isNull();
        assertThat(campaign.getConfirmedCount()).isEqualTo(1);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service().confirmManually(r, admin, "Otra vez"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("promotion_slot.confirm.not_pending");
    }

    @Test
    void manualRelease_freesAPendingSlot_butNotAConfirmedOneAlreadyUsed() {
        campaign.setQuotaTrigger(QuotaTrigger.ON_PAYMENT_CONFIRMED);
        MembershipPromotion pendingMp = membershipPromotion();
        PromotionRedemption pending = reserve(pendingMp);
        com.fenixcore.optibienestar360.modules.auth.entity.User admin = new com.fenixcore.optibienestar360.modules.auth.entity.User();

        service().releaseManually(pending, admin, "El afiliado desistió");
        assertThat(pending.getRedemptionStatus()).isEqualTo(RedemptionStatus.RELEASED);
        assertThat(pending.getManualNote()).isEqualTo("El afiliado desistió");
        assertThat(pendingMp.getStatus()).isEqualTo("CANCELED");
        assertThat(pendingMp.getEndedReason()).isEqualTo(PromotionRedemptionService.REASON_SLOT_RELEASED);
        assertThat(campaign.getRedemptionsCount()).isZero();

        campaign.setQuotaTrigger(QuotaTrigger.ON_ENROLLMENT);
        MembershipPromotion usedMp = membershipPromotion();
        usedMp.setInscriptionApplied(true);
        PromotionRedemption used = reserve(usedMp);
        assertThat(used.getRedemptionStatus()).isEqualTo(RedemptionStatus.CONFIRMED);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service().releaseManually(used, admin, "Liberar"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("promotion_slot.release.not_allowed");
        assertThat(used.getRedemptionStatus()).isEqualTo(RedemptionStatus.CONFIRMED);
    }
}
