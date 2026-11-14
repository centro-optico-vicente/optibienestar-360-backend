package com.fenixcore.optibienestar360.modules.campaign;

import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.modules.campaign.dto.CampaignPaymentRemindResultDto;
import com.fenixcore.optibienestar360.modules.campaign.dto.CampaignPaymentRowDto;
import com.fenixcore.optibienestar360.modules.campaign.dto.CampaignPaymentsDto;
import com.fenixcore.optibienestar360.modules.campaign.dto.CampaignSlotDto;
import com.fenixcore.optibienestar360.modules.campaign.dto.CampaignSlotIssueDto;
import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion;
import com.fenixcore.optibienestar360.modules.promotion.entity.PromotionRedemption.RedemptionStatus;
import com.fenixcore.optibienestar360.modules.promotion.service.CampaignSlotService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT of the campaign slot endpoints (V260, hub ADR 0038 §9): one granular
 * permission per action, 400/422 on invalid input or state, and the JSON
 * shape the frontend reads (flat DisplayRefs, Spring page).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class AdminCampaignSlotControllerIT {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private CampaignSlotService slotService;

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

    private static CampaignSlotDto slot(UUID uuid, RedemptionStatus status, String note) {
        return new CampaignSlotDto(uuid,
                new DisplayRef(UUID.randomUUID(), "V12345678", "Ana Pérez"),
                new DisplayRef(UUID.randomUUID(), null, "Plan Familiar"),
                new DisplayRef(UUID.randomUUID(), null, "Lanzamiento"),
                null,
                Promotion.BenefitKind.DISCOUNT, status, Campaign.QuotaTrigger.ON_PAYMENT_CONFIRMED,
                Instant.now(), status == RedemptionStatus.CONFIRMED ? Instant.now() : null,
                status == RedemptionStatus.PENDING ? Instant.now().plusSeconds(3600) : null,
                status == RedemptionStatus.PENDING, null, note,
                note == null ? null : new DisplayRef(UUID.randomUUID(), null, "admin@example.com"));
    }

    @Test
    void slots_requireCampaignViewAll_andExposeFlatRefs() throws Exception {
        UUID campaign = UUID.randomUUID();
        mockMvc.perform(get("/v1/admin/campaigns/{uuid}/slots", campaign).with(principal("CAMPAIGN_PAYMENT_VIEW")))
                .andExpect(status().isForbidden());

        UUID slotUuid = UUID.randomUUID();
        when(slotService.slots(eq(campaign), eq(RedemptionStatus.PENDING), any()))
                .thenReturn(new PageImpl<>(List.of(slot(slotUuid, RedemptionStatus.PENDING, null)), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/v1/admin/campaigns/{uuid}/slots", campaign).param("status", "PENDING")
                        .with(principal("CAMPAIGN_VIEW_ALL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].uuid").value(slotUuid.toString()))
                .andExpect(jsonPath("$.content[0].member_Uuid").exists())
                .andExpect(jsonPath("$.content[0].member_Display").exists())
                .andExpect(jsonPath("$.content[0].redemptionStatus").value("PENDING"))
                .andExpect(jsonPath("$.content[0].redemptionStatus_Display").exists())
                .andExpect(jsonPath("$.content[0].deadlineFrozen").value(true))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void confirm_requiresItsPermission_aNote_andAPendingSlot() throws Exception {
        UUID campaign = UUID.randomUUID();
        UUID slotUuid = UUID.randomUUID();
        String path = "/v1/admin/campaigns/{uuid}/slots/{slotUuid}/confirm";
        mockMvc.perform(post(path, campaign, slotUuid).with(principal("CAMPAIGN_VIEW_ALL", "PROMOTION_SLOT_RELEASE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"ok\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(path, campaign, slotUuid).with(principal("PROMOTION_SLOT_CONFIRM"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"   \"}"))
                .andExpect(status().isBadRequest());

        when(slotService.confirm(eq(campaign), eq(slotUuid), eq("Pago verificado"), any()))
                .thenReturn(slot(slotUuid, RedemptionStatus.CONFIRMED, "Pago verificado"));
        mockMvc.perform(post(path, campaign, slotUuid).with(principal("PROMOTION_SLOT_CONFIRM"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Pago verificado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redemptionStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.manualNote").value("Pago verificado"))
                .andExpect(jsonPath("$.manualBy_Display").value("admin@example.com"));

        when(slotService.confirm(eq(campaign), eq(slotUuid), eq("Otra vez"), any()))
                .thenThrow(new IllegalArgumentException("promotion_slot.confirm.not_pending"));
        mockMvc.perform(post(path, campaign, slotUuid).with(principal("PROMOTION_SLOT_CONFIRM"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Otra vez\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void release_requiresItsPermission_andAnUsedSlotIs422() throws Exception {
        UUID campaign = UUID.randomUUID();
        UUID slotUuid = UUID.randomUUID();
        String path = "/v1/admin/campaigns/{uuid}/slots/{slotUuid}/release";
        mockMvc.perform(post(path, campaign, slotUuid).with(principal("PROMOTION_SLOT_CONFIRM"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"x\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(path, campaign, slotUuid).with(principal("PROMOTION_SLOT_RELEASE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        when(slotService.release(eq(campaign), eq(slotUuid), eq("Ya usado"), any()))
                .thenThrow(new IllegalArgumentException("promotion_slot.release.not_allowed"));
        mockMvc.perform(post(path, campaign, slotUuid).with(principal("PROMOTION_SLOT_RELEASE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Ya usado\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void slotIssues_listTheThreeTypes() throws Exception {
        UUID campaign = UUID.randomUUID();
        DisplayRef member = new DisplayRef(UUID.randomUUID(), "V1", "Ana");
        DisplayRef membership = new DisplayRef(UUID.randomUUID(), null, "Plan");
        DisplayRef promotion = new DisplayRef(UUID.randomUUID(), null, "Lanzamiento");
        DisplayRef payment = new DisplayRef(UUID.randomUUID(), "REF-1", null);
        when(slotService.issues(campaign)).thenReturn(List.of(
                new CampaignSlotIssueDto(CampaignSlotIssueDto.IssueType.APPROVED_DISCOUNT_WITHOUT_SLOT, member, membership,
                        promotion, payment, UUID.randomUUID(), RedemptionStatus.EXPIRED, Instant.now()),
                new CampaignSlotIssueDto(CampaignSlotIssueDto.IssueType.PENDING_WITH_APPROVED_PAYMENT, member, membership,
                        promotion, payment, UUID.randomUUID(), RedemptionStatus.PENDING, Instant.now()),
                new CampaignSlotIssueDto(CampaignSlotIssueDto.IssueType.FROZEN_TOO_LONG, member, membership,
                        promotion, payment, UUID.randomUUID(), RedemptionStatus.PENDING, Instant.now())));

        mockMvc.perform(get("/v1/admin/campaigns/{uuid}/slot-issues", campaign).with(principal("CAMPAIGN_VIEW_ALL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("APPROVED_DISCOUNT_WITHOUT_SLOT"))
                .andExpect(jsonPath("$[1].type").value("PENDING_WITH_APPROVED_PAYMENT"))
                .andExpect(jsonPath("$[2].type").value("FROZEN_TOO_LONG"))
                .andExpect(jsonPath("$[2].type_Display").exists())
                .andExpect(jsonPath("$[0].payment_Uuid").value(payment.uuid().toString()));
    }

    @Test
    void payments_requireCampaignPaymentView_andReturnTotalsAndAPage() throws Exception {
        UUID campaign = UUID.randomUUID();
        mockMvc.perform(get("/v1/admin/campaigns/{uuid}/payments", campaign).with(principal("CAMPAIGN_VIEW_ALL")))
                .andExpect(status().isForbidden());

        UUID slotUuid = UUID.randomUUID();
        CampaignPaymentRowDto row = new CampaignPaymentRowDto(
                new DisplayRef(UUID.randomUUID(), "REF-9", null), "REF-9", new BigDecimal("20.00"), "USD",
                LocalDate.now(), "PENDING",
                new DisplayRef(UUID.randomUUID(), "V1", "Ana"), new DisplayRef(UUID.randomUUID(), null, "Plan"),
                null, new DisplayRef(UUID.randomUUID(), null, "Lanzamiento"), slotUuid, RedemptionStatus.PENDING,
                Instant.now().plusSeconds(3600), true, true, null);
        when(slotService.payments(eq(campaign), isNull(), isNull(), any())).thenReturn(new CampaignPaymentsDto(
                new CampaignPaymentsDto.Totals(3, 1, 2, 4), new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1)));

        mockMvc.perform(get("/v1/admin/campaigns/{uuid}/payments", campaign).with(principal("CAMPAIGN_PAYMENT_VIEW")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.confirmed").value(3))
                .andExpect(jsonPath("$.totals.pendingInReview").value(1))
                .andExpect(jsonPath("$.totals.pendingWithoutPayment").value(2))
                .andExpect(jsonPath("$.totals.expired").value(4))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.content[0].paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.page.content[0].confirmsOnApproval").value(true))
                .andExpect(jsonPath("$.page.content[0].amountCurrency_Code").value("USD"))
                .andExpect(jsonPath("$.page.content[0].payment_Uuid").exists())
                .andExpect(jsonPath("$.page.content[0].slotUuid").value(slotUuid.toString()));
    }

    @Test
    void remind_requiresCampaignPaymentRemind_acceptsANullNote_andIs422WithinTheHour() throws Exception {
        UUID campaign = UUID.randomUUID();
        UUID payment = UUID.randomUUID();
        String path = "/v1/admin/campaigns/{uuid}/payments/{paymentUuid}/remind";
        mockMvc.perform(post(path, campaign, payment).with(principal("CAMPAIGN_PAYMENT_VIEW"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":null}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(path, campaign, payment).with(principal("CAMPAIGN_PAYMENT_REMIND"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":null}"))
                .andExpect(status().isNoContent());
        verify(slotService).remind(eq(campaign), eq(payment), isNull(), any());

        doThrow(new IllegalArgumentException("campaign_payment.remind.too_soon"))
                .when(slotService).remind(eq(campaign), eq(payment), eq("Otra vez"), any());
        mockMvc.perform(post(path, campaign, payment).with(principal("CAMPAIGN_PAYMENT_REMIND"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Otra vez\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void remindPending_returnsSentAndSkipped() throws Exception {
        UUID campaign = UUID.randomUUID();
        when(slotService.remindPending(eq(campaign), isNull(), any())).thenReturn(new CampaignPaymentRemindResultDto(2, 1));

        mockMvc.perform(post("/v1/admin/campaigns/{uuid}/payments/remind-pending", campaign)
                        .with(principal("CAMPAIGN_PAYMENT_REMIND"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(2))
                .andExpect(jsonPath("$.skipped").value(1));
    }
}
