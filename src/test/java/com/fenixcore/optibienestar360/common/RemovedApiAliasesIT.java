package com.fenixcore.optibienestar360.common;

import com.fenixcore.optibienestar360.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The pre-rename API aliases ({@code /v1/promoter/me/**}, {@code /v1/admin/promoters/**},
 * {@code promoter-ranks}, {@code promoter-types}, {@code assign-promoter},
 * {@code promoter-history}, {@code members/promoter/*}, {@code /v1/aliado/services},
 * {@code /v1/ally/*}, {@code /v1/public/contact}, {@code /v1/public/affiliations})
 * were removed once every client moved to the new paths: they must answer 404,
 * even for a caller holding every permission the old handler required.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class RemovedApiAliasesIT {

    private static final String MEMBER = "00000000-0000-0000-0000-000000000001";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private RequestPostProcessor allPermissions() {
        CustomUserDetails p = CustomUserDetails.fromJwt(UUID.randomUUID(), "jti", "es",
                List.of("PROMOTER_VIEW_OWN", "PROMOTER_CONTACT_OWN", "PROMOTER_PAYOUT_ACCOUNT_VIEW_OWN",
                        "BONUS_VIEW_OWN", "PROMOTER_VIEW_ALL", "PROMOTER_RANK_VIEW", "PROMOTER_TYPE_VIEW",
                        "MEMBER_ASSIGN_PROMOTER", "ALLY_VIEW_OWN", "ALLY_VALIDATE_MEMBER", "ALLY_REGISTER_USAGE")
                        .stream().map(SimpleGrantedAuthority::new).toList());
        return authentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/v1/promoter/me",
            "/v1/promoter/me/payout-accounts",
            "/v1/promoter/me/contacts?memberUuid=" + MEMBER,
            "/v1/promoter/me/collection-score",
            "/v1/promoter/me/qr",
            "/v1/promoter/me/bonuses",
            "/v1/promoter/me/payments?direction=IN",
            "/v1/admin/promoters",
            "/v1/admin/promoter-ranks",
            "/v1/admin/catalogs/promoter-types",
            "/v1/admin/catalogs/promoter-types/options",
            "/v1/admin/members/" + MEMBER + "/promoter-history",
            "/v1/ally/validate/V-12345678",
            "/v1/ally/usage-history",
    })
    void removedGetAlias_is404(String path) throws Exception {
        mockMvc.perform(get(path).with(allPermissions()))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/v1/admin/members/" + MEMBER + "/assign-promoter",
            "/v1/admin/members/promoter/bulk-assign",
            "/v1/admin/members/promoter/portfolio-to-supervisor",
            "/v1/aliado/services",
            "/v1/ally/benefit-usage",
    })
    void removedPostAlias_is404(String path) throws Exception {
        mockMvc.perform(post(path).with(allPermissions())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/v1/public/contact", "/v1/public/affiliations"})
    void removedPublicAlias_anonymous_is404(String path) throws Exception {
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }
}
