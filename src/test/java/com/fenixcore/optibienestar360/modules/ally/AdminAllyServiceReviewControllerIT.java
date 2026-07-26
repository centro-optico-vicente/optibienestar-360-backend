package com.fenixcore.optibienestar360.modules.ally;

import com.fenixcore.optibienestar360.modules.ally.dto.AllyServiceDto;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyServiceReviewLogDto;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyService.ReviewStatus;
import com.fenixcore.optibienestar360.modules.ally.service.AllyServiceReviewService;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT of the admin ally-service review contract (v2 PDF #6,
 * {@link AdminAllyServiceReviewController}): the queue + transitions are gated by
 * {@code ALLY_SERVICE_APPROVE}, the log read by {@code ALLY_VIEW_ALL}. The
 * service is mocked.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class AdminAllyServiceReviewControllerIT {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private AllyServiceReviewService reviewService;

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

    private AllyServiceDto serviceDto(ReviewStatus status) {
        return new AllyServiceDto(UUID.randomUUID(), UUID.randomUUID(), null,
                "Consulta oftalmológica", null, null, null, false,
                status, null, null, null, false, null, true, null, null, null);
    }

    // ─── pending queue (ALLY_SERVICE_APPROVE) ────────────────────────────────

    @Test
    void pending_anonymous_is401() throws Exception {
        mockMvc.perform(get("/v1/admin/ally-services/pending"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pending_withoutPermission_is403() throws Exception {
        mockMvc.perform(get("/v1/admin/ally-services/pending").with(principal("ALLY_VIEW_ALL")))
                .andExpect(status().isForbidden());
    }

    @Test
    void pending_withPermission_is200() throws Exception {
        when(reviewService.pendingQueue(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(serviceDto(ReviewStatus.PROPOSED))));

        mockMvc.perform(get("/v1/admin/ally-services/pending").with(principal("ALLY_SERVICE_APPROVE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    // ─── approve / reject / remove (ALLY_SERVICE_APPROVE) ────────────────────

    @Test
    void approve_withoutPermission_is403() throws Exception {
        mockMvc.perform(post("/v1/admin/ally-services/{u}/approve", UUID.randomUUID())
                        .with(principal("ALLY_VIEW_ALL"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        verify(reviewService, never()).approve(any(), any(), any());
    }

    @Test
    void approve_withPermission_is200() throws Exception {
        when(reviewService.approve(any(), any(), any())).thenReturn(serviceDto(ReviewStatus.APPROVED));

        mockMvc.perform(post("/v1/admin/ally-services/{u}/approve", UUID.randomUUID())
                        .with(principal("ALLY_SERVICE_APPROVE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"ok\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("APPROVED"));
    }

    @Test
    void reject_withPermission_is200() throws Exception {
        when(reviewService.reject(any(), any(), any())).thenReturn(serviceDto(ReviewStatus.REJECTED));

        mockMvc.perform(post("/v1/admin/ally-services/{u}/reject", UUID.randomUUID())
                        .with(principal("ALLY_SERVICE_APPROVE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"incomplete\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("REJECTED"));
    }

    @Test
    void reject_blankReason_is400() throws Exception {
        mockMvc.perform(post("/v1/admin/ally-services/{u}/reject", UUID.randomUUID())
                        .with(principal("ALLY_SERVICE_APPROVE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"  \"}"))
                .andExpect(status().isBadRequest());
        verify(reviewService, never()).reject(any(), any(), any());
    }

    @Test
    void remove_withPermission_is200() throws Exception {
        when(reviewService.adminRemove(any(), any(), any())).thenReturn(serviceDto(ReviewStatus.REMOVED));

        mockMvc.perform(post("/v1/admin/ally-services/{u}/remove", UUID.randomUUID())
                        .with(principal("ALLY_SERVICE_APPROVE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"contract ended\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("REMOVED"));
    }

    // ─── log (ALLY_VIEW_ALL) ─────────────────────────────────────────────────

    @Test
    void log_withViewPermission_is200() throws Exception {
        when(reviewService.adminLog(any())).thenReturn(List.of(
                new AllyServiceReviewLogDto(ReviewStatus.PROPOSED, ReviewStatus.APPROVED,
                        UUID.randomUUID(), Instant.parse("2026-07-25T12:00:00Z"), "ok")));

        mockMvc.perform(get("/v1/admin/ally-services/{u}/log", UUID.randomUUID())
                        .with(principal("ALLY_VIEW_ALL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].toStatus").value("APPROVED"));
    }

    @Test
    void log_withoutViewPermission_is403() throws Exception {
        mockMvc.perform(get("/v1/admin/ally-services/{u}/log", UUID.randomUUID())
                        .with(principal("ALLY_SERVICE_APPROVE")))
                .andExpect(status().isForbidden());
        verify(reviewService, never()).adminLog(any());
    }
}
