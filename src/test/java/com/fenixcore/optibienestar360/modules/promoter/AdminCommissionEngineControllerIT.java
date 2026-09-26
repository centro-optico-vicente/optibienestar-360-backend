package com.fenixcore.optibienestar360.modules.promoter;

import com.fenixcore.optibienestar360.modules.membership.entity.Plan.PlanType;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionTierDto;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.PeriodStrategy;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionTier.AppliesTo;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionTier.BasisType;
import com.fenixcore.optibienestar360.modules.promoter.service.CommissionTiersService;
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
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT covering the authorization of the commission-tiers admin surface (V42),
 * granular per V79 ({@code COMMISSION_TIER_VIEW_ALL} / {@code _CREATE} /
 * {@code _UPDATE} / {@code _DELETE}). Service mocked. The leaderboard/prizes
 * surfaces this once also covered were retired in Fase 3 (migrated into
 * competitive commission rules) — {@code LEADERBOARD_VIEW} below is only
 * used as a stand-in "some permission the principal holds that isn't a
 * commission-tier one" for the wrong-permission-is-403 case.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class AdminCommissionEngineControllerIT {

    @Autowired private WebApplicationContext context;

    @MockitoBean private CommissionTiersService tiersService;

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
        when(tiersService.list(any(), any(), any(), any(), any(), anyBoolean())).thenReturn(new PageImpl<>(List.of()));
        mockMvc.perform(get("/v1/admin/commission-tiers").with(principal("COMMISSION_TIER_VIEW_ALL")))
                .andExpect(status().isOk());
    }

    @Test
    void tiers_create_withPermission_is201() throws Exception {
        when(tiersService.create(any())).thenReturn(tierDto());
        String json = """
                {"name":"Gold","thresholdCount":10,"commissionPct":25,
                 "accrualPeriodStrategy":"MONTHLY","appliesTo":"BOTH"}
                """;
        mockMvc.perform(post("/v1/admin/commission-tiers").with(principal("COMMISSION_TIER_CREATE"))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
    }

    private static CommissionTierDto tierDto() {
        return new CommissionTierDto(UUID.randomUUID(), "Gold", null, PlanType.FAMILIAR, null, 10,
                new BigDecimal("25"), null, null, null,
                PeriodStrategy.MONTHLY, PeriodStrategy.MONTHLY, PeriodStrategy.MONTHLY, PeriodStrategy.MONTHLY,
                null, null, null, null,
                BasisType.COUNT, null, null, null,
                AppliesTo.BOTH, null, null, null,
                true, null, null, null);
    }
}
