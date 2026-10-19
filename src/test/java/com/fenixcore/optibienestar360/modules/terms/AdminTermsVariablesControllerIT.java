package com.fenixcore.optibienestar360.modules.terms;

import com.fenixcore.optibienestar360.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT of the T&C template variables: {@code GET /v1/admin/terms/variables?type=}
 * (gated by {@code TERMS_VIEW_ALL}, Spanish wire codes) and the 422 that
 * {@code POST /v1/admin/terms} returns for a template using variables of
 * another kind. The real {@code TermsVersionService} runs — both paths
 * resolve before any database access (H2 test profile, no Flyway seed).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class AdminTermsVariablesControllerIT {

    @Autowired
    private WebApplicationContext context;

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

    @Test
    void variables_withoutPermission_is403() throws Exception {
        mockMvc.perform(get("/v1/admin/terms/variables").param("type", "ASESOR")
                        .with(principal("MEMBER_VIEW_ALL")))
                .andExpect(status().isForbidden());
    }

    @Test
    void variables_ofTheAdvisorType_listOnlyCommonAndAdvisorKeys() throws Exception {
        mockMvc.perform(get("/v1/admin/terms/variables").param("type", "ASESOR")
                        .header("Accept-Language", "es")
                        .with(principal("TERMS_VIEW_ALL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("brand.name"))
                .andExpect(jsonPath("$[*].key", hasItem("advisor.code")))
                .andExpect(jsonPath("$[*].key", hasItem("org.taxId")))
                .andExpect(jsonPath("$[*].key", not(hasItem("member.fullName"))))
                .andExpect(jsonPath("$[?(@.key == 'advisor.fullName')].description", hasItem("Nombre completo del asesor")));
    }

    @Test
    void variables_ofTheAllyType_includeTheAgreementKeys() throws Exception {
        mockMvc.perform(get("/v1/admin/terms/variables").param("type", "ALIADO")
                        .with(principal("TERMS_VIEW_ALL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].key", hasItem("agreement.addendum")))
                .andExpect(jsonPath("$[*].key", not(hasItem("advisor.code"))));
    }

    @Test
    void variables_withTheRetiredCode_is400() throws Exception {
        mockMvc.perform(get("/v1/admin/terms/variables").param("type", "PROMOTOR")
                        .with(principal("TERMS_VIEW_ALL")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_withVariablesOfAnotherType_is422ListingThem() throws Exception {
        String body = """
                {"termType":"AFILIADO","title":"Acuerdo","contentMarkdown":"{{member.fullName}} {{advisor.code}} {{foo.bar}}",
                 "isPublic":false,"validFrom":"2026-01-01T00:00:00Z"}
                """;

        mockMvc.perform(post("/v1/admin/terms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Accept-Language", "es")
                        .with(principal("TERMS_CREATE")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.invalidVariables[0]").value("advisor.code"))
                .andExpect(jsonPath("$.invalidVariables[1]").value("foo.bar"))
                .andExpect(jsonPath("$.detail", containsString("advisor.code, foo.bar")));
    }
}
