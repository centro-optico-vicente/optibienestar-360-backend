package com.fenixcore.optibienestar360.modules.promotion.service;

import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.core.display.DisplayRefs;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership.LifecycleStatus;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipRepository;
import com.fenixcore.optibienestar360.modules.membership.repository.PlanRepository;
import com.fenixcore.optibienestar360.modules.promotion.dto.MembershipPromotionDto;
import com.fenixcore.optibienestar360.modules.promotion.dto.MembershipPromotionStatusDto;
import com.fenixcore.optibienestar360.modules.promotion.dto.PromotionDto;
import com.fenixcore.optibienestar360.modules.promotion.entity.MembershipPromotion;
import com.fenixcore.optibienestar360.modules.promotion.entity.MembershipPromotion.Origin;
import com.fenixcore.optibienestar360.modules.promotion.entity.MembershipPromotion.Status;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.Kind;
import com.fenixcore.optibienestar360.modules.promotion.repository.MembershipPromotionRepository;
import com.fenixcore.optibienestar360.modules.promotion.repository.PromotionRepository;
import com.fenixcore.optibienestar360.modules.promotion.service.PromotionCodeResolver.CodeOwner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Applies, lists and ends the promotion of a membership (hub ADR 0018).
 * ACQUISITION promotions only fit an ACTIVE membership (a new enrollment is
 * ACTIVE); RECOVERY ones only a delayed (SUSPENDED/EXPIRED) membership.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PromotionAssignmentService {

    private final PromotionRepository promotionRepository;
    private final MembershipPromotionRepository membershipPromotionRepository;
    private final MembershipRepository membershipRepository;
    private final PlanRepository planRepository;
    private final UserRepository userRepository;
    private final PromotionCodeResolver codeResolver;
    private final com.fenixcore.optibienestar360.modules.membership.service.MembershipChargeService chargeService;

    // ─── Read ───────────────────────────────────────────────────────────────

    public MembershipPromotionStatusDto current(UUID membershipUuid) {
        Membership membership = findMembership(membershipUuid);
        return membershipPromotionRepository.findOngoing(membership.getId())
                .map(mp -> new MembershipPromotionStatusDto(true, toDto(mp)))
                .orElseGet(() -> new MembershipPromotionStatusDto(false, null));
    }

    /** Promotions that could be applied to this membership right now. */
    public List<PromotionDto> options(UUID membershipUuid) {
        Membership membership = findMembership(membershipUuid);
        return offeredFor(membership.getPlan(), membership.getStatus());
    }

    /** ACQUISITION promotions offered for a new enrollment on {@code planUuid}. */
    public List<PromotionDto> optionsForEnrollment(UUID planUuid) {
        Plan plan = planRepository.findByUuid(planUuid)
                .orElseThrow(() -> new NoSuchElementException("plan.not_found"));
        return offeredFor(plan, LifecycleStatus.ACTIVE.name());
    }

    private List<PromotionDto> offeredFor(Plan plan, String membershipStatus) {
        return promotionRepository.findOffered(OffsetDateTime.now()).stream()
                .filter(p -> fitsPlan(p, plan) && fitsStatus(p, membershipStatus) && hasRoom(p))
                .map(PromotionService::toDto)
                .toList();
    }

    // ─── Write ──────────────────────────────────────────────────────────────

    @Transactional
    public MembershipPromotionDto assignToExisting(UUID membershipUuid, UUID promotionUuid, String code, UUID actorUuid) {
        Membership membership = findMembership(membershipUuid);
        return toDto(assign(membership, promotionUuid, code, Origin.ADMIN, actorUuid));
    }

    /**
     * Validates and records a promotion on {@code membership}. Called inside
     * the enrollment transaction too, so an invalid promotion rolls back the
     * whole enrollment.
     */
    @Transactional
    public MembershipPromotion assign(Membership membership, UUID promotionUuid, String code,
                                      Origin origin, UUID actorUuid) {
        Promotion promotion = promotionRepository.findByUuid(promotionUuid)
                .filter(Promotion::isActive)
                .orElseThrow(() -> new NoSuchElementException("promotion.not_found"));

        OffsetDateTime now = OffsetDateTime.now();
        var campaign = promotion.getCampaign();
        if (!campaign.isActive() || !campaign.isEnabled()
                || now.isBefore(campaign.getStartsAt()) || now.isAfter(campaign.getEndsAt())) {
            throw new IllegalArgumentException("promotion.campaign.not_running");
        }
        if (!fitsPlan(promotion, membership.getPlan())) {
            throw new IllegalArgumentException("promotion.plan.not_eligible");
        }
        if (!fitsStatus(promotion, membership.getStatus())) {
            throw new IllegalArgumentException(promotion.getKind() == Kind.RECOVERY
                    ? "promotion.recovery.requires_delayed_membership"
                    : "promotion.acquisition.requires_active_membership");
        }
        if (!hasRoom(promotion)) {
            throw new IllegalArgumentException("promotion.max_redemptions_reached");
        }
        if (membership.getId() != null && membershipPromotionRepository.findOngoing(membership.getId()).isPresent()) {
            throw new IllegalArgumentException("promotion.membership.already_has_one");
        }

        CodeOwner owner = null;
        if (code != null && !code.isBlank()) {
            owner = codeResolver.resolve(code)
                    .orElseThrow(() -> new IllegalArgumentException("promotion.code.not_found"));
            if (!owner.isAcceptedBy(promotion)) {
                throw new IllegalArgumentException("promotion.code.owner_not_accepted");
            }
        } else if (promotion.isRequiresCode()) {
            throw new IllegalArgumentException("promotion.code.required");
        }

        MembershipPromotion mp = new MembershipPromotion();
        mp.setMembership(membership);
        mp.setPromotion(promotion);
        mp.setOrigin(origin);
        mp.setCyclesRemaining(promotion.getCycles());
        mp.setAssignedAt(Instant.now());
        mp.setAssignedBy(actorUuid == null ? null : userRepository.findByUuid(actorUuid).orElse(null));
        mp.setStatus(Status.ACTIVE.name());
        if (owner != null) {
            mp.setCodeUsed(owner.code());
            mp.setCodeOwnerPromoter(owner.promoter());
            mp.setCodeOwnerMember(owner.member());
            mp.setCodeOwnerAlly(owner.ally());
        }
        promotion.setRedemptionsCount(promotion.getRedemptionsCount() + 1);
        MembershipPromotion saved = membershipPromotionRepository.save(mp);
        if (promotion.getKind() == Kind.RECOVERY) {
            // The overdue months are already billed: discount them too.
            chargeService.repriceOpenCharges(membership);
        }
        return saved;
    }

    @Transactional
    public MembershipPromotionDto cancel(UUID membershipUuid, String reason) {
        Membership membership = findMembership(membershipUuid);
        MembershipPromotion mp = membershipPromotionRepository.findOngoing(membership.getId())
                .orElseThrow(() -> new NoSuchElementException("promotion.membership.none_active"));
        end(mp, Status.CANCELED, reason);
        return toDto(mp);
    }

    /** Closes an ongoing promotion (cancelled, or consumed when its cycles run out). */
    public static void end(MembershipPromotion mp, Status status, String reason) {
        mp.setStatus(status.name());
        mp.setEndedAt(Instant.now());
        mp.setEndedReason(reason);
    }

    // ─── Rules ──────────────────────────────────────────────────────────────

    static boolean fitsPlan(Promotion promotion, Plan plan) {
        return promotion.getPlans().isEmpty()
                || promotion.getPlans().stream().anyMatch(p -> p.getId() != null && p.getId().equals(plan.getId()));
    }

    static boolean fitsStatus(Promotion promotion, String membershipStatus) {
        boolean delayed = LifecycleStatus.SUSPENDED.name().equals(membershipStatus)
                || LifecycleStatus.EXPIRED.name().equals(membershipStatus);
        return promotion.getKind() == Kind.RECOVERY
                ? delayed
                : LifecycleStatus.ACTIVE.name().equals(membershipStatus);
    }

    static boolean hasRoom(Promotion promotion) {
        return promotion.getMaxRedemptions() == null || promotion.getRedemptionsCount() < promotion.getMaxRedemptions();
    }

    private Membership findMembership(UUID uuid) {
        return membershipRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("membership.not_found"));
    }

    static MembershipPromotionDto toDto(MembershipPromotion mp) {
        Promotion p = mp.getPromotion();
        String ownerType = null;
        DisplayRef owner = null;
        if (mp.getCodeOwnerPromoter() != null) {
            ownerType = "PROMOTER";
            owner = DisplayRefs.ref(mp.getCodeOwnerPromoter());
        } else if (mp.getCodeOwnerMember() != null) {
            ownerType = "MEMBER";
            owner = DisplayRefs.ref(mp.getCodeOwnerMember());
        } else if (mp.getCodeOwnerAlly() != null) {
            ownerType = "ALLY";
            owner = DisplayRefs.ref(mp.getCodeOwnerAlly());
        }
        return new MembershipPromotionDto(
                mp.getUuid(),
                DisplayRefs.ref(p),
                DisplayRefs.ref(p.getCampaign()),
                p.getKind(),
                p.getDiscountPct(),
                p.getAppliesTo(),
                mp.getCyclesRemaining(),
                mp.isInscriptionApplied(),
                mp.getCodeUsed(),
                ownerType,
                owner,
                mp.getOrigin().name(),
                mp.getStatus(),
                mp.getAssignedAt(),
                mp.getEndedAt(),
                mp.getEndedReason());
    }
}
