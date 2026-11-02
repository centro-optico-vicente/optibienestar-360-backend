package com.fenixcore.optibienestar360.modules.dashboard;

import com.fenixcore.optibienestar360.modules.dashboard.dto.DashboardActivityDto;
import com.fenixcore.optibienestar360.modules.dashboard.dto.DashboardChartDto;
import com.fenixcore.optibienestar360.modules.dashboard.dto.DashboardChartPointDto;
import com.fenixcore.optibienestar360.modules.dashboard.dto.DashboardDto;
import com.fenixcore.optibienestar360.modules.dashboard.dto.DashboardKpiDto;
import com.fenixcore.optibienestar360.modules.dashboard.dto.DashboardSection;
import com.fenixcore.optibienestar360.modules.dashboard.service.DashboardService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
 * IT del contrato de {@code GET /v1/me/dashboard} ({@link MyDashboardController}):
 * exige autenticación pero ningún permiso propio, pasa al servicio el uuid y las
 * authorities del principal, y serializa KPIs, gráficos y actividad en camelCase
 * omitiendo los nulos. {@link DashboardService} se mockea.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class MyDashboardControllerIT {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private DashboardService dashboardService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private RequestPostProcessor principal(UUID uuid, String... authorities) {
        CustomUserDetails p = CustomUserDetails.fromJwt(uuid, "jti", "es",
                List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList());
        return authentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }

    private DashboardDto dashboard() {
        return new DashboardDto(
                LocalDate.of(2026, 11, 1),
                List.of(DashboardSection.ADVISOR),
                List.of(DashboardKpiDto.number(DashboardSection.ADVISOR, "portfolioMembers", 4, 3),
                        DashboardKpiDto.text(DashboardSection.ADVISOR, "membershipStatus", "ACTIVE")),
                List.of(DashboardChartDto.counts(DashboardSection.ADVISOR, "portfolioPaymentStatus",
                        DashboardChartDto.Type.DONUT, DashboardChartDto.LabelKind.ENUM,
                        List.of(DashboardChartPointDto.of("UP_TO_DATE", 3)))),
                List.of(new DashboardActivityDto(DashboardActivityDto.Type.PAYMENT_APPROVED,
                        DashboardSection.ADVISOR, "Ana Pérez", "Plan Familiar", new BigDecimal("12.50"), "USD",
                        Instant.parse("2026-11-02T14:03:00Z"), DashboardActivityDto.Status.SUCCESS)));
    }

    @Test
    void anonymous_is401_andNeverTouchesService() throws Exception {
        mockMvc.perform(get("/v1/me/dashboard"))
                .andExpect(status().isUnauthorized());
        verify(dashboardService, never()).getMyDashboard(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void authenticated_returns200_andForwardsUuidAndAuthorities() throws Exception {
        UUID uuid = UUID.randomUUID();
        when(dashboardService.getMyDashboard(eq(uuid), any())).thenReturn(dashboard());

        mockMvc.perform(get("/v1/me/dashboard").with(principal(uuid, "PROMOTER_VIEW_OWN", "MEMBER_VIEW_OWN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodStart").value("2026-11-01"))
                .andExpect(jsonPath("$.sections[0]").value("ADVISOR"))
                .andExpect(jsonPath("$.kpis[0].key").value("portfolioMembers"))
                .andExpect(jsonPath("$.kpis[0].format").value("NUMBER"))
                .andExpect(jsonPath("$.kpis[0].value").value(4))
                .andExpect(jsonPath("$.kpis[0].previousValue").value(3))
                .andExpect(jsonPath("$.kpis[0].currency").doesNotExist())
                .andExpect(jsonPath("$.kpis[1].text").value("ACTIVE"))
                .andExpect(jsonPath("$.charts[0].type").value("DONUT"))
                .andExpect(jsonPath("$.charts[0].labelKind").value("ENUM"))
                .andExpect(jsonPath("$.charts[0].points[0].key").value("UP_TO_DATE"))
                .andExpect(jsonPath("$.charts[0].points[0].label").doesNotExist())
                .andExpect(jsonPath("$.activity[0].type").value("PAYMENT_APPROVED"))
                .andExpect(jsonPath("$.activity[0].title").value("Ana Pérez"))
                .andExpect(jsonPath("$.activity[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.activity[0].occurredAt").exists());

        ArgumentCaptor<Collection<String>> authorities = ArgumentCaptor.forClass(Collection.class);
        verify(dashboardService).getMyDashboard(eq(uuid), authorities.capture());
        assertThat(authorities.getValue()).contains("PROMOTER_VIEW_OWN", "MEMBER_VIEW_OWN");
    }

    @Test
    void authenticatedWithoutAnyPermission_stillReturns200() throws Exception {
        UUID uuid = UUID.randomUUID();
        when(dashboardService.getMyDashboard(eq(uuid), any())).thenReturn(
                new DashboardDto(LocalDate.of(2026, 11, 1), List.of(), List.of(), List.of(), List.of()));

        mockMvc.perform(get("/v1/me/dashboard").with(principal(uuid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections").isEmpty());
    }
}
