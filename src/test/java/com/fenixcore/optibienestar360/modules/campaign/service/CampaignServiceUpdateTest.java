package com.fenixcore.optibienestar360.modules.campaign.service;

import com.fenixcore.optibienestar360.modules.campaign.dto.CampaignDto;
import com.fenixcore.optibienestar360.modules.campaign.dto.CampaignRequest;
import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign.LifecycleStatus;
import com.fenixcore.optibienestar360.modules.campaign.repository.CampaignPromoterRepository;
import com.fenixcore.optibienestar360.modules.campaign.repository.CampaignRepository;
import com.fenixcore.optibienestar360.modules.promotion.repository.PromotionRepository;
import com.fenixcore.optibienestar360.modules.promotion.service.PromotionRedemptionService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Admin edit of a campaign against the slot lifecycle (hub ADR 0038 §4): the
 * row is locked and re-read before the request is applied, and the request
 * never carries lifecycle, counters or close data.
 */
@ExtendWith(MockitoExtension.class)
class CampaignServiceUpdateTest {

    @Mock private CampaignRepository repository;
    @Mock private CampaignPromoterRepository campaignPromoterRepository;
    @Mock private PromotionRepository promotionRepository;
    @Mock private PromotionRedemptionService redemptionService;
    @Mock private EntityManager entityManager;

    @InjectMocks private CampaignService service;

    private Campaign soldOut() {
        Campaign c = new Campaign();
        c.setId(3L);
        c.setUuid(UUID.randomUUID());
        c.setName("Navidad");
        c.setStartsAt(OffsetDateTime.now().minusDays(5));
        c.setMaxRedemptions(10);
        c.setRedemptionsCount(10);
        c.setConfirmedCount(4);
        c.setCloseOnEndDate(false);
        c.setLifecycleStatus(LifecycleStatus.SOLD_OUT);
        when(repository.findForUpdateByUuid(c.getUuid())).thenReturn(Optional.of(c));
        return c;
    }

    private static CampaignRequest request(Campaign c, Integer max) {
        return new CampaignRequest("Navidad 2026", null, c.getStartsAt(), null, true,
                Campaign.CampaignScope.ALL, Campaign.CampaignMode.GENERAL, false, false,
                null, null, null, null, null, List.of(), List.of(), max, null, null,
                // draft = true is ignored on update: the lifecycle never comes from the request
                true, null, null, null, null, null, false);
    }

    @Test
    void update_locksAndReReadsTheRow_beforeApplyingTheRequest() {
        Campaign c = soldOut();

        service.update(c.getUuid(), request(c, 10));

        InOrder order = inOrder(repository, entityManager);
        order.verify(repository).findForUpdateByUuid(c.getUuid());
        order.verify(entityManager).refresh(c);
        verify(repository, never()).findByUuid(c.getUuid());
    }

    @Test
    void update_keepsLifecycleAndCounters_whateverTheRequestSays() {
        Campaign c = soldOut();

        CampaignDto dto = service.update(c.getUuid(), request(c, 10));

        assertThat(c.getName()).isEqualTo("Navidad 2026");
        assertThat(c.getLifecycleStatus()).isEqualTo(LifecycleStatus.SOLD_OUT);
        assertThat(c.getRedemptionsCount()).isEqualTo(10);
        assertThat(c.getConfirmedCount()).isEqualTo(4);
        assertThat(c.getClosedAt()).isNull();
        assertThat(dto.redemptionsCount()).isEqualTo(10);
    }

    @Test
    void update_raisingTheCap_freesTheCampaign_throughTheQuotaRule() {
        Campaign c = soldOut();

        service.update(c.getUuid(), request(c, 20));

        assertThat(c.getLifecycleStatus()).isEqualTo(LifecycleStatus.ACTIVE);
        assertThat(c.getRedemptionsCount()).isEqualTo(10);
    }

    @Test
    void delete_takesTheSameLock() {
        Campaign c = soldOut();

        service.delete(c.getUuid());

        assertThat(c.isActive()).isFalse();
        verify(entityManager).refresh(c);
    }
}
