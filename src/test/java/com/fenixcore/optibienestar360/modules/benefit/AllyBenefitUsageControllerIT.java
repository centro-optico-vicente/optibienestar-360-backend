package com.fenixcore.optibienestar360.modules.benefit;

import com.fenixcore.optibienestar360.modules.benefit.dto.BenefitUsageDto;
import com.fenixcore.optibienestar360.modules.benefit.service.BenefitUsagesService;
import com.fenixcore.optibienestar360.modules.terms.service.TermsAcceptanceService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT of the ally-side benefit usage paths: {@code POST /v1/me/allies/benefit-usages}
 * ({@link AllyBenefitUsageController}, {@code ALLY_REGISTER_USAGE}) and
 * {@code GET /v1/me/allies/usage-history} ({@link AllyUsageHistoryController},
 * {@code ALLY_VIEW_OWN}), plus the secondary {@code /v1/allies/me/*} paths.
 * {@link BenefitUsagesService} is mocked.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class AllyBenefitUsageControllerIT {

    @Autowired
    private WebApplicationContext context;

    /** Portal gate ({@code TermsGateInterceptor}) asks it for pending contracts; this IT has no real database. */
    @MockitoBean
    private TermsAcceptanceService termsAcceptanceService;

    @MockitoBean
    private BenefitUsagesService benefitUsagesService;

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

    private BenefitUsageDto usage() {
        return new BenefitUsageDto(UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "Juan Pérez", "V", "12345678",
                UUID.randomUUID(), "INDIVIDUAL",
                UUID.randomUUID(), "Clínica Ejemplo", null, null, null, null,
                LocalDate.of(2026, 10, 20), null,
                null, null,
                null, null,
                null, null,
                null, null, null);
    }

    private String registerBody() {
        return """
                {"membershipUuid":"%s","allyUuid":"%s"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID());
    }

    // ─── POST /v1/me/allies/benefit-usages ────────────────────────────────────

    @Test
    void register_anonymous_is401() throws Exception {
        mockMvc.perform(post("/v1/me/allies/benefit-usages")
                        .contentType(MediaType.APPLICATION_JSON).content(registerBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void register_withoutPermission_is403() throws Exception {
        mockMvc.perform(post("/v1/me/allies/benefit-usages").with(principal("ALLY_VIEW_OWN"))
                        .contentType(MediaType.APPLICATION_JSON).content(registerBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void register_withPermission_is201() throws Exception {
        when(benefitUsagesService.register(any())).thenReturn(usage());

        mockMvc.perform(post("/v1/me/allies/benefit-usages").with(principal("ALLY_REGISTER_USAGE"))
                        .contentType(MediaType.APPLICATION_JSON).content(registerBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.allyName").value("Clínica Ejemplo"));
    }

    // ─── GET /v1/me/allies/usage-history ──────────────────────────────────────

    @Test
    void history_anonymous_is401() throws Exception {
        mockMvc.perform(get("/v1/me/allies/usage-history"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void history_withoutPermission_is403() throws Exception {
        mockMvc.perform(get("/v1/me/allies/usage-history").with(principal("ALLY_REGISTER_USAGE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void history_withPermission_is200() throws Exception {
        when(benefitUsagesService.listForAllyUser(any(UUID.class), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(usage())));

        mockMvc.perform(get("/v1/me/allies/usage-history").with(principal("ALLY_VIEW_OWN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    // ─── Secondary paths /v1/allies/me/* (kept, not deprecated) ──────────────

    @Test
    void secondaryAlliesMePaths_answerLikePrimary_withSameSecurity() throws Exception {
        when(benefitUsagesService.register(any())).thenReturn(usage());
        when(benefitUsagesService.listForAllyUser(any(UUID.class), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(usage())));

        mockMvc.perform(post("/v1/allies/me/benefit-usages")
                        .contentType(MediaType.APPLICATION_JSON).content(registerBody()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/v1/allies/me/benefit-usages").with(principal("ALLY_VIEW_OWN"))
                        .contentType(MediaType.APPLICATION_JSON).content(registerBody()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/v1/allies/me/benefit-usages").with(principal("ALLY_REGISTER_USAGE"))
                        .contentType(MediaType.APPLICATION_JSON).content(registerBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.allyName").value("Clínica Ejemplo"));
        mockMvc.perform(get("/v1/allies/me/usage-history"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/allies/me/usage-history").with(principal("ALLY_REGISTER_USAGE")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/v1/allies/me/usage-history").with(principal("ALLY_VIEW_OWN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }
}
