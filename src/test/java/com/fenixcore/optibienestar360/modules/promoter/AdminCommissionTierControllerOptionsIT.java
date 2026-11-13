package com.fenixcore.optibienestar360.modules.promoter;

import com.fenixcore.optibienestar360.core.dto.OptionDto;
import com.fenixcore.optibienestar360.modules.promoter.service.CommissionTiersService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT of {@code GET /v1/admin/commission-tiers/options} — flat {@code List<OptionDto>} for selects, open to any session
 * (ADR LOC-0013, rule 3). An inactive item passed via {@code currentValues} still surfaces.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class AdminCommissionTierControllerOptionsIT {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private CommissionTiersService commissionTiersService;

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
    void options_anonymous_is401() throws Exception {
        mockMvc.perform(get("/v1/admin/commission-tiers/options"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void options_authenticatedWithoutScreenPermission_is200() throws Exception {
        // /options only needs a session (ADR LOC-0013, rule 3): no *_VIEW_ALL required.
        mockMvc.perform(get("/v1/admin/commission-tiers/options").with(principal()))
                .andExpect(status().isOk());
    }

    @Test
    void options_returnsFlatListWithoutPageWrapper() throws Exception {
        UUID activeUuid = UUID.randomUUID();
        when(commissionTiersService.listOptions(isNull(), eq(50), isNull()))
                .thenReturn(List.of(new OptionDto(activeUuid, null, "Tramo base", true)));

        mockMvc.perform(get("/v1/admin/commission-tiers/options").with(principal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].uuid").value(activeUuid.toString()))
                .andExpect(jsonPath("$[0].active").value(true));
    }

    @Test
    void options_currentValues_inactiveItemStillAppears() throws Exception {
        UUID inactiveUuid = UUID.randomUUID();
        when(commissionTiersService.listOptions(any(), eq(50), eq(List.of(inactiveUuid))))
                .thenReturn(List.of(new OptionDto(inactiveUuid, null, "Inactivo", false)));

        mockMvc.perform(get("/v1/admin/commission-tiers/options")
                        .param("currentValues", inactiveUuid.toString())
                        .with(principal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].active").value(false));
    }
}
