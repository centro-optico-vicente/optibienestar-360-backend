package com.fenixcore.optibienestar360.modules.promotion.service;

import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership.LifecycleStatus;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipRepository;
import com.fenixcore.optibienestar360.modules.membership.repository.PlanRepository;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promotion.entity.MembershipPromotion;
import com.fenixcore.optibienestar360.modules.promotion.entity.MembershipPromotion.Origin;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.AppliesTo;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.Kind;
import com.fenixcore.optibienestar360.modules.promotion.repository.MembershipPromotionRepository;
import com.fenixcore.optibienestar360.modules.promotion.repository.PromotionRepository;
import com.fenixcore.optibienestar360.modules.promotion.service.PromotionCodeResolver.CodeOwner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionAssignmentServiceTest {

    @Mock private PromotionRepository promotionRepository;
    @Mock private MembershipPromotionRepository membershipPromotionRepository;
    @Mock private MembershipRepository membershipRepository;
    @Mock private PlanRepository planRepository;
    @Mock private UserRepository userRepository;
    @Mock private PromotionCodeResolver codeResolver;
    @Mock private com.fenixcore.optibienestar360.modules.membership.service.MembershipChargeService chargeService;
    @Mock private ReferrerRewardService referrerRewardService;

    private PromotionAssignmentService service() {
        return new PromotionAssignmentService(promotionRepository, membershipPromotionRepository,
                membershipRepository, planRepository, userRepository, codeResolver, chargeService, referrerRewardService);
    }

    private Plan plan(long id) {
        Plan p = new Plan();
        p.setId(id);
        return p;
    }

    private Membership membership(Plan plan, LifecycleStatus status) {
        Membership m = new Membership();
        m.setId(10L);
        m.setUuid(UUID.randomUUID());
        m.setPlan(plan);
        m.setMember(new Member());
        m.setStatus(status.name());
        return m;
    }

    private Promotion promotion(Kind kind, boolean running) {
        Campaign campaign = new Campaign();
        campaign.setEnabled(true);
        OffsetDateTime now = OffsetDateTime.now();
        campaign.setStartsAt(running ? now.minusDays(1) : now.plusDays(1));
        campaign.setEndsAt(running ? now.plusDays(30) : now.plusDays(60));
        Promotion p = new Promotion();
        p.setUuid(UUID.randomUUID());
        p.setCampaign(campaign);
        p.setKind(kind);
        p.setDiscountPct(new BigDecimal("20.00"));
        p.setAppliesTo(AppliesTo.MONTHLY);
        p.setCycles(3);
        lenient().when(promotionRepository.findByUuid(p.getUuid())).thenReturn(Optional.of(p));
        return p;
    }

    private void stubSave() {
        when(membershipPromotionRepository.save(any(MembershipPromotion.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void assign_recordsPromotion_withCycles_codeOwner_andCountsTheRedemption() {
        Plan plan = plan(1L);
        Membership membership = membership(plan, LifecycleStatus.ACTIVE);
        Promotion promotion = promotion(Kind.ACQUISITION, true);
        promotion.setRequiresCode(true);
        promotion.setAcceptsPromoterCode(true);
        Promoter promoter = new Promoter();
        when(codeResolver.resolve("vicente")).thenReturn(Optional.of(new CodeOwner("VICENTE", promoter, null, null)));
        stubSave();

        MembershipPromotion mp = service().assign(membership, promotion.getUuid(), "vicente", Origin.ENROLLMENT, null);

        assertThat(mp.getCyclesRemaining()).isEqualTo(3);
        assertThat(mp.getCodeUsed()).isEqualTo("VICENTE");
        assertThat(mp.getCodeOwnerPromoter()).isSameAs(promoter);
        assertThat(mp.getStatus()).isEqualTo("ACTIVE");
        assertThat(promotion.getRedemptionsCount()).isEqualTo(1);
    }

    @Test
    void assign_rejectsWhenTheCampaignIsNotRunning() {
        Promotion promotion = promotion(Kind.ACQUISITION, false);

        assertThatThrownBy(() -> service().assign(membership(plan(1L), LifecycleStatus.ACTIVE),
                promotion.getUuid(), null, Origin.ADMIN, null))
                .hasMessage("promotion.campaign.not_running");
    }

    @Test
    void assign_rejectsAPlanOutsideTheEligibleList() {
        Promotion promotion = promotion(Kind.ACQUISITION, true);
        promotion.setPlans(Set.of(plan(2L)));

        assertThatThrownBy(() -> service().assign(membership(plan(1L), LifecycleStatus.ACTIVE),
                promotion.getUuid(), null, Origin.ADMIN, null))
                .hasMessage("promotion.plan.not_eligible");
    }

    @Test
    void assign_acquisitionRequiresAnActiveMembership_recoveryADelayedOne() {
        Promotion acquisition = promotion(Kind.ACQUISITION, true);
        assertThatThrownBy(() -> service().assign(membership(plan(1L), LifecycleStatus.SUSPENDED),
                acquisition.getUuid(), null, Origin.ADMIN, null))
                .hasMessage("promotion.acquisition.requires_active_membership");

        Promotion recovery = promotion(Kind.RECOVERY, true);
        assertThatThrownBy(() -> service().assign(membership(plan(1L), LifecycleStatus.ACTIVE),
                recovery.getUuid(), null, Origin.ADMIN, null))
                .hasMessage("promotion.recovery.requires_delayed_membership");

        stubSave();
        Membership expired = membership(plan(1L), LifecycleStatus.EXPIRED);
        assertThat(service().assign(expired, recovery.getUuid(), null, Origin.ADMIN, null)).isNotNull();
        verify(chargeService).repriceOpenCharges(expired);
    }

    @Test
    void assign_rejectsWhenTheCapIsReached() {
        Promotion promotion = promotion(Kind.ACQUISITION, true);
        promotion.setMaxRedemptions(5);
        promotion.setRedemptionsCount(5);

        assertThatThrownBy(() -> service().assign(membership(plan(1L), LifecycleStatus.ACTIVE),
                promotion.getUuid(), null, Origin.ADMIN, null))
                .hasMessage("promotion.max_redemptions_reached");
    }

    @Test
    void assign_rejectsASecondActivePromotion() {
        Promotion promotion = promotion(Kind.ACQUISITION, true);
        when(membershipPromotionRepository.findOngoing(10L)).thenReturn(Optional.of(new MembershipPromotion()));

        assertThatThrownBy(() -> service().assign(membership(plan(1L), LifecycleStatus.ACTIVE),
                promotion.getUuid(), null, Origin.ADMIN, null))
                .hasMessage("promotion.membership.already_has_one");
        verify(membershipPromotionRepository, never()).save(any());
    }

    @Test
    void assign_enforcesTheCodeRules() {
        Promotion promotion = promotion(Kind.ACQUISITION, true);
        promotion.setRequiresCode(true);
        promotion.setAcceptsMemberCode(true);

        assertThatThrownBy(() -> service().assign(membership(plan(1L), LifecycleStatus.ACTIVE),
                promotion.getUuid(), null, Origin.ADMIN, null))
                .hasMessage("promotion.code.required");

        when(codeResolver.resolve("PROMO1")).thenReturn(Optional.of(new CodeOwner("PROMO1", new Promoter(), null, null)));
        assertThatThrownBy(() -> service().assign(membership(plan(1L), LifecycleStatus.ACTIVE),
                promotion.getUuid(), "PROMO1", Origin.ADMIN, null))
                .hasMessage("promotion.code.owner_not_accepted");

        when(codeResolver.resolve("NOPE")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().assign(membership(plan(1L), LifecycleStatus.ACTIVE),
                promotion.getUuid(), "NOPE", Origin.ADMIN, null))
                .hasMessage("promotion.code.not_found");
    }
}
