package com.fenixcore.optibienestar360.modules.payment;

import com.fenixcore.optibienestar360.core.dto.OptionDto;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentMethodDto;
import com.fenixcore.optibienestar360.modules.payment.service.PaymentCategoryService;
import com.fenixcore.optibienestar360.modules.payment.service.PaymentMethodService;
import org.springframework.data.domain.PageImpl;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Server-side search of the self-service payment catalogs and the extra {@code /options} filters:
 * {@code GET /v1/me/payment-methods?q=} (full DTO with the mandatory flags) and
 * {@code GET /v1/admin/payment-categories/options?direction=}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class MyPaymentCatalogsSearchIT {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private PaymentMethodService paymentMethodService;

    @MockitoBean
    private PaymentCategoryService paymentCategoryService;

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
    void paymentMethods_anonymous_is401() throws Exception {
        mockMvc.perform(get("/v1/me/payment-methods")).andExpect(status().isUnauthorized());
    }

    @Test
    void paymentMethods_withoutQuery_returnsTheCachedFullList() throws Exception {
        when(paymentMethodService.loadAllForDropdown()).thenReturn(List.of());

        mockMvc.perform(get("/v1/me/payment-methods").with(principal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        verify(paymentMethodService, never()).list(any(), any(), any(), anyBoolean());
    }

    @Test
    void paymentMethods_withQuery_searchesOnTheServer() throws Exception {
        when(paymentMethodService.list(any(), isNull(), eq("pago"), eq(false))).thenReturn(new PageImpl<PaymentMethodDto>(List.of()));

        mockMvc.perform(get("/v1/me/payment-methods").param("q", "pago").with(principal()))
                .andExpect(status().isOk());
        verify(paymentMethodService).list(any(), isNull(), eq("pago"), eq(false));
    }

    @Test
    void paymentCategoryOptions_passesTheDirectionFilter() throws Exception {
        UUID uuid = UUID.randomUUID();
        when(paymentCategoryService.listOptions(isNull(), eq(50), isNull(), eq("OUT")))
                .thenReturn(List.of(new OptionDto(uuid, "COMMISSION_REGULAR", "Comisión directa regular", true)));

        mockMvc.perform(get("/v1/admin/payment-categories/options").param("direction", "OUT").with(principal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].uuid").value(uuid.toString()));
    }
}
