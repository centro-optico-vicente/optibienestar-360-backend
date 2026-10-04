package com.fenixcore.optibienestar360.modules.promotion.service;

import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
import com.fenixcore.optibienestar360.modules.campaign.repository.CampaignRepository;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan;
import com.fenixcore.optibienestar360.modules.membership.repository.PlanRepository;
import com.fenixcore.optibienestar360.modules.promotion.dto.PromotionDto;
import com.fenixcore.optibienestar360.modules.promotion.dto.PromotionRequest;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.AppliesTo;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.Kind;
import com.fenixcore.optibienestar360.modules.promotion.repository.PromotionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionServiceTest {

    @Mock private PromotionRepository repository;
    @Mock private CampaignRepository campaignRepository;
    @Mock private PlanRepository planRepository;

    private PromotionService service() {
        return new PromotionService(repository, campaignRepository, planRepository);
    }

    private static Campaign campaign() {
        Campaign c = new Campaign();
        c.setUuid(UUID.randomUUID());
        c.setName("Octubre");
        return c;
    }

    private static PromotionRequest request(Kind kind, AppliesTo appliesTo, boolean requiresCode,
                                            boolean acceptsMemberCode, BigDecimal rewardPct, Integer rewardCycles,
                                            List<UUID> planUuids) {
        return new PromotionRequest("Inscripción 50%", null, kind, new BigDecimal("50.00"), appliesTo,
                3, false, 100, requiresCode, false, acceptsMemberCode, false, rewardPct, rewardCycles, planUuids);
    }

    @Test
    void create_persistsPromotionUnderCampaign_withEligiblePlans() {
        Campaign campaign = campaign();
        Plan plan = new Plan();
        plan.setUuid(UUID.randomUUID());
        plan.setName("Individual");
        when(campaignRepository.findByUuid(campaign.getUuid())).thenReturn(Optional.of(campaign));
        when(planRepository.findByUuid(plan.getUuid())).thenReturn(Optional.of(plan));
        when(repository.save(any(Promotion.class))).thenAnswer(inv -> inv.getArgument(0));

        PromotionDto dto = service().create(campaign.getUuid(),
                request(Kind.ACQUISITION, AppliesTo.BOTH, true, true, new BigDecimal("100.00"), 1, List.of(plan.getUuid())));

        ArgumentCaptor<Promotion> saved = ArgumentCaptor.forClass(Promotion.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getCampaign()).isSameAs(campaign);
        assertThat(saved.getValue().getPlans()).containsExactly(plan);
        assertThat(saved.getValue().getReferrerRewardPct()).isEqualByComparingTo("100.00");
        assertThat(dto.plans()).hasSize(1);
    }

    @Test
    void create_rejectsRecoveryOnInscription() {
        Campaign campaign = campaign();
        when(campaignRepository.findByUuid(campaign.getUuid())).thenReturn(Optional.of(campaign));

        assertThatThrownBy(() -> service().create(campaign.getUuid(),
                request(Kind.RECOVERY, AppliesTo.BOTH, false, false, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("promotion.recovery.monthly_only");
        verify(repository, never()).save(any());
    }

    @Test
    void create_rejectsRequiredCodeWithoutAcceptedOwner() {
        Campaign campaign = campaign();
        when(campaignRepository.findByUuid(campaign.getUuid())).thenReturn(Optional.of(campaign));

        assertThatThrownBy(() -> service().create(campaign.getUuid(),
                request(Kind.ACQUISITION, AppliesTo.MONTHLY, true, false, null, null, null)))
                .hasMessage("promotion.code.owner_required");
    }

    @Test
    void create_rejectsReferrerRewardWithoutMemberCodes() {
        Campaign campaign = campaign();
        when(campaignRepository.findByUuid(campaign.getUuid())).thenReturn(Optional.of(campaign));

        assertThatThrownBy(() -> service().create(campaign.getUuid(),
                request(Kind.ACQUISITION, AppliesTo.MONTHLY, false, false, new BigDecimal("50.00"), 1, null)))
                .hasMessage("promotion.referrer_reward.member_code_required");
    }

    @Test
    void cloneInto_copiesPromotionsToTheRelaunchedCampaign_withCountersReset() {
        Campaign source = campaign();
        Campaign target = campaign();
        Promotion original = new Promotion();
        original.setCampaign(source);
        original.setName("3 meses 20%");
        original.setKind(Kind.ACQUISITION);
        original.setDiscountPct(new BigDecimal("20.00"));
        original.setAppliesTo(AppliesTo.MONTHLY);
        original.setCycles(3);
        original.setRedemptionsCount(42);
        original.setPlans(Set.of(new Plan()));
        when(repository.findByCampaignAndActiveTrueOrderByCreatedAtAsc(source)).thenReturn(List.of(original));

        service().cloneInto(source, target);

        ArgumentCaptor<Promotion> saved = ArgumentCaptor.forClass(Promotion.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getCampaign()).isSameAs(target);
        assertThat(saved.getValue().getCycles()).isEqualTo(3);
        assertThat(saved.getValue().getRedemptionsCount()).isZero();
        assertThat(saved.getValue().getPlans()).hasSize(1);
    }
}
