package com.fenixcore.optibienestar360.modules.promotion.service;

import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.campaign.dto.CampaignPaymentRemindResultDto;
import com.fenixcore.optibienestar360.modules.campaign.dto.CampaignPaymentsDto;
import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign.QuotaTrigger;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Campaign "Pagos" section and its reminders (V260, hub ADR 0038 §9). */
@ExtendWith(MockitoExtension.class)
class CampaignSlotServiceTest {

    private static final long MEMBERSHIP_ID = 10L;

    @Mock private CampaignRepository campaignRepository;
    @Mock private PromotionRedemptionRepository repository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private UserRepository userRepository;
    @Mock private PromotionRedemptionService redemptionService;
    @Mock private PromotionSlotStatusService slotStatus;
    @Mock private PromotionSlotNoticeService noticeService;
    @Mock private PromotionSlotAuditRecorder auditRecorder;

    private Campaign campaign;
    private Promotion promotion;
    private Membership membership;

    private CampaignSlotService service() {
        return new CampaignSlotService(campaignRepository, repository, paymentRepository, userRepository,
                redemptionService, slotStatus, noticeService, auditRecorder);
    }

    @BeforeEach
    void setUp() {
        campaign = new Campaign();
        campaign.setId(1L);
        campaign.setUuid(UUID.randomUUID());
        campaign.setName("Navidad");
        campaign.setQuotaTrigger(QuotaTrigger.ON_PAYMENT_CONFIRMED);
        promotion = new Promotion();
        promotion.setId(2L);
        promotion.setUuid(UUID.randomUUID());
        promotion.setName("Navidad");
        promotion.setKind(Kind.ACQUISITION);
        promotion.setCampaign(campaign);
        membership = new Membership();
        membership.setId(MEMBERSHIP_ID);
        membership.setUuid(UUID.randomUUID());
        when(campaignRepository.findByUuid(campaign.getUuid())).thenReturn(Optional.of(campaign));
    }

    private PromotionRedemption slot(RedemptionStatus status) {
        MembershipPromotion mp = new MembershipPromotion();
        mp.setId(50L);
        mp.setMembership(membership);
        mp.setPromotion(promotion);
        PromotionRedemption r = new PromotionRedemption();
        r.setUuid(UUID.randomUUID());
        r.setMembershipPromotion(mp);
        r.setPromotion(promotion);
        r.setCampaign(campaign);
        r.setRedemptionStatus(status);
        r.setReservedAt(Instant.now().minus(Duration.ofDays(1)));
        return r;
    }

    private Payment inReview() {
        Payment p = new Payment();
        p.setId(900L);
        p.setUuid(UUID.randomUUID());
        p.setDirection("IN");
        p.setInscription(true);
        p.setStatus("PENDING");
        p.setCreatedAt(Instant.now().minus(Duration.ofHours(3)));
        p.setMembership(membership);
        return p;
    }

