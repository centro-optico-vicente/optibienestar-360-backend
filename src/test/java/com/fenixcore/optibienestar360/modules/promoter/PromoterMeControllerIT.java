package com.fenixcore.optibienestar360.modules.promoter;

import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterDashboardDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterMemberRow;
import com.fenixcore.optibienestar360.modules.promoter.service.PromoterDashboardService;
import com.fenixcore.optibienestar360.modules.promoter.service.PromoterPayoutAccountsService;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT del contrato de {@code GET /v1/me/advisor} ({@link PromoterMeController}):
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

    /** Portal gate ({@code TermsGateInterceptor}) asks it for pending contracts; this IT has no real database. */
    @MockitoBean
    private TermsAcceptanceService termsAcceptanceService;

    @MockitoBean
    private PromoterDashboardService dashboardService;

    @MockitoBean
    private PromoterPayoutAccountsService payoutAccountsService;

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
        mockMvc.perform(get("/v1/me/advisor"))
                .andExpect(status().isUnauthorized());
        verify(dashboardService, never()).getMyDashboard(any());
    }

    @Test
    void authenticatedWithoutPermission_is403_andNeverTouchesService() throws Exception {
        mockMvc.perform(get("/v1/me/advisor").with(principal("MEMBER_VIEW_ALL")))
                .andExpect(status().isForbidden());
        verify(dashboardService, never()).getMyDashboard(any());
    }

    @Test
    void withPermission_returns200_withDashboard() throws Exception {
        when(dashboardService.getMyDashboard(any(UUID.class))).thenReturn(dashboard());

        mockMvc.perform(get("/v1/me/advisor").with(principal("PROMOTER_VIEW_OWN")))
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

        mockMvc.perform(get("/v1/me/advisor").with(principal("PROMOTER_VIEW_OWN")))
                .andExpect(status().isNotFound());
    }

    // ─── GET /v1/me/advisor/payout-accounts (V184) ──────────────────────────

    @Test
    void myPayoutAccounts_requiresItsOwnPermission_notPromoterViewOwn() throws Exception {
        mockMvc.perform(get("/v1/me/advisor/payout-accounts").with(principal("PROMOTER_VIEW_OWN")))
                .andExpect(status().isForbidden());
        verify(payoutAccountsService, never()).listMine(any());
    }

    @Test
    void myPayoutAccounts_resolvesTheCallerFromTheJwt() throws Exception {
        CustomUserDetails p = CustomUserDetails.fromJwt(UUID.randomUUID(), "jti", "es",
                List.of(new SimpleGrantedAuthority("PROMOTER_PAYOUT_ACCOUNT_VIEW_OWN")));
        when(payoutAccountsService.listMine(p.getUuid())).thenReturn(List.of());

        mockMvc.perform(get("/v1/me/advisor/payout-accounts")
                        .with(authentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        verify(payoutAccountsService).listMine(eq(p.getUuid()));
    }

    // ─── Secondary path /v1/advisors/me (kept, not deprecated) ──────────────

    @Test
    void secondaryAdvisorsMePath_answersLikePrimary_withSameSecurity() throws Exception {
        when(dashboardService.getMyDashboard(any(UUID.class))).thenReturn(dashboard());
        when(payoutAccountsService.listMine(any(UUID.class))).thenReturn(List.of());

        mockMvc.perform(get("/v1/advisors/me"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/advisors/me").with(principal("MEMBER_VIEW_ALL")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/v1/advisors/me").with(principal("PROMOTER_VIEW_OWN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referralCode").value("ANAV42"));
        mockMvc.perform(get("/v1/advisors/me/payout-accounts").with(principal("PROMOTER_VIEW_OWN")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/v1/advisors/me/payout-accounts").with(principal("PROMOTER_PAYOUT_ACCOUNT_VIEW_OWN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
