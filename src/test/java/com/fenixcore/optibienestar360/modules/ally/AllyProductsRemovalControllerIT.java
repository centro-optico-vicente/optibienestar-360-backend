package com.fenixcore.optibienestar360.modules.ally;

import com.fenixcore.optibienestar360.modules.ally.dto.AllyProductDto;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyProductReviewLogDto;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyProduct.ReviewStatus;
import com.fenixcore.optibienestar360.modules.ally.service.AllyProductReviewService;
import com.fenixcore.optibienestar360.modules.product.entity.Product.ProductType;
import com.fenixcore.optibienestar360.modules.terms.service.TermsAcceptanceService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import com.fenixcore.optibienestar360.security.RoleNames;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
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
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT of the ally-owner self-service surface (v2 PDF #6, {@link AllyProductsController}):
 * {@code DELETE /v1/me/allies/products/{uuid}} (self-removal) + {@code GET .../log}.
 * Both are {@code isAuthenticated()} — the membership rule lives in the mocked
 * {@link AllyProductReviewService}; here we lock the auth gate + error mapping.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class AllyProductsRemovalControllerIT {

    @Autowired
    private WebApplicationContext context;

    /** Portal gate ({@code TermsGateInterceptor}) asks it for pending contracts; this IT has no real database. */
    @MockitoBean
    private TermsAcceptanceService termsAcceptanceService;

    @MockitoBean
    private AllyProductReviewService reviewService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private RequestPostProcessor principal() {
        CustomUserDetails p = CustomUserDetails.fromJwt(UUID.randomUUID(), "jti", "es",
                List.of(new SimpleGrantedAuthority(RoleNames.ALLY)));
        return authentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }

    private AllyProductDto removedDto() {
        return new AllyProductDto(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), ProductType.SERVICE, true, null, "Consulta", null, false, null,
                null, null, null,
                ReviewStatus.REMOVED, null, null, "Retirado por el aliado", false, null, true, null, null, null);
    }

    // ─── DELETE (self-removal) ────────────────────────────────────────────────

    @Test
    void delete_anonymous_is401() throws Exception {
        mockMvc.perform(delete("/v1/me/allies/products/{u}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void delete_authenticatedOwner_is200() throws Exception {
        when(reviewService.allyRemove(any(), any(), any())).thenReturn(removedDto());

        mockMvc.perform(delete("/v1/me/allies/products/{u}", UUID.randomUUID()).with(principal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("REMOVED"));
    }

    @Test
    void delete_notAMember_is403() throws Exception {
        when(reviewService.allyRemove(any(), any(), any()))
                .thenThrow(new AccessDeniedException("ally_product.remove.not_allowed"));

        mockMvc.perform(delete("/v1/me/allies/products/{u}", UUID.randomUUID()).with(principal()))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_unknownService_is404() throws Exception {
        when(reviewService.allyRemove(any(), any(), any()))
                .thenThrow(new NoSuchElementException("ally_product.not_found"));

        mockMvc.perform(delete("/v1/me/allies/products/{u}", UUID.randomUUID()).with(principal()))
                .andExpect(status().isNotFound());
    }

    // ─── GET log ──────────────────────────────────────────────────────────────

    @Test
    void log_anonymous_is401() throws Exception {
        mockMvc.perform(get("/v1/me/allies/products/{u}/log", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void log_authenticatedMember_is200() throws Exception {
        when(reviewService.allyLog(any(), any())).thenReturn(List.of(
                new AllyProductReviewLogDto(ReviewStatus.APPROVED, ReviewStatus.REMOVED,
                        UUID.randomUUID(), Instant.parse("2026-07-25T12:00:00Z"), "Retirado por el aliado")));

        mockMvc.perform(get("/v1/me/allies/products/{u}/log", UUID.randomUUID()).with(principal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].toStatus").value("REMOVED"));
    }

    @Test
    void log_foreignService_is404() throws Exception {
        when(reviewService.allyLog(any(), any()))
                .thenThrow(new NoSuchElementException("ally_product.not_found"));

        mockMvc.perform(get("/v1/me/allies/products/{u}/log", UUID.randomUUID()).with(principal()))
                .andExpect(status().isNotFound());
    }

    // ─── Deprecated .../services alias (ADR 0026 transition) ─────────────────

    @Test
    void deprecatedServicesAlias_stillAnswers_withSameSecurity() throws Exception {
        when(reviewService.allyRemove(any(), any(), any())).thenReturn(removedDto());

        mockMvc.perform(delete("/v1/me/allies/services/{u}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/v1/me/allies/services/{u}", UUID.randomUUID()).with(principal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("REMOVED"));
    }

    // ─── Secondary path /v1/allies/me/products (kept, not deprecated) ────────

    @Test
    void secondaryAlliesMePath_answersLikePrimary_withSameSecurity() throws Exception {
        when(reviewService.allyRemove(any(), any(), any())).thenReturn(removedDto());
        when(reviewService.allyLog(any(), any())).thenReturn(List.of());

        mockMvc.perform(delete("/v1/allies/me/products/{u}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/v1/allies/me/products/{u}", UUID.randomUUID()).with(principal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("REMOVED"));
        mockMvc.perform(get("/v1/allies/me/products/{u}/log", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/allies/me/products/{u}/log", UUID.randomUUID()).with(principal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
