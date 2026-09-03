package com.fenixcore.optibienestar360.modules.corporate;

import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.modules.corporate.dto.CorporateBulkEnrollResponse;
import com.fenixcore.optibienestar360.modules.corporate.dto.CorporateContractDto;
import com.fenixcore.optibienestar360.modules.corporate.entity.CorporateContract.PayerMode;
import com.fenixcore.optibienestar360.modules.corporate.service.CorporateContractsService;
import com.fenixcore.optibienestar360.modules.member.dto.MemberListItemDto;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT of the admin corporate-contract contract (v2 PDF — "Contratos
 * Corporativos", V38, {@link AdminCorporateContractController}): granular per
 * V79 — contract CRUD via {@code CORPORATE_CONTRACT_VIEW_ALL}/{@code _CREATE}/
 * {@code _UPDATE}/{@code _DELETE}, member association via
 * {@code CORPORATE_CONTRACT_MEMBER_VIEW_ALL}/{@code _CREATE}. The service is mocked.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class AdminCorporateContractControllerIT {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private CorporateContractsService service;

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

    private CorporateContractDto contractDto() {
        return new CorporateContractDto(UUID.randomUUID(),
                new DisplayRef(UUID.randomUUID(), null, "Corp Plan"),
                "Clínica X", "J-123456789", null, PayerMode.INSTITUTION_BULK, 100, 0,
                true, null, null, null);
    }

    private static final String VALID_CREATE_JSON = """
            {"planUuid":"11111111-1111-1111-1111-111111111111","institutionName":"Clínica X",
             "institutionTaxId":"J-123456789","payerMode":"INSTITUTION_BULK","expectedMemberCount":100}
            """;

    private static final String VALID_BULK_JSON = """
            {"members":[{"firstName":"Juan","lastName":"Pérez","documentType":"V",
             "documentNumber":"12345678","birthDate":"1990-01-01"}]}
            """;

    // ─── list (read, VIEW_ALL) ───────────────────────────────────────────────

    @Test
    void list_anonymous_is401() throws Exception {
        mockMvc.perform(get("/v1/admin/corporate-contracts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_withoutPermission_is403() throws Exception {
        mockMvc.perform(get("/v1/admin/corporate-contracts").with(principal("CORPORATE_CONTRACT_CREATE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_withPermission_is200() throws Exception {
        when(service.list(any(), any(), any())).thenReturn(new PageImpl<>(List.of(contractDto())));

        mockMvc.perform(get("/v1/admin/corporate-contracts").with(principal("CORPORATE_CONTRACT_VIEW_ALL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    // ─── create (write, CREATE) ─────────────────────────────────────────────

    @Test
    void create_withoutPermission_is403() throws Exception {
        mockMvc.perform(post("/v1/admin/corporate-contracts")
                        .with(principal("CORPORATE_CONTRACT_VIEW_ALL"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE_JSON))
                .andExpect(status().isForbidden());
        verify(service, never()).create(any());
    }

    @Test
    void create_withPermission_is201() throws Exception {
        when(service.create(any())).thenReturn(contractDto());

        mockMvc.perform(post("/v1/admin/corporate-contracts")
                        .with(principal("CORPORATE_CONTRACT_CREATE"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.payerMode").value("INSTITUTION_BULK"));
    }

    @Test
    void create_blankInstitutionName_is400() throws Exception {
        String badJson = """
                {"planUuid":"11111111-1111-1111-1111-111111111111","institutionName":"  ",
                 "institutionTaxId":"J-1","payerMode":"INSTITUTION_BULK"}
                """;
        mockMvc.perform(post("/v1/admin/corporate-contracts")
                        .with(principal("CORPORATE_CONTRACT_CREATE"))
                        .contentType(MediaType.APPLICATION_JSON).content(badJson))
                .andExpect(status().isBadRequest());
        verify(service, never()).create(any());
    }

    // ─── delete (write, DELETE) ─────────────────────────────────────────────

    @Test
    void delete_withPermission_is204() throws Exception {
        mockMvc.perform(delete("/v1/admin/corporate-contracts/{u}", UUID.randomUUID())
                        .with(principal("CORPORATE_CONTRACT_DELETE")))
                .andExpect(status().isNoContent());
        verify(service).delete(any());
    }

    // ─── bulk enroll (write, MEMBER_CREATE) ──────────────────────────────────

    @Test
    void enrollMembers_withoutPermission_is403() throws Exception {
        mockMvc.perform(post("/v1/admin/corporate-contracts/{u}/members", UUID.randomUUID())
                        .with(principal("CORPORATE_CONTRACT_VIEW_ALL"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BULK_JSON))
                .andExpect(status().isForbidden());
        verify(service, never()).enrollMembers(any(), any());
    }

    @Test
    void enrollMembers_withPermission_is200() throws Exception {
        when(service.enrollMembers(any(), any())).thenReturn(
                new CorporateBulkEnrollResponse(1, 1, 0, List.of()));

        mockMvc.perform(post("/v1/admin/corporate-contracts/{u}/members", UUID.randomUUID())
                        .with(principal("CORPORATE_CONTRACT_MEMBER_CREATE"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BULK_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrolled").value(1));
    }

    @Test
    void enrollMembers_emptyList_is400() throws Exception {
        mockMvc.perform(post("/v1/admin/corporate-contracts/{u}/members", UUID.randomUUID())
                        .with(principal("CORPORATE_CONTRACT_MEMBER_CREATE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"members\":[]}"))
                .andExpect(status().isBadRequest());
        verify(service, never()).enrollMembers(any(), any());
    }

    // ─── list members (read, VIEW_ALL) ───────────────────────────────────────

    @Test
    void listMembers_withPermission_is200() throws Exception {
        when(service.listMembers(any(), any())).thenReturn(new PageImpl<>(List.<MemberListItemDto>of()));

        mockMvc.perform(get("/v1/admin/corporate-contracts/{u}/members", UUID.randomUUID())
                        .with(principal("CORPORATE_CONTRACT_MEMBER_VIEW_ALL")))
                .andExpect(status().isOk());
    }

    @Test
    void listMembers_withoutPermission_is403() throws Exception {
        mockMvc.perform(get("/v1/admin/corporate-contracts/{u}/members", UUID.randomUUID())
                        .with(principal("CORPORATE_CONTRACT_VIEW_ALL")))
                .andExpect(status().isForbidden());
        verify(service, never()).listMembers(any(), any());
    }
}
