package com.fenixcore.optibienestar360.modules.promotion;

import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
import com.fenixcore.optibienestar360.modules.promotion.dto.AllyPartnershipCampaignDto;
import com.fenixcore.optibienestar360.modules.promotion.service.AllyPartnershipService;
import com.fenixcore.optibienestar360.modules.promotion.service.MembershipBenefitGrantService;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT of the ally portal partnership endpoints and the member's vouchers
 * (V257, hub ADR 0038 §7-§8): one granular permission per action.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class AllyCampaignControllerIT {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private AllyPartnershipService partnershipService;

    @MockitoBean
    private MembershipBenefitGrantService grantService;

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

    private static final String BODY = """
            {"name":"Consulta gratis","allyProductUuid":"%s","benefitType":"FREE","benefitQuantity":1,"ownQuota":false}
            """.formatted(UUID.randomUUID());

    @Test
    void campaigns_requireAllyCampaignView_andExposeTheExclusiveFlag() throws Exception {
        mockMvc.perform(get("/v1/me/allies/campaigns").with(principal("ALLY_VALIDATE_MEMBER")))
                .andExpect(status().isForbidden());

        UUID campaignUuid = UUID.randomUUID();
        when(partnershipService.campaigns(any(), isNull())).thenReturn(List.of(new AllyPartnershipCampaignDto(
                campaignUuid, "Navidad", null, OffsetDateTime.now(), null, Campaign.LifecycleStatus.ACTIVE,
                100, 40, true)));

        mockMvc.perform(get("/v1/me/allies/campaigns").with(principal("ALLY_CAMPAIGN_VIEW")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].uuid").value(campaignUuid.toString()))
                .andExpect(jsonPath("$[0].lifecycleStatus").value("ACTIVE"))
                .andExpect(jsonPath("$[0].freeSlots").value(40))
                .andExpect(jsonPath("$[0].exclusive").value(true));
    }

    @Test
    void createPromotion_requiresTheCreatePermission_andANotOpenCampaignIs422() throws Exception {
        UUID campaignUuid = UUID.randomUUID();
        mockMvc.perform(post("/v1/me/allies/campaigns/{uuid}/promotions", campaignUuid)
                        .with(principal("ALLY_CAMPAIGN_VIEW"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());

        when(partnershipService.create(any(), isNull(), any(), any()))
                .thenThrow(new IllegalArgumentException("ally_campaign.not_open"));
        mockMvc.perform(post("/v1/me/allies/campaigns/{uuid}/promotions", campaignUuid)
                        .with(principal("ALLY_CAMPAIGN_PROMOTION_CREATE"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void deletePromotion_requiresTheDeletePermission() throws Exception {
        UUID promotionUuid = UUID.randomUUID();
        mockMvc.perform(delete("/v1/me/allies/promotions/{uuid}", promotionUuid)
                        .with(principal("ALLY_CAMPAIGN_PROMOTION_UPDATE")))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/v1/me/allies/promotions/{uuid}", promotionUuid)
                        .with(principal("ALLY_CAMPAIGN_PROMOTION_DELETE")))
                .andExpect(status().isNoContent());
    }

    @Test
    void myBenefitGrants_requireMemberViewOwn() throws Exception {
        mockMvc.perform(get("/v1/me/benefit-grants").with(principal("ALLY_CAMPAIGN_VIEW")))
                .andExpect(status().isForbidden());

        when(grantService.forUser(any())).thenReturn(List.of());
        mockMvc.perform(get("/v1/me/benefit-grants").with(principal("MEMBER_VIEW_OWN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
