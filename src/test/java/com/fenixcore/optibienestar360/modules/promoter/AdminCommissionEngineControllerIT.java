package com.fenixcore.optibienestar360.modules.promoter;

import com.fenixcore.optibienestar360.modules.membership.entity.Plan.PlanType;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionTierDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.LeaderboardDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.PrizeAwardResult;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.PeriodStrategy;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionTier.AppliesTo;
import com.fenixcore.optibienestar360.modules.promoter.service.CommissionTiersService;
import com.fenixcore.optibienestar360.modules.promoter.service.LeaderboardPrizeService;
import com.fenixcore.optibienestar360.modules.promoter.service.LeaderboardService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT covering the authorization of the three new commission-engine admin
 * surfaces (V42): tiers ({@code COMMISSION_TIER_MANAGE}), leaderboard
 * ({@code LEADERBOARD_VIEW}), prizes ({@code LEADERBOARD_PRIZE_MANAGE}). Services
 * mocked; one context for all three to keep the suite lean.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class AdminCommissionEngineControllerIT {

    @Autowired private WebApplicationContext context;

    @MockitoBean private CommissionTiersService tiersService;
    @MockitoBean private LeaderboardService leaderboardService;
    @MockitoBean private LeaderboardPrizeService prizeService;

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

    // ─── commission-tiers ─────────────────────────────────────────────────────

    @Test
    void tiers_anonymous_is401() throws Exception {
        mockMvc.perform(get("/v1/admin/commission-tiers")).andExpect(status().isUnauthorized());
    }

    @Test
    void tiers_withoutPermission_is403() throws Exception {
        mockMvc.perform(get("/v1/admin/commission-tiers").with(principal("LEADERBOARD_VIEW")))
                .andExpect(status().isForbidden());
    }

    @Test
    void tiers_list_withPermission_is200() throws Exception {
        when(tiersService.list(any(), any(), any(), any(), anyBoolean())).thenReturn(new PageImpl<>(List.of()));
        mockMvc.perform(get("/v1/admin/commission-tiers").with(principal("COMMISSION_TIER_MANAGE")))
                .andExpect(status().isOk());
    }

    @Test
    void tiers_create_withPermission_is201() throws Exception {
        when(tiersService.create(any())).thenReturn(tierDto());
        String json = """
                {"name":"Gold","thresholdCount":10,"commissionPct":25,
                 "periodStrategy":"MONTHLY","appliesTo":"BOTH"}
                """;
        mockMvc.perform(post("/v1/admin/commission-tiers").with(principal("COMMISSION_TIER_MANAGE"))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
    }

    // ─── leaderboard ──────────────────────────────────────────────────────────

    @Test
    void leaderboard_withoutPermission_is403() throws Exception {
        mockMvc.perform(get("/v1/admin/leaderboard").with(principal("COMMISSION_TIER_MANAGE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void leaderboard_withPermission_is200() throws Exception {
        when(leaderboardService.leaderboard(any(), any(), anyInt())).thenReturn(
                new LeaderboardDto(PeriodStrategy.MONTHLY, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), List.of()));
        mockMvc.perform(get("/v1/admin/leaderboard").param("period", "2026-06")
                        .with(principal("LEADERBOARD_VIEW")))
                .andExpect(status().isOk());
    }

    // ─── leaderboard prizes ─────────────────────────────────────────────────────

    @Test
    void prizes_list_withPermission_is200() throws Exception {
        when(prizeService.list()).thenReturn(List.of());
        mockMvc.perform(get("/v1/admin/leaderboard-prizes").with(principal("LEADERBOARD_PRIZE_MANAGE")))
                .andExpect(status().isOk());
    }

    @Test
    void prizes_award_withPermission_is200() throws Exception {
        when(prizeService.award(any(), any(), anyBoolean())).thenReturn(
                new PrizeAwardResult(PeriodStrategy.MONTHLY, LocalDate.of(2026, 6, 1),
                        LocalDate.of(2026, 6, 30), true, 0, BigDecimal.ZERO, "USD"));
        mockMvc.perform(post("/v1/admin/leaderboard-prizes/award").param("dryRun", "true")
                        .with(principal("LEADERBOARD_PRIZE_MANAGE")))
                .andExpect(status().isOk());
    }

    @Test
    void prizes_withoutPermission_is403() throws Exception {
        mockMvc.perform(get("/v1/admin/leaderboard-prizes").with(principal("LEADERBOARD_VIEW")))
                .andExpect(status().isForbidden());
    }

    private static CommissionTierDto tierDto() {
        return new CommissionTierDto(UUID.randomUUID(), "Gold", PlanType.FAMILIAR, null, null, 10,
                new BigDecimal("25"), null, PeriodStrategy.MONTHLY, AppliesTo.BOTH, true, null, null, null);
    }
}
