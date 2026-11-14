package com.fenixcore.optibienestar360.modules.campaign;

import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.modules.campaign.dto.CampaignDto;
import com.fenixcore.optibienestar360.modules.campaign.dto.CampaignQuotaDto;
import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
import com.fenixcore.optibienestar360.modules.campaign.service.CampaignService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT of the campaign lifecycle endpoints (V256, hub ADR 0038): one granular
 * permission per action and the lifecycle/quota fields on the wire.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class AdminCampaignLifecycleControllerIT {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private CampaignService campaignService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private RequestPostProcessor principal(String... authorities) {
        CustomUserDetails p = CustomUserDetails.fromJwt(UUID.randomUUID(), "jti", "es",
                List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList());
        return authentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }

    private static CampaignDto dto(UUID uuid, Campaign.LifecycleStatus status, Campaign.CloseReason reason,
                                   Integer max, int occupied) {
        OffsetDateTime now = OffsetDateTime.now();
        return new CampaignDto(uuid, "Navidad", null, now.minusDays(1), null, true,
                Campaign.CampaignScope.ALL, Campaign.CampaignMode.GENERAL, false, false,
                null, null, null, null, null, null, true, List.of(),
                status, List.of(Campaign.CloseCondition.QUOTA_FILLED), max, occupied, occupied,
                max == null ? null : max - occupied,
                reason == null ? null : now, reason, null, null, null, null,
                Campaign.QuotaTrigger.ON_ENROLLMENT, null, Campaign.UsageWindow.CAMPAIGN_DATES, null, 0,
                null, false);
    }

    @Test
    void close_requiresCampaignClose() throws Exception {
        UUID uuid = UUID.randomUUID();
        mockMvc.perform(post("/v1/admin/campaigns/{uuid}/close", uuid).with(principal("CAMPAIGN_UPDATE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cancelPending\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void close_returnsTheClosedCampaign() throws Exception {
        UUID uuid = UUID.randomUUID();
        when(campaignService.close(eq(uuid), any(), any()))
                .thenReturn(dto(uuid, Campaign.LifecycleStatus.CLOSED, Campaign.CloseReason.MANUAL, 10, 4));

        mockMvc.perform(post("/v1/admin/campaigns/{uuid}/close", uuid).with(principal("CAMPAIGN_CLOSE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cancelPending\":true,\"note\":\"Fin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lifecycleStatus").value("CLOSED"))
                .andExpect(jsonPath("$.closedReason").value("MANUAL"))
                .andExpect(jsonPath("$.maxRedemptions").value(10))
                .andExpect(jsonPath("$.freeSlots").value(6))
                .andExpect(jsonPath("$.closeConditions[0]").value("QUOTA_FILLED"));
    }

    @Test
    void pauseAndResume_requireCampaignPause() throws Exception {
        UUID uuid = UUID.randomUUID();
        mockMvc.perform(post("/v1/admin/campaigns/{uuid}/pause", uuid).with(principal("CAMPAIGN_CLOSE")))
                .andExpect(status().isForbidden());

        when(campaignService.pause(uuid)).thenReturn(dto(uuid, Campaign.LifecycleStatus.PAUSED, null, null, 0));
        mockMvc.perform(post("/v1/admin/campaigns/{uuid}/pause", uuid).with(principal("CAMPAIGN_PAUSE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lifecycleStatus").value("PAUSED"))
                .andExpect(jsonPath("$.endsAt").isEmpty())
                .andExpect(jsonPath("$.freeSlots").isEmpty());

        when(campaignService.resume(uuid)).thenReturn(dto(uuid, Campaign.LifecycleStatus.ACTIVE, null, null, 0));
        mockMvc.perform(post("/v1/admin/campaigns/{uuid}/resume", uuid).with(principal("CAMPAIGN_PAUSE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lifecycleStatus").value("ACTIVE"));
    }

    @Test
    void reopen_requiresCampaignReopen_andANotEnlargingRequestIs422() throws Exception {
        UUID uuid = UUID.randomUUID();
        mockMvc.perform(post("/v1/admin/campaigns/{uuid}/reopen", uuid).with(principal("CAMPAIGN_CLOSE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"maxRedemptions\":20}"))
                .andExpect(status().isForbidden());

        when(campaignService.reopen(eq(uuid), any()))
                .thenThrow(new IllegalArgumentException("campaign.reopen.not_enlarged"));
        mockMvc.perform(post("/v1/admin/campaigns/{uuid}/reopen", uuid).with(principal("CAMPAIGN_REOPEN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"maxRedemptions\":5}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void activate_requiresCampaignUpdate() throws Exception {
        UUID uuid = UUID.randomUUID();
        mockMvc.perform(post("/v1/admin/campaigns/{uuid}/activate", uuid).with(principal("CAMPAIGN_PAUSE")))
                .andExpect(status().isForbidden());

        when(campaignService.activate(uuid)).thenReturn(dto(uuid, Campaign.LifecycleStatus.ACTIVE, null, 5, 0));
        mockMvc.perform(post("/v1/admin/campaigns/{uuid}/activate", uuid).with(principal("CAMPAIGN_UPDATE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lifecycleStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.freeSlots").value(5));
    }

    @Test
    void quota_requiresViewAll_andExposesTheCounters() throws Exception {
        UUID uuid = UUID.randomUUID();
        mockMvc.perform(get("/v1/admin/campaigns/{uuid}/quota", uuid).with(principal("CAMPAIGN_CLOSE")))
                .andExpect(status().isForbidden());

        UUID promotionUuid = UUID.randomUUID();
        when(campaignService.quota(uuid)).thenReturn(new CampaignQuotaDto(10, 3, 2, 5, 1, 0, List.of(
                new CampaignQuotaDto.PromotionQuota(new DisplayRef(promotionUuid, null, "Navidad 20%"),
                        true, 4, 2, 1, 1, 1, 0))));

        mockMvc.perform(get("/v1/admin/campaigns/{uuid}/quota", uuid).with(principal("CAMPAIGN_VIEW_ALL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxRedemptions").value(10))
                .andExpect(jsonPath("$.confirmed").value(3))
                .andExpect(jsonPath("$.pending").value(2))
                .andExpect(jsonPath("$.free").value(5))
                .andExpect(jsonPath("$.expired").value(1))
                .andExpect(jsonPath("$.promotions[0].promotion_Uuid").value(promotionUuid.toString()))
                .andExpect(jsonPath("$.promotions[0].ownQuota").value(true))
                .andExpect(jsonPath("$.promotions[0].free").value(1));
    }
}
