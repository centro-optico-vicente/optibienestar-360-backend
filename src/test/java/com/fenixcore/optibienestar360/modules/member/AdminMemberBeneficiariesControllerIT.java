package com.fenixcore.optibienestar360.modules.member;

import com.fenixcore.optibienestar360.modules.member.dto.BeneficiaryDto;
import com.fenixcore.optibienestar360.modules.member.entity.Beneficiary.Relationship;
import com.fenixcore.optibienestar360.modules.member.service.BeneficiariesService;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT of the beneficiary sub-resource ({@link AdminMemberBeneficiariesController}):
 * reads gated by {@code MEMBER_VIEW_ALL}, writes by {@code MEMBER_UPDATE}, and
 * the v2 cap-exceeded error surfaced as 422. The service is mocked.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class AdminMemberBeneficiariesControllerIT {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private BeneficiariesService beneficiariesService;

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

    private BeneficiaryDto dto() {
        return new BeneficiaryDto(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Ana", null, "Pérez", null, "Ana Pérez", "V", "12345678",
                LocalDate.of(2015, 5, 20), null, null, Relationship.CHILD, false, null,
                true, null, null, null);
    }

    private static final String VALID_JSON = """
            {"firstName":"Ana","lastName":"Pérez","documentType":"V",
             "documentNumber":"12345678","relationship":"CHILD"}
            """;

    // ─── list (read, MEMBER_VIEW_ALL) ─────────────────────────────────────────

    @Test
    void list_anonymous_is401() throws Exception {
        mockMvc.perform(get("/v1/admin/members/{m}/beneficiaries", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_withPermission_is200() throws Exception {
        when(beneficiariesService.listForMember(any(), any())).thenReturn(List.of(dto()));

        mockMvc.perform(get("/v1/admin/members/{m}/beneficiaries", UUID.randomUUID())
                        .with(principal("MEMBER_VIEW_ALL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ─── add (write, MEMBER_UPDATE) ───────────────────────────────────────────

    @Test
    void add_withoutPermission_is403() throws Exception {
        mockMvc.perform(post("/v1/admin/members/{m}/beneficiaries", UUID.randomUUID())
                        .with(principal("MEMBER_VIEW_ALL"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_JSON))
                .andExpect(status().isForbidden());
        verify(beneficiariesService, never()).add(any(), any());
    }

    @Test
    void add_withPermission_is201() throws Exception {
        when(beneficiariesService.add(any(), any())).thenReturn(dto());

        mockMvc.perform(post("/v1/admin/members/{m}/beneficiaries", UUID.randomUUID())
                        .with(principal("MEMBER_UPDATE"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.relationship").value("CHILD"));
    }

    @Test
    void add_capExceeded_is422() throws Exception {
        when(beneficiariesService.add(any(), any()))
                .thenThrow(new IllegalArgumentException("member.beneficiary.cap_exceeded"));

        mockMvc.perform(post("/v1/admin/members/{m}/beneficiaries", UUID.randomUUID())
                        .with(principal("MEMBER_UPDATE"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_JSON))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void add_blankFirstName_is400() throws Exception {
        String bad = """
                {"firstName":"  ","lastName":"Pérez","documentType":"V",
                 "documentNumber":"12345678","relationship":"CHILD"}
                """;
        mockMvc.perform(post("/v1/admin/members/{m}/beneficiaries", UUID.randomUUID())
                        .with(principal("MEMBER_UPDATE"))
                        .contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isBadRequest());
        verify(beneficiariesService, never()).add(any(), any());
    }
}