    @Test
    void remind_withinTheHour_is422TooSoon_andSendsNothing() {
        Payment payment = inReview();
        PromotionRedemption r = slot(RedemptionStatus.PENDING);
        r.setLastRemindedAt(Instant.now().minus(Duration.ofMinutes(10)));
        when(paymentRepository.findByUuid(payment.getUuid())).thenReturn(Optional.of(payment));
        when(repository.findByMembershipAndStatus(MEMBERSHIP_ID, RedemptionStatus.PENDING)).thenReturn(List.of(r));

        assertThatThrownBy(() -> service().remind(campaign.getUuid(), payment.getUuid(), null, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("campaign_payment.remind.too_soon");
        verify(noticeService, never()).remindPayment(any(), any(), any(), any(), any());
    }

    @Test
    void remind_afterTheHour_emailsTheAdvisorAndApprovers_storesTheReminder_andAudits() {
        Payment payment = inReview();
        PromotionRedemption r = slot(RedemptionStatus.PENDING);
        r.setLastRemindedAt(Instant.now().minus(Duration.ofHours(2)));
        when(paymentRepository.findByUuid(payment.getUuid())).thenReturn(Optional.of(payment));
        when(repository.findByMembershipAndStatus(MEMBERSHIP_ID, RedemptionStatus.PENDING)).thenReturn(List.of(r));
        when(userRepository.findActiveEmailsWithPermission(CampaignSlotService.PAYMENT_APPROVER_PERMISSION))
                .thenReturn(List.of("finanzas@example.com"));

        service().remind(campaign.getUuid(), payment.getUuid(), "Urgente", UUID.randomUUID());

        verify(noticeService).remindPayment(eq(r), eq(payment), isNull(), eq("Urgente"), eq(List.of("finanzas@example.com")));
        assertThat(r.getLastRemindedAt()).isAfter(Instant.now().minus(Duration.ofMinutes(1)));
        verify(auditRecorder).recordUpdate(eq(r.getUuid()), anyMap(), anyMap());
    }

    @Test
    void remind_ofAPaymentNotInReview_is422() {
        Payment payment = inReview();
        payment.setStatus("APPROVED");
        when(paymentRepository.findByUuid(payment.getUuid())).thenReturn(Optional.of(payment));
        when(repository.findByMembershipAndStatus(MEMBERSHIP_ID, RedemptionStatus.PENDING))
                .thenReturn(List.of(slot(RedemptionStatus.PENDING)));

        assertThatThrownBy(() -> service().remind(campaign.getUuid(), payment.getUuid(), null, null))
                .hasMessage("campaign_payment.remind.not_in_review");
    }

    @Test
    void remindPending_skipsTheSlotsRemindedWithinTheHour() {
        PromotionRedemption recent = slot(RedemptionStatus.PENDING);
        recent.setLastRemindedAt(Instant.now().minus(Duration.ofMinutes(5)));
        PromotionRedemption due = slot(RedemptionStatus.PENDING);
        PromotionRedemption withoutPayment = slot(RedemptionStatus.PENDING);
        Payment payment = inReview();
        when(repository.findByCampaignAndRedemptionStatus(campaign, RedemptionStatus.PENDING))
                .thenReturn(List.of(recent, due, withoutPayment));
        when(slotStatus.paymentInReview(any(PromotionRedemption.class)))
                .thenAnswer(inv -> inv.getArgument(0) == withoutPayment ? null : payment);

        CampaignPaymentRemindResultDto result = service().remindPending(campaign.getUuid(), null, null);

        assertThat(result.sent()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(due.getLastRemindedAt()).isNotNull();
    }

    @Test
    void payments_totalsCoverEverySlot_andTheFiltersOnlyNarrowThePage() {
        PromotionRedemption confirmed = slot(RedemptionStatus.CONFIRMED);
        PromotionRedemption inReview = slot(RedemptionStatus.PENDING);
        PromotionRedemption withoutPayment = slot(RedemptionStatus.PENDING);
        PromotionRedemption expired = slot(RedemptionStatus.EXPIRED);
        Payment payment = inReview();
        when(repository.findAllOfCampaign(campaign)).thenReturn(List.of(confirmed, inReview, withoutPayment, expired));
        when(slotStatus.paymentInReview(any(PromotionRedemption.class)))
                .thenAnswer(inv -> inv.getArgument(0) == inReview ? payment : null);

        CampaignPaymentsDto dto = service().payments(campaign.getUuid(), RedemptionStatus.PENDING, "PENDING",
                PageRequest.of(0, 20));

        assertThat(dto.totals()).isEqualTo(new CampaignPaymentsDto.Totals(1, 1, 1, 1));
        assertThat(dto.page().getTotalElements()).isEqualTo(1);
        assertThat(dto.page().getContent().get(0).slotUuid()).isEqualTo(inReview.getUuid());
        assertThat(dto.page().getContent().get(0).paymentStatus()).isEqualTo("PENDING");
        assertThat(dto.page().getContent().get(0).confirmsOnApproval()).isTrue();
    }
}
