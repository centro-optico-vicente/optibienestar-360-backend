package com.fenixcore.optibienestar360.modules.promotion.service;

import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.core.display.DisplayRefs;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.campaign.dto.CampaignPaymentRemindResultDto;
import com.fenixcore.optibienestar360.modules.campaign.dto.CampaignPaymentRowDto;
import com.fenixcore.optibienestar360.modules.campaign.dto.CampaignPaymentsDto;
import com.fenixcore.optibienestar360.modules.campaign.dto.CampaignSlotDto;
import com.fenixcore.optibienestar360.modules.campaign.dto.CampaignSlotIssueDto;
import com.fenixcore.optibienestar360.modules.campaign.dto.CampaignSlotIssueDto.IssueType;
import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
import com.fenixcore.optibienestar360.modules.campaign.repository.CampaignRepository;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment.PaymentStatus;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentRepository;
import com.fenixcore.optibienestar360.modules.promotion.entity.MembershipPromotion;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion;
import com.fenixcore.optibienestar360.modules.promotion.entity.PromotionRedemption;
import com.fenixcore.optibienestar360.modules.promotion.entity.PromotionRedemption.RedemptionStatus;
import com.fenixcore.optibienestar360.modules.promotion.repository.PromotionRedemptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Admin view and manual control of a campaign's slots against their payments
 * (V260, hub ADR 0038 §9): slot list, manual confirm / release with a note,
 * inconsistency report, and the "Pagos" section with internal reminders (at
 * most one per slot per hour). The slot lifecycle itself (locks, counters)
 * stays in {@link PromotionRedemptionService}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CampaignSlotService {

    /** Permission of whoever approves collections: they get the internal payment reminders. */
    public static final String PAYMENT_APPROVER_PERMISSION = "COLLECTION_APPROVE";
    static final Duration REMIND_THROTTLE = Duration.ofHours(1);
    static final Duration FROZEN_TOO_LONG = Duration.ofDays(7);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Caracas");

    private final CampaignRepository campaignRepository;
    private final PromotionRedemptionRepository repository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final PromotionRedemptionService redemptionService;
    private final PromotionSlotStatusService slotStatus;
    private final PromotionSlotNoticeService noticeService;
    private final PromotionSlotAuditRecorder auditRecorder;

    // ─── Slots ──────────────────────────────────────────────────────────────

    /** {@code GET /v1/admin/campaigns/{uuid}/slots}: every slot, or only those in {@code status}. */
    public Page<CampaignSlotDto> slots(UUID campaignUuid, RedemptionStatus status, Pageable pageable) {
        Campaign campaign = findCampaign(campaignUuid);
        Page<PromotionRedemption> page = status == null
                ? repository.findByCampaign(campaign, pageable)
                : repository.findByCampaignAndRedemptionStatus(campaign, status, pageable);
        return page.map(this::toSlotDto);
    }

    /** Manual confirm of a PENDING slot ({@code PROMOTION_SLOT_CONFIRM}); 422 for any other state. */
    @Transactional
    public CampaignSlotDto confirm(UUID campaignUuid, UUID slotUuid, String note, UUID actorUuid) {
        PromotionRedemption r = findSlot(campaignUuid, slotUuid);
        RedemptionStatus before = r.getRedemptionStatus();
        redemptionService.confirmManually(r, findUser(actorUuid), note);
        audit(r, before, "CONFIRM");
        return toSlotDto(r);
    }

    /** Manual release of a PENDING or unused CONFIRMED slot ({@code PROMOTION_SLOT_RELEASE}); 422 otherwise. */
    @Transactional
    public CampaignSlotDto release(UUID campaignUuid, UUID slotUuid, String note, UUID actorUuid) {
        PromotionRedemption r = findSlot(campaignUuid, slotUuid);
        RedemptionStatus before = r.getRedemptionStatus();
        redemptionService.releaseManually(r, findUser(actorUuid), note);
        audit(r, before, "RELEASE");
        return toSlotDto(r);
    }

    // ─── Issue report ───────────────────────────────────────────────────────

    /**
     * {@code GET /v1/admin/campaigns/{uuid}/slot-issues}: approved payments
     * that carry the promotion's discount while their slot is not CONFIRMED,
     * PENDING slots whose payment already satisfies the trigger in time, and
     * PENDING slots frozen by a payment in review for more than 7 days.
     */
    public List<CampaignSlotIssueDto> issues(UUID campaignUuid) {
        Campaign campaign = findCampaign(campaignUuid);
        Instant now = Instant.now();
        List<CampaignSlotIssueDto> issues = new ArrayList<>();
        for (PromotionRedemption r : repository.findAllOfCampaign(campaign)) {
            Membership membership = membershipOf(r);
            if (membership == null) {
                continue;
            }
            if (!r.isConfirmed() && r.getPromotion().getBenefitKind() != Promotion.BenefitKind.ALLY_BENEFIT) {
                String prefix = PromotionPricing.discountReasonPrefix(r.getPromotion());
                for (Payment p : paymentRepository.findCollectionsOfMembership(membership.getId(), true,
                        List.of(PaymentStatus.APPROVED.name()))) {
                    if (p.getDiscountAmount() != null && p.getDiscountReason() != null
                            && p.getDiscountReason().startsWith(prefix)) {
                        issues.add(issue(IssueType.APPROVED_DISCOUNT_WITHOUT_SLOT, r, p, reviewedOrCreated(p)));
                    }
                }
            }
            if (!r.isPending()) {
                continue;
            }
            Payment confirming = slotStatus.confirmingPayment(r);
            if (confirming != null) {
                issues.add(issue(IssueType.PENDING_WITH_APPROVED_PAYMENT, r, confirming, reviewedOrCreated(confirming)));
                continue;
            }
            Payment freezing = slotStatus.freezingPayment(r);
            if (freezing != null && freezing.getCreatedAt() != null
                    && freezing.getCreatedAt().plus(FROZEN_TOO_LONG).isBefore(now)) {
                issues.add(issue(IssueType.FROZEN_TOO_LONG, r, freezing, freezing.getCreatedAt()));
            }
        }
        return issues;
    }

    private CampaignSlotIssueDto issue(IssueType type, PromotionRedemption r, Payment payment, Instant since) {
        Membership membership = membershipOf(r);
        return new CampaignSlotIssueDto(type,
                DisplayRefs.ref(membership != null ? membership.getMember() : null),
                DisplayRefs.ref(membership),
                DisplayRefs.ref(r.getPromotion()),
                DisplayRefs.ref(payment),
                r.getUuid(),
                r.getRedemptionStatus(),
                since);
    }

    // ─── Payments section ───────────────────────────────────────────────────

    /**
     * {@code GET /v1/admin/campaigns/{uuid}/payments}: one row per slot with
     * the payment that confirmed it (CONFIRMED) or the one in review (PENDING;
     * none yet = no payment). Totals cover every slot; {@code slotStatus} and
     * {@code paymentStatus} (raw payment status, or {@code NONE} for rows
     * without a payment) filter the page.
     */
    public CampaignPaymentsDto payments(UUID campaignUuid, RedemptionStatus slotStatusFilter, String paymentStatus,
                                        Pageable pageable) {
        Campaign campaign = findCampaign(campaignUuid);
        int confirmed = 0;
        int pendingInReview = 0;
        int pendingWithoutPayment = 0;
        int expired = 0;
        List<CampaignPaymentRowDto> rows = new ArrayList<>();
        for (PromotionRedemption r : repository.findAllOfCampaign(campaign)) {
            Payment payment = paymentOf(r);
            switch (r.getRedemptionStatus()) {
                case CONFIRMED -> confirmed++;
                case PENDING -> {
                    if (payment != null) pendingInReview++;
                    else pendingWithoutPayment++;
                }
                case EXPIRED -> expired++;
                default -> { }
            }
            if (slotStatusFilter != null && r.getRedemptionStatus() != slotStatusFilter) {
                continue;
            }
            if (paymentStatus != null && !paymentStatus.isBlank()) {
                String wanted = paymentStatus.trim().toUpperCase(java.util.Locale.ROOT);
                boolean matches = payment == null ? "NONE".equals(wanted) : wanted.equals(payment.getStatus());
                if (!matches) {
                    continue;
                }
            }
            rows.add(toPaymentRow(r, payment));
        }
        int from = (int) Math.min(pageable.getOffset(), rows.size());
        int to = pageable.isPaged() ? Math.min(from + pageable.getPageSize(), rows.size()) : rows.size();
        Page<CampaignPaymentRowDto> page = new PageImpl<>(rows.subList(from, to), pageable, rows.size());
        return new CampaignPaymentsDto(
                new CampaignPaymentsDto.Totals(confirmed, pendingInReview, pendingWithoutPayment, expired), page);
    }

    /**
     * {@code POST /v1/admin/campaigns/{uuid}/payments/{paymentUuid}/remind}:
     * emails the member's advisor and every active user who approves
     * collections that this payment (in review) confirms the slot. 422
     * {@code campaign_payment.remind.too_soon} when the slot was reminded less
     * than an hour ago.
     */
    @Transactional
    public void remind(UUID campaignUuid, UUID paymentUuid, String note, UUID actorUuid) {
        Campaign campaign = findCampaign(campaignUuid);
        Payment payment = paymentRepository.findByUuid(paymentUuid)
                .orElseThrow(() -> new NoSuchElementException("payment.not_found"));
        PromotionRedemption r = pendingSlotOf(campaign, payment);
        if (!PaymentStatus.PENDING.name().equals(payment.getStatus())) {
            throw new IllegalArgumentException("campaign_payment.remind.not_in_review");
        }
        Instant now = Instant.now();
        if (remindedRecently(r, now)) {
            throw new IllegalArgumentException("campaign_payment.remind.too_soon");
        }
        sendReminder(r, payment, note, findUser(actorUuid), now,
                userRepository.findActiveEmailsWithPermission(PAYMENT_APPROVER_PERMISSION));
    }

    /**
     * {@code POST /v1/admin/campaigns/{uuid}/payments/remind-pending}: a
     * reminder for every PENDING slot with a payment in review; those
     * reminded less than an hour ago are skipped.
     */
    @Transactional
    public CampaignPaymentRemindResultDto remindPending(UUID campaignUuid, String note, UUID actorUuid) {
        Campaign campaign = findCampaign(campaignUuid);
        Instant now = Instant.now();
        User actor = findUser(actorUuid);
        List<String> approvers = null;
        int sent = 0;
        int skipped = 0;
        for (PromotionRedemption r : repository.findByCampaignAndRedemptionStatus(campaign, RedemptionStatus.PENDING)) {
            Payment payment = slotStatus.paymentInReview(r);
            if (payment == null) {
                continue;
            }
            if (remindedRecently(r, now)) {
                skipped++;
                continue;
            }
            if (approvers == null) {
                approvers = userRepository.findActiveEmailsWithPermission(PAYMENT_APPROVER_PERMISSION);
            }
            sendReminder(r, payment, note, actor, now, approvers);
            sent++;
        }
        return new CampaignPaymentRemindResultDto(sent, skipped);
    }

    private void sendReminder(PromotionRedemption r, Payment payment, String note, User actor, Instant now,
                              List<String> approvers) {
        Instant before = r.getLastRemindedAt();
        noticeService.remindPayment(r, payment, PromotionSlotStatusService.effectiveDeadline(r), note, approvers);
        r.setLastRemindedAt(now);
        r.setLastRemindedBy(actor);
        Map<String, Object> beforeMap = new HashMap<>();
        beforeMap.put("lastRemindedAt", before);
        Map<String, Object> afterMap = new HashMap<>();
        afterMap.put("action", "REMIND");
        afterMap.put("lastRemindedAt", now);
        afterMap.put("paymentUuid", payment.getUuid());
        afterMap.put("note", note);
        auditRecorder.recordUpdate(r.getUuid(), beforeMap, afterMap);
    }

    static boolean remindedRecently(PromotionRedemption r, Instant now) {
        return r.getLastRemindedAt() != null && r.getLastRemindedAt().plus(REMIND_THROTTLE).isAfter(now);
    }

    /** The PENDING slot of {@code campaign} that {@code payment} is the kind of payment for; 404 when none. */
    private PromotionRedemption pendingSlotOf(Campaign campaign, Payment payment) {
        if (payment.getMembership() == null || payment.getMembership().getId() == null
                || "OUT".equals(payment.getDirection())) {
            throw new NoSuchElementException("campaign_payment.slot.not_found");
        }
        return repository.findByMembershipAndStatus(payment.getMembership().getId(), RedemptionStatus.PENDING)
                .stream()
                .filter(r -> r.getCampaign() != null && campaign.getId().equals(r.getCampaign().getId()))
                .filter(r -> PromotionSlotStatusService.ofTheKind(payment, r))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("campaign_payment.slot.not_found"));
    }

    // ─── Mapping ────────────────────────────────────────────────────────────

    private Payment paymentOf(PromotionRedemption r) {
        if (r.isPending()) {
            return slotStatus.paymentInReview(r);
        }
        return r.getConfirmedByPayment();
    }

    CampaignSlotDto toSlotDto(PromotionRedemption r) {
        Membership membership = membershipOf(r);
        Member member = membership != null ? membership.getMember() : null;
        MembershipPromotion mp = r.getMembershipPromotion();
        Campaign campaign = r.getCampaign();
        return new CampaignSlotDto(
                r.getUuid(),
                DisplayRefs.ref(member),
                DisplayRefs.ref(membership),
                DisplayRefs.ref(r.getPromotion()),
                advisorOf(member),
                mp != null ? mp.getBenefitKind() : r.getPromotion().getBenefitKind(),
                r.getRedemptionStatus(),
                campaign != null ? campaign.getQuotaTrigger() : null,
                r.getReservedAt(),
                r.getConfirmedAt(),
                PromotionSlotStatusService.effectiveDeadline(r),
                slotStatus.isFrozen(r),
                DisplayRefs.ref(r.getConfirmedByPayment()),
                r.getManualNote(),
                DisplayRefs.ref(r.getManualBy()));
    }

    private CampaignPaymentRowDto toPaymentRow(PromotionRedemption r, Payment payment) {
        Membership membership = membershipOf(r);
        Member member = membership != null ? membership.getMember() : null;
        return new CampaignPaymentRowDto(
                DisplayRefs.ref(payment),
                payment != null ? PromotionSlotNoticeService.referenceOf(payment) : null,
                payment != null ? payment.getAmount() : null,
                payment != null && payment.getCurrency() != null ? payment.getCurrency().getCode() : null,
                payment != null && payment.getPaymentDate() != null
                        ? LocalDate.ofInstant(payment.getPaymentDate(), BUSINESS_ZONE) : null,
                payment != null ? payment.getStatus() : null,
                DisplayRefs.ref(member),
                DisplayRefs.ref(membership),
                advisorOf(member),
                DisplayRefs.ref(r.getPromotion()),
                r.getUuid(),
                r.getRedemptionStatus(),
                PromotionSlotStatusService.effectiveDeadline(r),
                r.isPending() && slotStatus.isFrozen(r),
                PromotionSlotStatusService.confirmsOnApproval(payment, r),
                r.getLastRemindedAt());
    }

    private static DisplayRef advisorOf(Member member) {
        return member != null && member.getPromoter() != null ? DisplayRefs.ref((Object) member.getPromoter()) : null;
    }

    private void audit(PromotionRedemption r, RedemptionStatus before, String action) {
        Map<String, Object> beforeMap = new HashMap<>();
        beforeMap.put("redemptionStatus", before != null ? before.name() : null);
        Map<String, Object> afterMap = new HashMap<>();
        afterMap.put("action", action);
        afterMap.put("redemptionStatus", r.getRedemptionStatus().name());
        afterMap.put("manualNote", r.getManualNote());
        auditRecorder.recordUpdate(r.getUuid(), beforeMap, afterMap);
    }

    private static Instant reviewedOrCreated(Payment p) {
        return p.getReviewedAt() != null ? p.getReviewedAt() : p.getCreatedAt();
    }

    private static Membership membershipOf(PromotionRedemption r) {
        MembershipPromotion mp = r.getMembershipPromotion();
        return mp != null ? mp.getMembership() : null;
    }

    private Campaign findCampaign(UUID uuid) {
        return campaignRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("campaign.not_found"));
    }

    private PromotionRedemption findSlot(UUID campaignUuid, UUID slotUuid) {
        Campaign campaign = findCampaign(campaignUuid);
        PromotionRedemption r = repository.findByUuid(slotUuid)
                .orElseThrow(() -> new NoSuchElementException("promotion_slot.not_found"));
        if (r.getCampaign() == null || !campaign.getId().equals(r.getCampaign().getId())) {
            throw new NoSuchElementException("promotion_slot.not_found");
        }
        return r;
    }

    private User findUser(UUID uuid) {
        return uuid == null ? null : userRepository.findByUuid(uuid).orElse(null);
    }
}
