package com.fenixcore.optibienestar360.modules.affiliation;

import com.fenixcore.optibienestar360.modules.affiliation.dto.AffiliationRequestCreatedDto;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AffiliationRequestDetailDto;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AffiliationRequestListItemDto;
import com.fenixcore.optibienestar360.modules.affiliation.service.AffiliationRequestService;
import com.fenixcore.optibienestar360.modules.terms.service.TermsAcceptanceService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT of the affiliation request endpoints (V190): the anonymous public submit,
 * one granular permission per admin action, the advisor's own list, and the 422 of a
 * rejection without reason. {@link AffiliationRequestService} is mocked (H2
 * test profile, no Flyway).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class AffiliationRequestControllersIT {

    private static final String CREATE_BODY = """
            {"firstName":"María","lastName":"Pérez","documentType":"V","documentNumber":"12345678",
             "birthDate":"1985-03-15","email":"maria@example.com","password":"s3cret-pass",
             "planUuid":"%s","termsVersionUuid":"%s"}
            """.formatted(UUID.randomUUID(), UUID.randomUUID());

    private static final String UPDATE_BODY = """
            {"firstName":"María","lastName":"Pérez","documentType":"V","documentNumber":"12345679",
             "birthDate":"1985-03-15","email":"maria@example.com","planUuid":"%s"}
            """.formatted(UUID.randomUUID());

    @Autowired
    private WebApplicationContext context;

    /** Portal gate ({@code TermsGateInterceptor}) asks it for pending contracts; this IT has no real database. */
    @MockitoBean
    private TermsAcceptanceService termsAcceptanceService;

    @MockitoBean
    private AffiliationRequestService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    private RequestPostProcessor principal(String... authorities) {
        CustomUserDetails p = CustomUserDetails.fromJwt(UUID.randomUUID(), "jti", "es",
                List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList());
        return authentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }

    private static AffiliationRequestDetailDto detail(String status) {
        return new AffiliationRequestDetailDto(UUID.randomUUID(),
                "María", "Pérez", "María Pérez", "V", "12345678", LocalDate.of(1985, 3, 15),
                "maria@example.com", null, "es",
                null, null, null,
                "SELF_SERVICE", null, false,
                null, "Contrato", "abc", Instant.parse("2026-10-20T14:00:00Z"), null, null, false,
                status, null, null, null,
                null, null, null,
                Instant.parse("2026-10-20T14:00:00Z"), List.of());
    }

    private static Page<AffiliationRequestListItemDto> onePage() {
        return new PageImpl<>(List.of(new AffiliationRequestListItemDto(UUID.randomUUID(), "María Pérez", "V",
                "12345678", "maria@example.com", null, null, null, "SELF_SERVICE", "PENDING",
                Instant.parse("2026-10-20T14:00:00Z"), null)));
    }

    // ─── Public submit ──────────────────────────────────────────────────────

    @Test
    void publicSubmit_isAnonymous_andReturns201WithThePendingRequest() throws Exception {
        UUID uuid = UUID.randomUUID();
        when(service.create(any(), any())).thenReturn(
                new AffiliationRequestCreatedDto(uuid, "PENDING", "maria@example.com", "Ana Ventas"));

        mockMvc.perform(post("/v1/public/affiliation-requests").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uuid").value(uuid.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.advisorName").value("Ana Ventas"));
    }

    @Test
    void publicSubmit_shortPassword_is400_andNeverTouchesService() throws Exception {
        mockMvc.perform(post("/v1/public/affiliation-requests").contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY.replace("s3cret-pass", "short")))
                .andExpect(status().isBadRequest());
        verify(service, never()).create(any(), any());
    }

    @Test
    void publicSubmit_duplicatePending_is422() throws Exception {
        when(service.create(any(), any()))
                .thenThrow(new IllegalArgumentException("affiliation_request.document.pending"));

        mockMvc.perform(post("/v1/public/affiliation-requests").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isUnprocessableEntity());
    }

    // ─── Admin ──────────────────────────────────────────────────────────────

    @Test
    void adminList_requiresViewAll() throws Exception {
        mockMvc.perform(get("/v1/admin/affiliation-requests")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/admin/affiliation-requests").with(principal("AFFILIATION_REQUEST_VIEW_OWN")))
                .andExpect(status().isForbidden());
        verify(service, never()).list(any(), any(), any());

        when(service.list(any(), eq("PENDING"), isNull())).thenReturn(onePage());
        mockMvc.perform(get("/v1/admin/affiliation-requests").param("status", "PENDING")
                        .with(principal("AFFILIATION_REQUEST_VIEW_ALL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].fullName").value("María Pérez"))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));
    }

    @Test
    void adminDetail_requiresViewAll() throws Exception {
        UUID uuid = UUID.randomUUID();
        when(service.get(uuid)).thenReturn(detail("PENDING"));

        mockMvc.perform(get("/v1/admin/affiliation-requests/{uuid}", uuid).with(principal("AFFILIATION_REQUEST_APPROVE")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/v1/admin/affiliation-requests/{uuid}", uuid).with(principal("AFFILIATION_REQUEST_VIEW_ALL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.termsRenderedContent").value("Contrato"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void adminUpdate_requiresUpdate() throws Exception {
        UUID uuid = UUID.randomUUID();
        mockMvc.perform(put("/v1/admin/affiliation-requests/{uuid}", uuid).with(principal("AFFILIATION_REQUEST_VIEW_ALL"))
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
                .andExpect(status().isForbidden());
        verify(service, never()).update(any(), any(), any());

        when(service.update(eq(uuid), any(), any())).thenReturn(detail("PENDING"));
        mockMvc.perform(put("/v1/admin/affiliation-requests/{uuid}", uuid).with(principal("AFFILIATION_REQUEST_UPDATE"))
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void adminApprove_requiresApprove_andTheBodyIsOptional() throws Exception {
        UUID uuid = UUID.randomUUID();
        mockMvc.perform(post("/v1/admin/affiliation-requests/{uuid}/approve", uuid).with(principal("AFFILIATION_REQUEST_REJECT")))
                .andExpect(status().isForbidden());
        verify(service, never()).approve(any(), any(), any());

        when(service.approve(eq(uuid), isNull(), any())).thenReturn(detail("APPROVED"));
        mockMvc.perform(post("/v1/admin/affiliation-requests/{uuid}/approve", uuid).with(principal("AFFILIATION_REQUEST_APPROVE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void adminApprove_ofAnAlreadyReviewedRequest_is422() throws Exception {
        UUID uuid = UUID.randomUUID();
        when(service.approve(eq(uuid), any(), any()))
                .thenThrow(new IllegalArgumentException("affiliation_request.approve.invalid_state"));

        mockMvc.perform(post("/v1/admin/affiliation-requests/{uuid}/approve", uuid).with(principal("AFFILIATION_REQUEST_APPROVE")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void adminReject_requiresReject() throws Exception {
        UUID uuid = UUID.randomUUID();
        mockMvc.perform(post("/v1/admin/affiliation-requests/{uuid}/reject", uuid).with(principal("AFFILIATION_REQUEST_APPROVE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Datos falsos\"}"))
                .andExpect(status().isForbidden());
        verify(service, never()).reject(any(), any(), any());

        when(service.reject(eq(uuid), eq("Datos falsos"), any())).thenReturn(detail("REJECTED"));
        mockMvc.perform(post("/v1/admin/affiliation-requests/{uuid}/reject", uuid).with(principal("AFFILIATION_REQUEST_REJECT"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Datos falsos\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void adminReject_withoutReason_is422() throws Exception {
        UUID uuid = UUID.randomUUID();
        when(service.reject(eq(uuid), isNull(), any()))
                .thenThrow(new IllegalArgumentException("affiliation_request.reject.reason_required"));

        mockMvc.perform(post("/v1/admin/affiliation-requests/{uuid}/reject", uuid).with(principal("AFFILIATION_REQUEST_REJECT"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity());
    }

    // ─── Advisor portal ─────────────────────────────────────────────────────

    @Test
    void advisorList_requiresViewOwn_notViewAll() throws Exception {
        mockMvc.perform(get("/v1/me/advisor/affiliation-requests").with(principal("AFFILIATION_REQUEST_VIEW_ALL")))
                .andExpect(status().isForbidden());
        verify(service, never()).listForAdvisor(any(), any(), any());

        when(service.listForAdvisor(any(UUID.class), any(), any())).thenReturn(onePage());
        mockMvc.perform(get("/v1/me/advisor/affiliation-requests").with(principal("AFFILIATION_REQUEST_VIEW_OWN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].documentNumber").value("12345678"));
    }

    @Test
    void advisorList_whenNotAnAdvisor_is404() throws Exception {
        when(service.listForAdvisor(any(UUID.class), any(), any()))
                .thenThrow(new java.util.NoSuchElementException("me.promoter.not_found"));

        mockMvc.perform(get("/v1/me/advisor/affiliation-requests").param("status", "PENDING")
                        .with(principal("AFFILIATION_REQUEST_VIEW_OWN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void advisorList_secondaryAdvisorsMePath_answersLikePrimary_withSameSecurity() throws Exception {
        when(service.listForAdvisor(any(UUID.class), any(), any())).thenReturn(onePage());

        mockMvc.perform(get("/v1/advisors/me/affiliation-requests"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/advisors/me/affiliation-requests").with(principal("AFFILIATION_REQUEST_VIEW_ALL")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/v1/advisors/me/affiliation-requests").with(principal("AFFILIATION_REQUEST_VIEW_OWN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].documentNumber").value("12345678"));
    }

    @Test
    void advisorList_primaryPath_anonymous_is401() throws Exception {
        mockMvc.perform(get("/v1/me/advisor/affiliation-requests"))
                .andExpect(status().isUnauthorized());
    }
}
