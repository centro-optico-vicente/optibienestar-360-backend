package com.fenixcore.optibienestar360.modules.promoter;

import com.fenixcore.optibienestar360.core.dto.OptionDto;
import com.fenixcore.optibienestar360.modules.promoter.service.PromotersService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT of {@code GET /v1/admin/advisors/options} — the typeahead surface used by
 * payment, commission and membership filters. Verifies the flat {@code List<OptionDto>}
 * shape and that an inactive advisor passed via {@code currentValues} still
 * surfaces (with {@code active:false}) instead of being silently dropped, and that
 * the INSTITUCION system row is left out unless the caller asks for it
 * ({@code includeSystem=true}, used by the filters).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class AdminPromoterControllerOptionsIT {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private PromotersService promotersService;

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
        mockMvc.perform(get("/v1/admin/advisors/options"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void options_authenticatedWithoutScreenPermission_is200() throws Exception {
        // /options only needs a session (ADR LOC-0013, rule 3): no *_VIEW_ALL required.
        mockMvc.perform(get("/v1/admin/advisors/options").with(principal()))
                .andExpect(status().isOk());
    }

    @Test
    void options_returnsFlatListWithoutPageWrapper() throws Exception {
        UUID activeUuid = UUID.randomUUID();
        when(promotersService.listOptions(any(), eq(50), any(), eq(false)))
                .thenReturn(List.of(new OptionDto(activeUuid, "ABC123", "Juan Pérez", true)));

        mockMvc.perform(get("/v1/admin/advisors/options").param("q", "juan").with(principal("PROMOTER_VIEW_ALL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("ABC123"))
                .andExpect(jsonPath("$[0].label").value("Juan Pérez"));
    }

    @Test
    void options_currentValues_inactiveAdvisorStillAppears() throws Exception {
        UUID inactiveUuid = UUID.randomUUID();
        when(promotersService.listOptions(any(), eq(50), eq(List.of(inactiveUuid)), eq(false)))
                .thenReturn(List.of(new OptionDto(inactiveUuid, "OLD999", "Ex Asesor", false)));

        mockMvc.perform(get("/v1/admin/advisors/options")
                        .param("currentValues", inactiveUuid.toString())
                        .with(principal("PROMOTER_VIEW_ALL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].active").value(false));
    }

    @Test
    void options_byDefault_asksTheServiceToLeaveTheSystemAdvisorOut() throws Exception {
        mockMvc.perform(get("/v1/admin/advisors/options").with(principal()))
                .andExpect(status().isOk());

        verify(promotersService).listOptions(any(), eq(50), any(), eq(false));
    }

    @Test
    void options_includeSystem_isPassedThrough() throws Exception {
        UUID systemUuid = UUID.randomUUID();
        when(promotersService.listOptions(any(), eq(50), any(), eq(true)))
                .thenReturn(List.of(new OptionDto(systemUuid, "INSTITUCION", "Institución", true)));

        mockMvc.perform(get("/v1/admin/advisors/options").param("includeSystem", "true").with(principal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("INSTITUCION"));
    }
}
