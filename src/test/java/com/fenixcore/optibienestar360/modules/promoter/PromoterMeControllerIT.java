package com.fenixcore.optibienestar360.modules.promoter;

import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterDashboardDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterMemberRow;
import com.fenixcore.optibienestar360.modules.promoter.service.PromoterDashboardService;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT del contrato de {@code GET /v1/promoter/me} ({@link PromoterMeController}):
 * autorización ({@code PROMOTER_VIEW_OWN}), forma del dashboard y el 404 cuando
 * el usuario autenticado no es promotor. {@link PromoterDashboardService} se
 * mockea; el principal es un {@link CustomUserDetails} real (el controller lee
 * {@code actor.getUuid()}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class PromoterMeControllerIT {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private PromoterDashboardService dashboardService;

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

    private PromoterDashboardDto dashboard() {
        return new PromoterDashboardDto(
                UUID.randomUUID(), "Ana Ventas", "ANAV42",
                4, 1, 2, 1,
                new BigDecimal("42.00"), "USD",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31),
                null,
                List.of(new PromoterMemberRow(UUID.randomUUID(), "Juan Pérez", "ACTIVE",
                        LocalDate.of(2026, 8, 1), new BigDecimal("5.00"))));
    }

    @Test
    void anonymous_is401_andNeverTouchesService() throws Exception {
        mockMvc.perform(get("/v1/promoter/me"))
                .andExpect(status().isUnauthorized());
        verify(dashboardService, never()).getMyDashboard(any());
    }

    @Test
    void authenticatedWithoutPermission_is403_andNeverTouchesService() throws Exception {
        mockMvc.perform(get("/v1/promoter/me").with(principal("MEMBER_VIEW_ALL")))
                .andExpect(status().isForbidden());
        verify(dashboardService, never()).getMyDashboard(any());
    }

    @Test
    void withPermission_returns200_withDashboard() throws Exception {
        when(dashboardService.getMyDashboard(any(UUID.class))).thenReturn(dashboard());

        mockMvc.perform(get("/v1/promoter/me").with(principal("PROMOTER_VIEW_OWN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referralCode").value("ANAV42"))
                .andExpect(jsonPath("$.activeAffiliates").value(4))
                .andExpect(jsonPath("$.affiliatesUpToDate").value(1))
                .andExpect(jsonPath("$.affiliatesOverdue").value(2))
                .andExpect(jsonPath("$.periodCurrency").value("USD"))
                .andExpect(jsonPath("$.portfolio.length()").value(1))
                // leaderboardPosition is null (PDF #5) → omitted by @JsonInclude(NON_NULL).
                .andExpect(jsonPath("$.leaderboardPosition").doesNotExist());
    }

    @Test
    void notAPromoter_maps_to_404() throws Exception {
        when(dashboardService.getMyDashboard(any(UUID.class)))
                .thenThrow(new NoSuchElementException("me.promoter.not_found"));

        mockMvc.perform(get("/v1/promoter/me").with(principal("PROMOTER_VIEW_OWN")))
                .andExpect(status().isNotFound());
    }
}
