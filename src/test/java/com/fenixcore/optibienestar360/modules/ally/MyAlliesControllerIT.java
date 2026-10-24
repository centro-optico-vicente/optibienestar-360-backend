package com.fenixcore.optibienestar360.modules.ally;

import com.fenixcore.optibienestar360.modules.ally.dto.MyAllyDto;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser.AllyRole;
import com.fenixcore.optibienestar360.modules.ally.service.MyAlliesService;
import com.fenixcore.optibienestar360.modules.terms.service.TermsAcceptanceService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT of {@link MyAlliesController}: {@code GET /v1/me/allies} is the primary
 * path and {@code GET /v1/allies/me} the secondary one; both must serve
 * the exact same response and the same {@code ALLY_VIEW_OWN} gate.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class MyAlliesControllerIT {

    private static final String PRIMARY_PATH = "/v1/me/allies";
    private static final String SECONDARY_PATH = "/v1/allies/me";

    @Autowired
    private WebApplicationContext context;

    /** Portal gate ({@code TermsGateInterceptor}) asks it for pending contracts; this IT has no real database. */
    @MockitoBean
    private TermsAcceptanceService termsAcceptanceService;

    @MockitoBean
    private MyAlliesService myAlliesService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private RequestPostProcessor principal(String... authorities) {
        CustomUserDetails p = CustomUserDetails.fromJwt(UUID.randomUUID(), "jti", "es",
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
        return authentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }

    @Test
    void bothPaths_returnTheSameBody() throws Exception {
        UUID allyUuid = UUID.randomUUID();
        when(myAlliesService.listMyAllies(any())).thenReturn(List.of(
                new MyAllyDto(allyUuid, "Clínica Demo", List.of("Clínica"), null, null,
                        AllyRole.OWNER, true, LocalDate.of(2026, 1, 15))));

        String primary = mockMvc.perform(get(PRIMARY_PATH).with(principal("ALLY_VIEW_OWN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].uuid").value(allyUuid.toString()))
                .andExpect(jsonPath("$[0].name").value("Clínica Demo"))
                .andReturn().getResponse().getContentAsString();

        String secondary = mockMvc.perform(get(SECONDARY_PATH).with(principal("ALLY_VIEW_OWN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(secondary).isEqualTo(primary);
    }

    @Test
    void bothPaths_anonymous_is401() throws Exception {
        mockMvc.perform(get(PRIMARY_PATH)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(SECONDARY_PATH)).andExpect(status().isUnauthorized());
    }

    @Test
    void bothPaths_withoutAllyViewOwn_is403() throws Exception {
        mockMvc.perform(get(PRIMARY_PATH).with(principal("MEMBER_VIEW_OWN")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(SECONDARY_PATH).with(principal("MEMBER_VIEW_OWN")))
                .andExpect(status().isForbidden());
    }
}
