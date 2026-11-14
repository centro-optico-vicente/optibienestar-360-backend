package com.fenixcore.optibienestar360.modules.promotion.service;

import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser.AllyRole;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyUserRepository;
import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
import com.fenixcore.optibienestar360.modules.campaign.repository.CampaignRepository;
import com.fenixcore.optibienestar360.modules.promotion.dto.AllyPromotionRequest;
import com.fenixcore.optibienestar360.modules.promotion.dto.PromotionRequest;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.BenefitKind;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.BenefitType;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.Kind;
import com.fenixcore.optibienestar360.modules.promotion.repository.PromotionRedemptionRepository;
import com.fenixcore.optibienestar360.modules.promotion.repository.PromotionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Ally portal partnership in campaigns (V257, hub ADR 0038 §8). */
@ExtendWith(MockitoExtension.class)
class AllyPartnershipServiceTest {

    @Mock private AllyUserRepository allyUserRepository;
    @Mock private CampaignRepository campaignRepository;
    @Mock private PromotionRepository promotionRepository;
    @Mock private PromotionRedemptionRepository redemptionRepository;
    @Mock private PromotionService promotionService;

    private final UUID actor = UUID.randomUUID();

    private AllyPartnershipService service() {
        return new AllyPartnershipService(allyUserRepository, campaignRepository, promotionRepository,
                redemptionRepository, promotionService);
    }

    private Ally callerAlly(AllyRole role) {
        Ally ally = new Ally();
        ally.setId(1L);
        ally.setUuid(UUID.randomUUID());
        AllyUser operator = new AllyUser();
        operator.setAlly(ally);
        operator.setAllyRole(role);
        when(allyUserRepository.findActiveByUserUuid(actor)).thenReturn(List.of(operator));
        return ally;
    }

    private Campaign campaign(boolean partnership, Ally headerAlly) {
        Campaign c = new Campaign();
        c.setId(9L);
        c.setUuid(UUID.randomUUID());
        c.setName("Día de la Madre");
        c.setStartsAt(OffsetDateTime.now().minusDays(1));
        c.setAllowsAllyPartnership(partnership);
        c.setAlly(headerAlly);
        when(campaignRepository.findByUuid(c.getUuid())).thenReturn(Optional.of(c));
        return c;
    }

    private static AllyPromotionRequest request() {
        return new AllyPromotionRequest("Consulta gratis", null, UUID.randomUUID(), BenefitType.FREE,
                null, null, null, 1, false, null, null);
    }

    @Test
    void create_inACampaignOpenToPartnership_isAnAllyBenefitOfTheCaller_markedAsProposedByTheAlly() {
        Ally ally = callerAlly(AllyRole.STAFF);
        Campaign campaign = campaign(true, null);
        when(promotionRepository.save(any(Promotion.class))).thenAnswer(inv -> inv.getArgument(0));

        service().create(actor, null, campaign.getUuid(), request());

        ArgumentCaptor<PromotionRequest> req = ArgumentCaptor.forClass(PromotionRequest.class);
        ArgumentCaptor<Promotion> promotion = ArgumentCaptor.forClass(Promotion.class);
        verify(promotionService).apply(promotion.capture(), req.capture(), eq(ally));
        assertThat(req.getValue().benefitKind()).isEqualTo(BenefitKind.ALLY_BENEFIT);
        assertThat(req.getValue().kind()).isEqualTo(Kind.ACQUISITION);
        assertThat(req.getValue().allyUuid()).isNull();
        assertThat(promotion.getValue().getCampaign()).isSameAs(campaign);
        assertThat(promotion.getValue().isCreatedByAlly()).isTrue();
    }

    @Test
    void create_isRejected_whenTheCampaignIsNotOpenToTheCaller() {
        callerAlly(AllyRole.STAFF);
        Campaign closedToPartnership = campaign(false, null);
        assertThatThrownBy(() -> service().create(actor, null, closedToPartnership.getUuid(), request()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ally_campaign.not_open");

        Ally other = new Ally();
        other.setId(2L);
        Campaign exclusiveToOther = campaign(true, other);
        assertThatThrownBy(() -> service().create(actor, null, exclusiveToOther.getUuid(), request()))
                .hasMessage("ally_campaign.not_open");

        Campaign paused = campaign(true, null);
        paused.setLifecycleStatus(Campaign.LifecycleStatus.PAUSED);
        assertThatThrownBy(() -> service().create(actor, null, paused.getUuid(), request()))
                .hasMessage("ally_campaign.not_open");
        verify(promotionRepository, never()).save(any());
    }

    @Test
    void create_byAViewer_isForbidden() {
        callerAlly(AllyRole.VIEWER);

        assertThatThrownBy(() -> service().create(actor, null, UUID.randomUUID(), request()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void update_ofAPromotionThatTookASlot_is422() {
        Ally ally = callerAlly(AllyRole.OWNER);
        Promotion own = new Promotion();
        own.setId(3L);
        own.setUuid(UUID.randomUUID());
        own.setAlly(ally);
        own.setCreatedByAlly(true);
        when(promotionRepository.findByUuid(own.getUuid())).thenReturn(Optional.of(own));
        when(redemptionRepository.existsByPromotion_Id(3L)).thenReturn(true);

        assertThatThrownBy(() -> service().update(actor, null, own.getUuid(), request()))
                .hasMessage("ally_campaign.promotion.has_redemptions");
    }

    @Test
    void listCampaigns_flagsTheOnesExclusiveToTheCaller() {
        Ally ally = callerAlly(AllyRole.STAFF);
        Campaign shared = new Campaign();
        shared.setUuid(UUID.randomUUID());
        Campaign exclusive = new Campaign();
        exclusive.setUuid(UUID.randomUUID());
        exclusive.setAlly(ally);
        when(campaignRepository.findOpenToAllyPartnership(Campaign.LifecycleStatus.ACTIVE, 1L))
                .thenReturn(List.of(shared, exclusive));

        var campaigns = service().campaigns(actor, null);

        assertThat(campaigns).hasSize(2);
        assertThat(campaigns.get(0).exclusive()).isFalse();
        assertThat(campaigns.get(1).exclusive()).isTrue();
    }
}
