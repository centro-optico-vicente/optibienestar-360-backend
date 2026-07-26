package com.fenixcore.optibienestar360.modules.subsidy;

import com.fenixcore.optibienestar360.modules.subsidy.service.SubsidiesService;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT of {@link MySubsidiesController} (V41) — self-service subsidy view gated by
 * {@code SUBSIDY_VIEW_OWN}. Service mocked.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class MySubsidiesControllerIT {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private SubsidiesService service;

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

    @Test
    void mine_anonymous_is401() throws Exception {
        mockMvc.perform(get("/v1/me/subsidies")).andExpect(status().isUnauthorized());
    }

    @Test
    void mine_withoutPermission_is403() throws Exception {
        mockMvc.perform(get("/v1/me/subsidies").with(principal("SUBSIDY_VIEW_ALL")))
                .andExpect(status().isForbidden());
    }

    @Test
    void mine_withPermission_is200() throws Exception {
        when(service.listForUser(any())).thenReturn(List.of());

        mockMvc.perform(get("/v1/me/subsidies").with(principal("SUBSIDY_VIEW_OWN")))
                .andExpect(status().isOk());
    }
}
