package com.fenixcore.optibienestar360.modules.promotion.service;

import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership.LifecycleStatus;
import com.fenixcore.optibienestar360.modules.promotion.entity.MembershipPromotion.Status;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.Kind;
import com.fenixcore.optibienestar360.modules.promotion.repository.MembershipPromotionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Ends a membership's promotion when the membership changes state (hub ADR
 * 0018): any promotion on cancellation; an ACQUISITION promotion also on any
 * delay (SUSPENDED or EXPIRED). RECOVERY promotions survive the delay — they
 * exist precisely for delayed memberships. An ended promotion frees its
 * campaign slot when no discount was applied yet (hub ADR 0038 §5).
 */
@Component
@RequiredArgsConstructor
public class PromotionLifecycle {

    private final MembershipPromotionRepository membershipPromotionRepository;
    private final PromotionRedemptionService redemptionService;

    public void onStatusChanged(Membership membership, LifecycleStatus newStatus) {
        if (membership.getId() == null) return;
        membershipPromotionRepository.findOngoing(membership.getId()).ifPresent(mp -> {
            if (newStatus == LifecycleStatus.CANCELED) {
                PromotionAssignmentService.end(mp, Status.CANCELED, "Membresía cancelada");
                redemptionService.releaseIfUnused(mp);
            } else if ((newStatus == LifecycleStatus.SUSPENDED || newStatus == LifecycleStatus.EXPIRED)
                    && mp.getPromotion().getKind() == Kind.ACQUISITION) {
                PromotionAssignmentService.end(mp, Status.CANCELED, "Atraso en el pago");
                redemptionService.releaseIfUnused(mp);
            }
        });
    }
}
