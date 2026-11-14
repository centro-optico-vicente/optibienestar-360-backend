package com.fenixcore.optibienestar360.modules.promotion.service;

import com.fenixcore.optibienestar360.core.display.DisplayRefs;
import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign.QuotaTrigger;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment.PaymentStatus;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentRepository;
import com.fenixcore.optibienestar360.modules.promotion.dto.MembershipBenefitGrantDto;
import com.fenixcore.optibienestar360.modules.promotion.dto.PaymentPromotionSlotDto;
import com.fenixcore.optibienestar360.modules.promotion.dto.PendingBenefitDto;
import com.fenixcore.optibienestar360.modules.promotion.entity.MembershipBenefitGrant.GrantStatus;
import com.fenixcore.optibienestar360.modules.promotion.entity.MembershipPromotion;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.Kind;
import com.fenixcore.optibienestar360.modules.promotion.entity.PromotionRedemption;
import com.fenixcore.optibienestar360.modules.promotion.entity.PromotionRedemption.RedemptionStatus;
import com.fenixcore.optibienestar360.modules.promotion.repository.PromotionRedemptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Read-only rules that tie a campaign slot to its payments (V260, hub ADR
 * 0038 §9). Kept apart from {@link PromotionRedemptionService} (which writes
 * the slots and their counters) so the payment detail and the validator can
 * show the slot state without depending on the slot lifecycle.
 *
 * <ul>
 *   <li>A slot is confirmed by the inscription for an ACQUISITION promotion
 *       (ally benefits included) and by a monthly fee for a RECOVERY one,
 *       registered after the membership fell behind.</li>
 *   <li>A PENDING slot of a campaign that confirms on the approved payment
 *       is <b>frozen</b> while such a payment, registered before the slot's
 *       effective deadline, is in review: it does not expire, and approving
 *       that payment confirms it even after the deadline.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PromotionSlotStatusService {

    private static final List<String> IN_REVIEW = List.of(PaymentStatus.PENDING.name());

    private final PaymentRepository paymentRepository;
    private final PromotionRedemptionRepository repository;

    // ─── Rules ──────────────────────────────────────────────────────────────

    /** Which payment confirms a slot of {@code promotion}: the inscription, or a monthly fee for RECOVERY. */
    public static boolean confirmedByInscription(Promotion promotion) {
        return promotion == null || promotion.getKind() != Kind.RECOVERY;
    }

    /** Effective deadline of a PENDING slot (own deadline cut by the post-close grace); {@code null} otherwise. */
    public static Instant effectiveDeadline(PromotionRedemption r) {
        return r != null && r.isPending() && r.getCampaign() != null
                ? r.getCampaign().pendingDeadline(r.getExpiresAt()) : null;
    }

    /** Whether {@code payment} was registered no later than {@code deadline} ({@code null} deadline = always). */
    public static boolean registeredBy(Payment payment, Instant deadline) {
        return deadline == null || (payment.getCreatedAt() != null && !payment.getCreatedAt().isAfter(deadline));
    }

    /**
     * Whether {@code payment} is the kind of collection the slot waits for:
     * the inscription, or for RECOVERY a monthly fee registered after the
     * membership fell behind (its last status change; any one when unknown).
     * The membership itself is not compared here.
     */
    public static boolean ofTheKind(Payment payment, PromotionRedemption r) {
        if (payment == null || "OUT".equals(payment.getDirection())) {
            return false;
        }
        boolean inscription = confirmedByInscription(r.getPromotion());
        if (payment.isInscription() != inscription) {
            return false;
        }
        if (inscription) {
            return true;
        }
        Membership membership = membershipOf(r);
        Instant overdueSince = membership != null ? membership.getLastStatusChangeAt() : null;
        return overdueSince == null
                || (payment.getCreatedAt() != null && payment.getCreatedAt().isAfter(overdueSince));
    }

    /** The slot's qualifying collections in one of {@code statuses}, newest registration first. */
    public List<Payment> qualifyingPayments(PromotionRedemption r, List<String> statuses) {
        Membership membership = membershipOf(r);
        if (membership == null || membership.getId() == null) {
            return List.of();
        }
        return paymentRepository.findCollectionsOfMembership(membership.getId(),
                        confirmedByInscription(r.getPromotion()), statuses).stream()
                .filter(p -> ofTheKind(p, r))
                .toList();
    }

    /**
     * The payment in review that freezes a PENDING slot's deadline: a
     * qualifying collection registered before the effective deadline, while
     * the campaign confirms on the approved payment. {@code null} = the
     * deadline runs (or there is none).
     */
    public Payment freezingPayment(PromotionRedemption r) {
        if (r == null || !r.isPending()) {
            return null;
        }
        Campaign campaign = r.getCampaign();
        if (campaign == null || campaign.getQuotaTrigger() != QuotaTrigger.ON_PAYMENT_CONFIRMED) {
            return null;
        }
        Instant deadline = campaign.pendingDeadline(r.getExpiresAt());
        if (deadline == null) {
            return null;
        }
        return qualifyingPayments(r, IN_REVIEW).stream()
                .filter(p -> registeredBy(p, deadline))
                .findFirst()
                .orElse(null);
    }

    public boolean isFrozen(PromotionRedemption r) {
        return freezingPayment(r) != null;
    }

    /**
     * A payment that already satisfies the campaign trigger in time
     * (registered — or approved — and registered before the effective
     * deadline): the hourly reconciliation confirms the slot with it.
     * {@code null} = none.
     */
    public Payment confirmingPayment(PromotionRedemption r) {
        if (r == null || !r.isPending() || r.getCampaign() == null) {
            return null;
        }
        QuotaTrigger trigger = r.getCampaign().getQuotaTrigger();
        if (trigger == null || trigger == QuotaTrigger.ON_ENROLLMENT) {
            return null;
        }
        List<String> statuses = trigger == QuotaTrigger.ON_PAYMENT_CONFIRMED
                ? List.of(PaymentStatus.APPROVED.name())
                : List.of(PaymentStatus.PENDING.name(), PaymentStatus.APPROVED.name());
        Instant deadline = effectiveDeadline(r);
        return qualifyingPayments(r, statuses).stream()
                .filter(p -> registeredBy(p, deadline))
                .findFirst()
                .orElse(null);
    }

    /** The newest qualifying payment in review of a PENDING slot (payments section); {@code null} = none yet. */
    public Payment paymentInReview(PromotionRedemption r) {
        if (r == null || !r.isPending()) {
            return null;
        }
        return qualifyingPayments(r, IN_REVIEW).stream().findFirst().orElse(null);
    }

    /**
     * Approving {@code payment} confirms the slot: the payment is in review,
     * the slot PENDING, the campaign confirms on the approved payment, and the
     * payment qualifies (kind and registered before the deadline).
     */
    public static boolean confirmsOnApproval(Payment payment, PromotionRedemption r) {
        return payment != null && r != null
                && PaymentStatus.PENDING.name().equals(payment.getStatus())
                && r.isPending()
                && r.getCampaign() != null
                && r.getCampaign().getQuotaTrigger() == QuotaTrigger.ON_PAYMENT_CONFIRMED
                && ofTheKind(payment, r)
                && registeredBy(payment, effectiveDeadline(r));
    }

    // ─── Read models ────────────────────────────────────────────────────────

    /**
     * {@code promotionSlot} of a payment: the slot it confirmed, else the
     * PENDING slot of its membership it is the kind of payment for.
     * {@code null} for a payout, a payment without membership, or none.
     */
    public PaymentPromotionSlotDto slotFor(Payment payment) {
        if (payment == null || payment.getId() == null || "OUT".equals(payment.getDirection())
                || payment.getMembership() == null || payment.getMembership().getId() == null) {
            return null;
        }
        PromotionRedemption r = repository.findFirstByConfirmedByPayment_IdOrderByConfirmedAtDesc(payment.getId())
                .orElse(null);
        if (r == null) {
            r = repository.findByMembershipAndStatus(payment.getMembership().getId(), RedemptionStatus.PENDING)
                    .stream()
                    .filter(s -> ofTheKind(payment, s))
                    .findFirst()
                    .orElse(null);
        }
        if (r == null) {
            return null;
        }
        Promotion promotion = r.getPromotion();
        Campaign campaign = r.getCampaign();
        return new PaymentPromotionSlotDto(
                r.getUuid(),
                DisplayRefs.ref(promotion),
                DisplayRefs.ref(campaign),
                r.getRedemptionStatus(),
                campaign != null ? campaign.getQuotaTrigger() : null,
                confirmsOnApproval(payment, r),
                effectiveDeadline(r),
                isFrozen(r));
    }

    /** PENDING ally benefits of a membership with one of {@code allyIds} (validator, information only). */
    public List<PendingBenefitDto> pendingAllyBenefits(Long membershipId, Collection<Long> allyIds) {
        if (membershipId == null || allyIds == null || allyIds.isEmpty()) {
            return List.of();
        }
        return repository.findPendingAllyBenefits(membershipId, allyIds).stream()
                .map(r -> {
                    Promotion p = r.getPromotion();
                    Campaign campaign = r.getCampaign();
                    return new PendingBenefitDto(
                            DisplayRefs.ref(p),
                            DisplayRefs.ref(campaign),
                            PromotionService.allyProductRef(p.getAllyProduct()),
                            p.getBenefitType(),
                            p.getBenefitPct(),
                            p.getBenefitAmount(),
                            PromotionService.currencyCode(p.getBenefitCurrency()),
                            p.getBenefitQuantity(),
                            campaign != null ? campaign.getQuotaTrigger() : null,
                            effectiveDeadline(r),
                            isFrozen(r));
                })
                .toList();
    }

    /**
     * The member's ally benefits whose slot is still PENDING, as voucher-like
     * entries ({@code GET /v1/me/benefit-grants?includePending=true}):
     * {@code uuid} {@code null}, status PENDING_CONFIRMATION, expiry = the
     * slot's effective deadline. Nothing to redeem.
     */
    public List<MembershipBenefitGrantDto> pendingGrantEntries(Long membershipId) {
        if (membershipId == null) {
            return List.of();
        }
        return repository.findPendingAllyBenefitsOfMembership(membershipId).stream()
                .map(r -> {
                    Promotion p = r.getPromotion();
                    return new MembershipBenefitGrantDto(
                            null,
                            DisplayRefs.ref(p),
                            DisplayRefs.ref(r.getCampaign()),
                            DisplayRefs.ref(p.getAlly()),
                            PromotionService.allyProductRef(p.getAllyProduct()),
                            p.getBenefitType(),
                            p.getBenefitPct(),
                            p.getBenefitAmount(),
                            DisplayRefs.ref(p.getBenefitCurrency()),
                            PromotionService.currencyCode(p.getBenefitCurrency()),
                            p.getBenefitQuantity(),
                            p.getBenefitQuantity(),
                            GrantStatus.PENDING_CONFIRMATION,
                            null,
                            effectiveDeadline(r),
                            null);
                })
                .toList();
    }

    private static Membership membershipOf(PromotionRedemption r) {
        MembershipPromotion mp = r != null ? r.getMembershipPromotion() : null;
        return mp != null ? mp.getMembership() : null;
    }
}
