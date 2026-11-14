package com.fenixcore.optibienestar360.modules.campaign.service;

import com.fenixcore.optibienestar360.modules.campaign.dto.CampaignDto;
import com.fenixcore.optibienestar360.modules.campaign.dto.CampaignRelaunchRequest;
import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
import com.fenixcore.optibienestar360.modules.campaign.repository.CampaignPromoterRepository;
import com.fenixcore.optibienestar360.modules.campaign.repository.CampaignRepository;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionBonusRuleRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionTierRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.HierarchyOverrideTierRepository;
import com.fenixcore.optibienestar360.modules.promotion.service.PromotionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Relaunch of a campaign as a new edition, open-ended or not (hub ADR 0038 §2). */
@ExtendWith(MockitoExtension.class)
class CampaignServiceRelaunchTest {

    @Mock private CampaignRepository repository;
    @Mock private CampaignPromoterRepository campaignPromoterRepository;
    @Mock private CommissionTierRepository commissionTierRepository;
    @Mock private CommissionBonusRuleRepository bonusRuleRepository;
    @Mock private HierarchyOverrideTierRepository hierarchyOverrideTierRepository;
    @Mock private PromotionService promotionService;

    @InjectMocks private CampaignService service;

    private Campaign source() {
        Campaign c = new Campaign();
        c.setId(1L);
        c.setUuid(UUID.randomUUID());
        c.setName("Navidad");
        c.setStartsAt(OffsetDateTime.now().minusMonths(2));
        c.setEndsAt(OffsetDateTime.now().minusMonths(1));
        c.setCloseOnEndDate(true);
        c.setCloseOnQuotaFilled(true);
        c.setMaxRedemptions(50);
        c.setRedemptionsCount(50);
        c.setConfirmedCount(50);
        c.close(Campaign.CloseReason.QUOTA_FILLED, null, null, OffsetDateTime.now().minusMonths(1));
        when(repository.findByUuid(c.getUuid())).thenReturn(Optional.of(c));
        return c;
    }

    @Test
    void relaunch_withoutEndDate_isOpenEnded_andDropsTheEndDateCondition() {
        Campaign source = source();
        when(repository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));

        CampaignDto dto = service.relaunch(source.getUuid(), new CampaignRelaunchRequest(OffsetDateTime.now(), null));

        ArgumentCaptor<Campaign> clone = ArgumentCaptor.forClass(Campaign.class);
        verify(repository).save(clone.capture());
        assertThat(clone.getValue().getEndsAt()).isNull();
        assertThat(clone.getValue().isCloseOnEndDate()).isFalse();
        assertThat(clone.getValue().isCloseOnQuotaFilled()).isTrue();
        assertThat(clone.getValue().getLifecycleStatus()).isEqualTo(Campaign.LifecycleStatus.ACTIVE);
        assertThat(clone.getValue().getRedemptionsCount()).isZero();
        assertThat(clone.getValue().getClosedAt()).isNull();
        assertThat(dto.closeConditions()).containsExactly(Campaign.CloseCondition.QUOTA_FILLED);
    }

    @Test
    void relaunch_withEndDate_keepsTheEndDateCondition_andStillChecksTheOrder() {
        Campaign source = source();
        OffsetDateTime start = OffsetDateTime.now();

        assertThatThrownBy(() -> service.relaunch(source.getUuid(), new CampaignRelaunchRequest(start, start.minusDays(1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("campaign.dates.order");

        when(repository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));
        service.relaunch(source.getUuid(), new CampaignRelaunchRequest(start, start.plusMonths(1)));
        ArgumentCaptor<Campaign> clone = ArgumentCaptor.forClass(Campaign.class);
        verify(repository).save(clone.capture());
        assertThat(clone.getValue().isCloseOnEndDate()).isTrue();
    }

    @Test
    void relaunch_withoutEndDate_isRejected_whenRulesNeedTheEnd() {
        Campaign atEnd = source();
        atEnd.setEvaluateOnlyAtEnd(true);
        atEnd.setPayOnlyAtEnd(true);
        assertThatThrownBy(() -> service.relaunch(atEnd.getUuid(), new CampaignRelaunchRequest(OffsetDateTime.now(), null)))
                .hasMessage("campaign.at_end_rules.require_ends_at");

        Campaign withCampaignBonus = source();
        CommissionBonusRule bonus = new CommissionBonusRule();
        bonus.setAccrualPeriodStrategy(CommissionBonusRule.WindowStrategy.CAMPAIGN);
        when(bonusRuleRepository.findByCampaign(withCampaignBonus)).thenReturn(List.of(bonus));
        assertThatThrownBy(() -> service.relaunch(withCampaignBonus.getUuid(),
                new CampaignRelaunchRequest(OffsetDateTime.now(), null)))
                .hasMessage("campaign.relaunch.campaign_window_rules_require_ends_at");
        verify(repository, never()).save(any());
    }
}
