package com.fenixcore.optibienestar360.modules.promoter;

import com.fenixcore.optibienestar360.modules.payment.PromoterMePaymentsController;
import com.fenixcore.optibienestar360.modules.payment.service.PaymentsService;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import com.fenixcore.optibienestar360.modules.terms.service.TermsAcceptanceService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT of the advisor self-service paths not covered by their own IT:
 * {@link PromoterMePaymentsController} and {@link PromoterQrController}.
 * {@code /v1/me/advisor/*} is the primary prefix and {@code /v1/advisors/me/*}
 * the secondary one; both must reach the same handler behind the same gate.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class AdvisorSelfServicePathsIT {

    @Autowired
    private WebApplicationContext context;

    /** Portal gate ({@code TermsGateInterceptor}) asks it for pending contracts; this IT has no real database. */
    @MockitoBean
    private TermsAcceptanceService termsAcceptanceService;

    @MockitoBean
    private PaymentsService paymentsService;

    @MockitoBean
    private PromoterRepository promoterRepository;

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

    // ─── payments ────────────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"/v1/me/advisor/payments", "/v1/advisors/me/payments"})
    void payments_anonymous_is401(String path) throws Exception {
        mockMvc.perform(get(path).param("direction", "IN"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/v1/me/advisor/payments", "/v1/advisors/me/payments"})
    void payments_withoutPermission_is403(String path) throws Exception {
        mockMvc.perform(get(path).param("direction", "IN").with(principal("MEMBER_VIEW_OWN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void payments_primaryAndSecondary_returnTheSameBody() throws Exception {
        when(paymentsService.listForPromoter(any(UUID.class), anyString(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        String primary = mockMvc.perform(get("/v1/me/advisor/payments").param("direction", "OUT")
                        .with(principal("PROMOTER_VIEW_OWN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String secondary = mockMvc.perform(get("/v1/advisors/me/payments").param("direction", "OUT")
                        .with(principal("PROMOTER_VIEW_OWN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(secondary).isEqualTo(primary);
    }

    // ─── qr ──────────────────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"/v1/me/advisor/qr", "/v1/advisors/me/qr"})
    void qr_anonymous_is401(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/v1/me/advisor/qr", "/v1/advisors/me/qr"})
    void qr_withoutPermission_is403(String path) throws Exception {
        mockMvc.perform(get(path).with(principal("MEMBER_VIEW_OWN")))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/v1/me/advisor/qr", "/v1/advisors/me/qr"})
    void qr_notAnAdvisor_is404(String path) throws Exception {
        when(promoterRepository.findActiveByUserUuid(any(UUID.class))).thenReturn(Optional.empty());

        mockMvc.perform(get(path).with(principal("PROMOTER_VIEW_OWN")))
                .andExpect(status().isNotFound());
    }
}
