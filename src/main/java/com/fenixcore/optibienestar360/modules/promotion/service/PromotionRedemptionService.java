package com.fenixcore.optibienestar360.modules.promotion.service;

import com.fenixcore.optibienestar360.core.display.DisplayRefs;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.campaign.dto.CampaignQuotaDto;
import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign.QuotaTrigger;
import com.fenixcore.optibienestar360.modules.campaign.repository.CampaignRepository;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment.PaymentStatus;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentRepository;
import com.fenixcore.optibienestar360.modules.promotion.entity.MembershipPromotion;
import com.fenixcore.optibienestar360.modules.promotion.entity.MembershipPromotion.Status;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.Kind;
import com.fenixcore.optibienestar360.modules.promotion.entity.PromotionRedemption;
import com.fenixcore.optibienestar360.modules.promotion.entity.PromotionRedemption.RedemptionStatus;
import com.fenixcore.optibienestar360.modules.promotion.repository.PromotionRedemptionRepository;
import com.fenixcore.optibienestar360.modules.promotion.repository.PromotionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * The lifecycle of a campaign slot (V257, hub ADR 0038 §5-§6): reserve on
 * assignment, confirm by the campaign's trigger, release when the promotion is
 * lost before any discount, expire a PENDING slot past its deadline, and end
 * the benefit once the usage window is over.
 *
 * <p>Counters: {@code redemptions_count} = PENDING + CONFIRMED (occupied),
 * {@code confirmed_count} = CONFIRMED, on both the campaign and the
 * promotion. Every change locks the campaign row and then the promotion row
 * ({@code SELECT ... FOR UPDATE}) in the transaction that writes the
 * counters, and re-derives the campaign state
 * ({@link Campaign#recomputeQuotaStatus}).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class PromotionRedemptionService {

    /** {@code membership_promotions.ended_reason} codes set by the slot lifecycle (hub ADR 0038). */
    public static final String REASON_QUOTA_EXPIRED = "QUOTA_EXPIRED";
    public static final String REASON_USAGE_WINDOW_ENDED = "USAGE_WINDOW_ENDED";
    public static final String REASON_CAMPAIGN_CLOSED = "CAMPAIGN_CLOSED";
    /** An admin released the slot by hand (V260, hub ADR 0038 §9). */
    public static final String REASON_SLOT_RELEASED = "SLOT_RELEASED";
    /** How early the member and their advisor are told a PENDING slot is about to expire. */
    public static final Duration EXPIRY_NOTICE_AHEAD = Duration.ofHours(48);

    private final PromotionRedemptionRepository repository;
    private final CampaignRepository campaignRepository;
    private final PromotionRepository promotionRepository;
    private final PaymentRepository paymentRepository;
    private final MembershipBenefitGrantService grantService;
    private final EntityManager entityManager;
    /**
     * Lazy: {@code MembershipChargeService → MembershipStatusService →
     * PromotionLifecycle → this} would otherwise be a constructor cycle.
     */
    private final ObjectProvider<com.fenixcore.optibienestar360.modules.membership.service.MembershipChargeService> chargeServiceProvider;
    /** Payment-side rules of a slot: frozen deadline, payment that confirms it (V260). */
    private final PromotionSlotStatusService slotStatus;
    private final PromotionSlotNoticeService noticeService;

    // ─── Reserve / confirm ──────────────────────────────────────────────────

    /**
     * Reserves the slot of a just-saved membership promotion. {@code campaign}
     * and {@code promotion} must already be row-locked by the caller
     * ({@code PromotionAssignmentService#assign}). ON_ENROLLMENT confirms at
     * once; the other triggers leave the slot PENDING until the payment, with
     * the campaign's {@code pending_ttl_days} as deadline.
     */
    @Transactional
    public PromotionRedemption reserve(Campaign campaign, Promotion promotion, MembershipPromotion mp, Instant now) {
        PromotionRedemption r = new PromotionRedemption();
        r.setMembershipPromotion(mp);
        r.setPromotion(promotion);
        r.setCampaign(campaign);
        r.setReservedAt(now);
        promotion.setRedemptionsCount(promotion.getRedemptionsCount() + 1);
        campaign.setRedemptionsCount(campaign.getRedemptionsCount() + 1);
        if (campaign.getQuotaTrigger() == QuotaTrigger.ON_ENROLLMENT) {
            markConfirmed(r, campaign, promotion, now, null);
        } else {
            r.setRedemptionStatus(RedemptionStatus.PENDING);
            r.setExpiresAt(campaign.getPendingTtlDays() == null ? null
                    : now.plus(Duration.ofDays(campaign.getPendingTtlDays())));
            // The payment the trigger waits for may already exist (a promotion assigned after the
            // inscription was paid): no event will come for it, so it confirms the slot right away.
            Payment alreadyPaid = existingQualifyingPayment(campaign.getQuotaTrigger(), promotion, mp);
            if (alreadyPaid != null) {
                markConfirmed(r, campaign, promotion, now, alreadyPaid);
            }
        }
        // Saved before the state is re-derived: a close by QUOTA_FILLED must find this slot too.
        PromotionRedemption saved = repository.save(r);
        recomputeQuotaStatus(campaign);
        return saved;
    }

    /** ON_PAYMENT_REGISTERED: a (non-draft) collection was registered. Runs after the registration commits. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int onPaymentRegistered(Long paymentId) {
        return confirmFor(paymentId, QuotaTrigger.ON_PAYMENT_REGISTERED);
    }

    /** ON_PAYMENT_CONFIRMED: a collection was approved (ADR 0008). Runs after the approval commits. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int onPaymentApproved(Long paymentId) {
        return confirmFor(paymentId, QuotaTrigger.ON_PAYMENT_CONFIRMED);
    }

    /**
     * Confirms the membership's PENDING slots whose campaign waits for
     * {@code trigger}: the inscription payment confirms an ACQUISITION
     * promotion, a monthly-fee payment a RECOVERY one (it has no
     * inscription). A slot past its effective deadline (own deadline cut by
     * the post-close grace) is left for the job to expire — unless the
     * campaign waits for the approval and this payment was registered before
     * that deadline: the payment in review froze the deadline, so its late
     * approval still confirms (V260, hub ADR 0038 §9).
     */
    int confirmFor(Long paymentId, QuotaTrigger trigger) {
        Payment payment = paymentId == null ? null : paymentRepository.findById(paymentId).orElse(null);
        if (payment == null || "OUT".equals(payment.getDirection())
                || payment.getMembership() == null || payment.getMembership().getId() == null) {
            return 0;
        }
        Instant now = Instant.now();
        int confirmed = 0;
        for (PromotionRedemption pending : repository.findByMembershipAndStatus(
                payment.getMembership().getId(), RedemptionStatus.PENDING)) {
            Campaign campaign = lockCampaign(pending.getCampaign().getId());
            lockFresh(pending);
            if (campaign.getQuotaTrigger() != trigger || !pending.isPending()) {
                continue;
            }
            Promotion promotion = lockPromotion(pending.getPromotion().getId());
            if (payment.isInscription() != confirmedByInscription(promotion)) {
                continue;
            }
            Instant deadline = campaign.pendingDeadline(pending.getExpiresAt());
            boolean frozenByThisPayment = trigger == QuotaTrigger.ON_PAYMENT_CONFIRMED
                    && PromotionSlotStatusService.registeredBy(payment, deadline);
            if (deadline != null && now.isAfter(deadline) && !frozenByThisPayment) {
                continue;
            }
            markConfirmed(pending, campaign, promotion, now, payment);
            recomputeQuotaStatus(campaign);
            confirmed++;
        }
        return confirmed;
    }

    /**
     * A collection was rejected (runs after the review commits): the deadline
     * it froze runs again. A PENDING slot of the membership, of a campaign
     * that waits for the approval, whose deadline already passed and that no
     * other qualifying payment in review keeps frozen, expires now — same path
     * as the job (slot freed, promotion ended, open discounts undone, member
     * and advisor told).
     *
     * @return how many slots expired
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int onPaymentRejected(Long paymentId) {
        Payment payment = paymentId == null ? null : paymentRepository.findById(paymentId).orElse(null);
        if (payment == null || "OUT".equals(payment.getDirection())
                || payment.getMembership() == null || payment.getMembership().getId() == null) {
            return 0;
        }
        Instant now = Instant.now();
        int expired = 0;
        for (PromotionRedemption pending : repository.findByMembershipAndStatus(
                payment.getMembership().getId(), RedemptionStatus.PENDING)) {
            Campaign campaign = lockCampaign(pending.getCampaign().getId());
            lockFresh(pending);
            if (campaign.getQuotaTrigger() != QuotaTrigger.ON_PAYMENT_CONFIRMED || !pending.isPending()
                    || payment.isInscription() != confirmedByInscription(pending.getPromotion())) {
                continue;
            }
            Instant deadline = campaign.pendingDeadline(pending.getExpiresAt());
            if (deadline == null || deadline.isAfter(now) || slotStatus.isFrozen(pending)) {
                continue;
            }
            expire(pending, now);
            expired++;
        }
        return expired;
    }

    /**
     * Which payment confirms a slot of {@code promotion}: the inscription for
     * an ACQUISITION promotion (ally benefits included), a monthly fee for a
     * RECOVERY one (it has no inscription).
     */
    private static boolean confirmedByInscription(Promotion promotion) {
        return promotion.getKind() != Kind.RECOVERY;
    }

    /**
     * A payment of the membership that already satisfies {@code trigger}:
     * registered (PENDING or APPROVED) for ON_PAYMENT_REGISTERED, APPROVED for
     * ON_PAYMENT_CONFIRMED; of the kind {@link #confirmedByInscription} asks
     * for. For RECOVERY only a monthly fee registered after the membership fell
     * behind (its last status change) counts; when that moment is unknown, the
     * latest monthly fee does. {@code null} = none yet.
     */
    private Payment existingQualifyingPayment(QuotaTrigger trigger, Promotion promotion, MembershipPromotion mp) {
        Membership membership = mp != null ? mp.getMembership() : null;
        if (membership == null || membership.getId() == null) {
            return null;
        }
        List<String> statuses = trigger == QuotaTrigger.ON_PAYMENT_CONFIRMED
                ? List.of(PaymentStatus.APPROVED.name())
                : List.of(PaymentStatus.PENDING.name(), PaymentStatus.APPROVED.name());
        boolean inscription = confirmedByInscription(promotion);
        List<Payment> payments = paymentRepository.findCollectionsOfMembership(membership.getId(), inscription, statuses);
        if (payments.isEmpty()) {
            return null;
        }
        Payment latest = payments.get(0);
        if (inscription) {
            return latest;
        }
        Instant overdueSince = membership.getLastStatusChangeAt();
        return overdueSince == null || (latest.getCreatedAt() != null && latest.getCreatedAt().isAfter(overdueSince))
                ? latest : null;
    }

    private void markConfirmed(PromotionRedemption r, Campaign campaign, Promotion promotion, Instant now, Payment payment) {
        r.setRedemptionStatus(RedemptionStatus.CONFIRMED);
        r.setConfirmedAt(now);
        r.setConfirmedByPayment(payment);
        r.setUsageEndsAt(campaign.usageEndsAt(now));
        promotion.setConfirmedCount(promotion.getConfirmedCount() + 1);
        campaign.setConfirmedCount(campaign.getConfirmedCount() + 1);
        if (promotion.isAllyBenefit()) {
            // ALLY_BENEFIT (V257): the confirmed slot gives the member the ally voucher.
            grantService.grantFor(r);
        }
    }

    // ─── Release / expire ───────────────────────────────────────────────────

    /**
     * The membership promotion just ended (cancelled, lost by a delay): frees
     * its slot when it was still PENDING, or CONFIRMED without any discount
     * applied yet (no inscription discount and no discounted monthly charge).
     * Otherwise the slot stays CONFIRMED.
     *
     * @return whether the slot was released
     */
    @Transactional
    public boolean releaseIfUnused(MembershipPromotion mp) {
        if (mp == null || mp.getId() == null) {
            return false;
        }
        PromotionRedemption r = repository.findByMembershipPromotion_Id(mp.getId()).orElse(null);
        if (r == null) {
            grantService.cancelFor(mp);
            return false;
        }
        // "Used" = a fee discount was applied (DISCOUNT) or a voucher unit was redeemed (ALLY_BENEFIT, V257).
        boolean used = mp.getBenefitKind() == Promotion.BenefitKind.ALLY_BENEFIT
                ? grantService.anyRedeemed(mp)
                : mp.isInscriptionApplied() || repository.anyDiscountedCharge(mp.getId());
        boolean unusedConfirmed = r.isConfirmed() && !used;
        if (!r.isPending() && !unusedConfirmed) {
            // The slot stays CONFIRMED; whatever is left of a voucher is cancelled with the promotion.
            grantService.cancelFor(mp);
            return false;
        }
        free(r, RedemptionStatus.RELEASED, Instant.now());
        undoOpenDiscounts(mp);
        return true;
    }

    /**
     * Job: PENDING slots past their effective deadline become EXPIRED, free
     * their slot (a SOLD_OUT campaign goes back to ACTIVE) and end their
     * membership promotion ({@code QUOTA_EXPIRED}). Right before expiring,
     * the payment is checked again (V260, hub ADR 0038 §9): a slot whose
     * payment already satisfies the trigger in time is confirmed instead, and
     * a slot frozen by a qualifying payment in review is left alone.
     */
    @Transactional
    public int expirePending(Instant now) {
        int expired = 0;
        for (PromotionRedemption r : repository.findPendingExpiryCandidates(now)) {
            Campaign campaign = lockCampaign(r.getCampaign().getId());
            lockFresh(r);
            if (!r.isPending()) {
                continue;
            }
            Instant deadline = campaign.pendingDeadline(r.getExpiresAt());
            if (deadline == null || deadline.isAfter(now)) {
                continue;
            }
            Payment paid = slotStatus.confirmingPayment(r);
            if (paid != null) {
                Promotion promotion = lockPromotion(r.getPromotion().getId());
                markConfirmed(r, campaign, promotion, now, paid);
                recomputeQuotaStatus(campaign);
                continue;
            }
            if (slotStatus.isFrozen(r)) {
                continue;
            }
            expire(r, now);
            expired++;
        }
        return expired;
    }

    /** Expires a locked PENDING slot: frees it, ends its promotion, undoes the open discounts and tells the member. */
    private void expire(PromotionRedemption r, Instant now) {
        free(r, RedemptionStatus.EXPIRED, now);
        endIfOngoing(r.getMembershipPromotion(), Status.CANCELED, REASON_QUOTA_EXPIRED);
        undoOpenDiscounts(r.getMembershipPromotion());
        noticeService.notifyExpired(r);
    }

    /**
     * Job, before {@link #expirePending}: confirms the PENDING slots whose
     * payment already satisfies the campaign trigger in time (an event lost
     * or failed, a payment approved while the listener was down).
     *
     * @return how many slots were confirmed
     */
    @Transactional
    public int reconcilePending(Instant now) {
        int confirmed = 0;
        for (PromotionRedemption r : repository.findAllInStatus(RedemptionStatus.PENDING)) {
            Campaign campaign = lockCampaign(r.getCampaign().getId());
            lockFresh(r);
            if (!r.isPending()) {
                continue;
            }
            Payment paid = slotStatus.confirmingPayment(r);
            if (paid == null) {
                continue;
            }
            Promotion promotion = lockPromotion(r.getPromotion().getId());
            markConfirmed(r, campaign, promotion, now, paid);
            recomputeQuotaStatus(campaign);
            confirmed++;
        }
        return confirmed;
    }

    /**
     * Job: tells the member and their advisor, once, that a PENDING slot
     * expires within {@link #EXPIRY_NOTICE_AHEAD}. A frozen slot is not
     * warned (its deadline does not run while the payment is in review).
     *
     * @return how many slots were warned
     */
    @Transactional
    public int sendExpiryNotices(Instant now) {
        Instant horizon = now.plus(EXPIRY_NOTICE_AHEAD);
        int warned = 0;
        for (PromotionRedemption r : repository.findExpiryNoticeCandidates(horizon)) {
            if (!r.isPending() || r.getExpiryNoticeSentAt() != null) {
                continue;
            }
            Instant deadline = r.getCampaign().pendingDeadline(r.getExpiresAt());
            if (deadline == null || !deadline.isAfter(now) || deadline.isAfter(horizon) || slotStatus.isFrozen(r)) {
                continue;
            }
            noticeService.notifyExpiring(r, deadline);
            r.setExpiryNoticeSentAt(now);
            warned++;
        }
        return warned;
    }

    // ─── Manual confirm / release (V260, hub ADR 0038 §9) ────────────────────

    /**
     * An admin confirms a PENDING slot by hand (permission
     * {@code PROMOTION_SLOT_CONFIRM}), with a required note. Any other state
     * is rejected (422).
     */
    @Transactional
    public PromotionRedemption confirmManually(PromotionRedemption r, User actor, String note) {
        requireNote(note);
        Campaign campaign = lockCampaign(r.getCampaign().getId());
        lockFresh(r);
        if (!r.isPending()) {
            throw new IllegalArgumentException("promotion_slot.confirm.not_pending");
        }
        Promotion promotion = lockPromotion(r.getPromotion().getId());
        markConfirmed(r, campaign, promotion, Instant.now(), null);
        r.setManualNote(note.trim());
        r.setManualBy(actor);
        recomputeQuotaStatus(campaign);
        return r;
    }

    /**
     * An admin releases a slot by hand (permission
     * {@code PROMOTION_SLOT_RELEASE}), with a required note: a PENDING slot,
     * or a CONFIRMED one never used (no fee discount applied, no voucher unit
     * redeemed). The membership promotion ends ({@code SLOT_RELEASED}) and the
     * discounts still open are undone. Any other state is rejected (422).
     */
    @Transactional
    public PromotionRedemption releaseManually(PromotionRedemption r, User actor, String note) {
        requireNote(note);
        lockCampaign(r.getCampaign().getId());
        lockFresh(r);
        MembershipPromotion mp = r.getMembershipPromotion();
        boolean releasable = r.isPending() || (r.isConfirmed() && !isUsed(mp));
        if (!releasable) {
            throw new IllegalArgumentException("promotion_slot.release.not_allowed");
        }
        free(r, RedemptionStatus.RELEASED, Instant.now());
        r.setManualNote(note.trim());
        r.setManualBy(actor);
        endIfOngoing(mp, Status.CANCELED, REASON_SLOT_RELEASED);
        undoOpenDiscounts(mp);
        return r;
    }

    private static void requireNote(String note) {
        if (note == null || note.isBlank()) {
            throw new IllegalArgumentException("promotion_slot.note.required");
        }
    }

    /** "Used" = a fee discount was applied (DISCOUNT) or a voucher unit was redeemed (ALLY_BENEFIT, V257). */
    private boolean isUsed(MembershipPromotion mp) {
        if (mp == null) {
            return false;
        }
        return mp.getBenefitKind() == Promotion.BenefitKind.ALLY_BENEFIT
                ? grantService.anyRedeemed(mp)
                : mp.isInscriptionApplied() || (mp.getId() != null && repository.anyDiscountedCharge(mp.getId()));
    }

    /** Job: a CONFIRMED slot whose usage window ended ends its membership promotion; the slot stays CONFIRMED. */
    @Transactional
    public int endUsageWindows(Instant now) {
        int ended = 0;
        for (PromotionRedemption r : repository.findConfirmedWithUsageEnded(now)) {
            if (endIfOngoing(r.getMembershipPromotion(), Status.CONSUMED, REASON_USAGE_WINDOW_ENDED)) {
                ended++;
            }
        }
        return ended;
    }

    /** Manual close with {@code cancelPending}: every PENDING slot of the campaign is released and its promotion cancelled. */
    @Transactional
    public int cancelPending(Campaign campaign) {
        Instant now = Instant.now();
        int released = 0;
        for (PromotionRedemption r : repository.findByCampaignAndRedemptionStatus(campaign, RedemptionStatus.PENDING)) {
            free(r, RedemptionStatus.RELEASED, now);
            endIfOngoing(r.getMembershipPromotion(), Status.CANCELED, REASON_CAMPAIGN_CLOSED);
            undoOpenDiscounts(r.getMembershipPromotion());
            released++;
        }
        return released;
    }

    private void free(PromotionRedemption r, RedemptionStatus to, Instant now) {
        Campaign campaign = lockCampaign(r.getCampaign().getId());
        Promotion promotion = lockPromotion(r.getPromotion().getId());
        boolean wasConfirmed = r.isConfirmed();
        r.setRedemptionStatus(to);
        r.setReleasedAt(now);
        promotion.setRedemptionsCount(Math.max(0, promotion.getRedemptionsCount() - 1));
        campaign.setRedemptionsCount(Math.max(0, campaign.getRedemptionsCount() - 1));
        if (wasConfirmed) {
            promotion.setConfirmedCount(Math.max(0, promotion.getConfirmedCount() - 1));
            campaign.setConfirmedCount(Math.max(0, campaign.getConfirmedCount() - 1));
        }
        recomputeQuotaStatus(campaign);
        grantService.cancelFor(r.getMembershipPromotion());
    }

    /** Re-derives the quota-driven state; a close it causes (QUOTA_FILLED) runs {@link #onCampaignClosed}. */
    private void recomputeQuotaStatus(Campaign campaign) {
        boolean wasClosed = campaign.getLifecycleStatus() == Campaign.LifecycleStatus.CLOSED;
        campaign.recomputeQuotaStatus(OffsetDateTime.now());
        if (!wasClosed && campaign.getLifecycleStatus() == Campaign.LifecycleStatus.CLOSED) {
            onCampaignClosed(campaign);
        }
    }

    /**
     * The campaign was just closed (by hand, QUOTA_FILLED or END_DATE). With
     * the CAMPAIGN_DATES usage window, a CONFIRMED slot with no usage end (an
     * open-ended campaign) now ends at the close, and so do the AVAILABLE ally
     * vouchers without an expiry; the job then ends the promotions and expires
     * the vouchers once that instant passes.
     *
     * @return how many slots got the usage end
     */
    @Transactional
    public int onCampaignClosed(Campaign campaign) {
        if (campaign.getClosedAt() == null || campaign.getUsageWindow() != Campaign.UsageWindow.CAMPAIGN_DATES) {
            return 0;
        }
        Instant closedAt = campaign.getClosedAt().toInstant();
        int capped = 0;
        for (PromotionRedemption r : repository.findByCampaignAndRedemptionStatus(campaign, RedemptionStatus.CONFIRMED)) {
            if (r.getUsageEndsAt() == null) {
                r.setUsageEndsAt(closedAt);
                capped++;
            }
        }
        grantService.expireAtClose(campaign, closedAt);
        return capped;
    }

    /**
     * The promotion of a freed slot (EXPIRED/RELEASED) already ended: its
     * discount must not stay on what is still open. An inscription payment
     * still under review loses the discount the system put on it for this
     * promotion (an approved one is left as it is), and the open monthly
     * charges are re-priced without it. Must run after the promotion ended, or
     * the re-pricing would find it still ongoing. Ally vouchers never discount
     * fees, so they have nothing to undo.
     */
    private void undoOpenDiscounts(MembershipPromotion mp) {
        if (mp == null || mp.getBenefitKind() == Promotion.BenefitKind.ALLY_BENEFIT) {
            return;
        }
        Membership membership = mp.getMembership();
        if (membership == null || membership.getId() == null) {
            return;
        }
        for (Payment payment : paymentRepository.findCollectionsOfMembership(membership.getId(), true,
                List.of(PaymentStatus.DRAFT.name(), PaymentStatus.PENDING.name()))) {
            if (PromotionPricing.clearPromotionDiscount(payment, mp.getPromotion())) {
                mp.setInscriptionApplied(false);
            }
        }
        chargeServiceProvider.getObject().repriceOpenCharges(membership);
    }

    private static boolean endIfOngoing(MembershipPromotion mp, Status status, String reason) {
        if (mp == null || !mp.isOngoing()) {
            return false;
        }
        PromotionAssignmentService.end(mp, status, reason);
        return true;
    }

    // ─── Read ───────────────────────────────────────────────────────────────

    public PromotionRedemption findFor(MembershipPromotion mp) {
        return mp == null || mp.getId() == null ? null
                : repository.findByMembershipPromotion_Id(mp.getId()).orElse(null);
    }

    /** Whether a qualifying payment in review keeps the PENDING slot's deadline from running (V260). */
    public boolean isDeadlineFrozen(PromotionRedemption r) {
        return r != null && slotStatus.isFrozen(r);
    }

    /** Quota counters of a campaign and of each of its promotions ({@code GET /v1/admin/campaigns/{uuid}/quota}). */
    public CampaignQuotaDto quota(UUID campaignUuid) {
        Campaign campaign = campaignRepository.findByUuid(campaignUuid)
                .orElseThrow(() -> new NoSuchElementException("campaign.not_found"));
        Map<Long, Map<RedemptionStatus, Long>> byPromotion = new HashMap<>();
        Map<RedemptionStatus, Long> totals = new EnumMap<>(RedemptionStatus.class);
        for (Object[] row : repository.countByPromotionAndStatus(campaign)) {
            Long promotionId = (Long) row[0];
            RedemptionStatus status = (RedemptionStatus) row[1];
            long count = ((Number) row[2]).longValue();
            byPromotion.computeIfAbsent(promotionId, k -> new EnumMap<>(RedemptionStatus.class)).merge(status, count, Long::sum);
            totals.merge(status, count, Long::sum);
        }
        Integer campaignFree = campaign.freeSlots();
        List<CampaignQuotaDto.PromotionQuota> promotions = promotionRepository
                .findByCampaignAndActiveTrueOrderByCreatedAtAsc(campaign).stream()
                .map(p -> {
                    Map<RedemptionStatus, Long> counts = byPromotion.getOrDefault(p.getId(), Map.of());
                    Integer ownFree = p.isOwnQuota() && p.getMaxRedemptions() != null
                            ? Math.max(0, p.getMaxRedemptions() - p.getRedemptionsCount()) : null;
                    return new CampaignQuotaDto.PromotionQuota(
                            DisplayRefs.ref(p), p.isOwnQuota(), p.getMaxRedemptions(),
                            p.getConfirmedCount(), p.getRedemptionsCount() - p.getConfirmedCount(),
                            minOrNull(ownFree, campaignFree),
                            counts.getOrDefault(RedemptionStatus.EXPIRED, 0L).intValue(),
                            counts.getOrDefault(RedemptionStatus.RELEASED, 0L).intValue());
                })
                .toList();
        return new CampaignQuotaDto(
                campaign.getMaxRedemptions(),
                campaign.getConfirmedCount(),
                campaign.getRedemptionsCount() - campaign.getConfirmedCount(),
                campaignFree,
                totals.getOrDefault(RedemptionStatus.EXPIRED, 0L).intValue(),
                totals.getOrDefault(RedemptionStatus.RELEASED, 0L).intValue(),
                promotions);
    }

    /** The smaller of two optional limits; {@code null} only when both are unlimited. */
    static Integer minOrNull(Integer a, Integer b) {
        if (a == null) return b;
        if (b == null) return a;
        return Math.min(a, b);
    }

    // ─── Locks ──────────────────────────────────────────────────────────────

    /**
     * Row-locks the slot and re-reads it, after its campaign lock: the row was
     * loaded before that lock, and a payment confirmation and the expiry job
     * racing on the same PENDING slot must see each other's outcome (else it
     * could be confirmed and expired at once, counted twice).
     */
    private void lockFresh(PromotionRedemption r) {
        entityManager.refresh(r, LockModeType.PESSIMISTIC_WRITE);
    }

    private Campaign lockCampaign(Long id) {
        return campaignRepository.findForUpdateById(id)
                .orElseThrow(() -> new NoSuchElementException("campaign.not_found"));
    }

    private Promotion lockPromotion(Long id) {
        return promotionRepository.findForUpdateById(id)
                .orElseThrow(() -> new NoSuchElementException("promotion.not_found"));
    }
}
