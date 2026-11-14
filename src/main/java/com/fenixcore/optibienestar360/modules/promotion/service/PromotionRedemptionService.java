package com.fenixcore.optibienestar360.modules.promotion.service;

import com.fenixcore.optibienestar360.core.display.DisplayRefs;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final PromotionRedemptionRepository repository;
    private final CampaignRepository campaignRepository;
    private final PromotionRepository promotionRepository;
    private final PaymentRepository paymentRepository;
    private final MembershipBenefitGrantService grantService;

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
        campaign.recomputeQuotaStatus(OffsetDateTime.now());
        return repository.save(r);
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
     * the post-close grace) is left for the job to expire.
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
            if (campaign.getQuotaTrigger() != trigger || !pending.isPending()) {
                continue;
            }
            Promotion promotion = lockPromotion(pending.getPromotion().getId());
            if (payment.isInscription() != confirmedByInscription(promotion)) {
                continue;
            }
            Instant deadline = campaign.pendingDeadline(pending.getExpiresAt());
            if (deadline != null && now.isAfter(deadline)) {
                continue;
            }
            markConfirmed(pending, campaign, promotion, now, payment);
            campaign.recomputeQuotaStatus(OffsetDateTime.now());
            confirmed++;
        }
        return confirmed;
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
        return true;
    }

    /**
     * Job: PENDING slots past their effective deadline become EXPIRED, free
     * their slot (a SOLD_OUT campaign goes back to ACTIVE) and end their
     * membership promotion ({@code QUOTA_EXPIRED}).
     */
    @Transactional
    public int expirePending(Instant now) {
        int expired = 0;
        for (PromotionRedemption r : repository.findPendingExpiryCandidates(now)) {
            Campaign campaign = lockCampaign(r.getCampaign().getId());
            if (!r.isPending()) {
                continue;
            }
            Instant deadline = campaign.pendingDeadline(r.getExpiresAt());
            if (deadline == null || deadline.isAfter(now)) {
                continue;
            }
            free(r, RedemptionStatus.EXPIRED, now);
            endIfOngoing(r.getMembershipPromotion(), Status.CANCELED, REASON_QUOTA_EXPIRED);
            expired++;
        }
        return expired;
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
        campaign.recomputeQuotaStatus(OffsetDateTime.now());
        grantService.cancelFor(r.getMembershipPromotion());
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

    private Campaign lockCampaign(Long id) {
        return campaignRepository.findForUpdateById(id)
                .orElseThrow(() -> new NoSuchElementException("campaign.not_found"));
    }

    private Promotion lockPromotion(Long id) {
        return promotionRepository.findForUpdateById(id)
                .orElseThrow(() -> new NoSuchElementException("promotion.not_found"));
    }
}
