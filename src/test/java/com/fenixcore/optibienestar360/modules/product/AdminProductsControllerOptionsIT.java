package com.fenixcore.optibienestar360.modules.product;

import com.fenixcore.optibienestar360.core.dto.OptionDto;
import com.fenixcore.optibienestar360.modules.product.service.ProductsService;
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
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT of {@code GET /v1/admin/products/options} — flat {@code List<OptionDto>} for selects.
 * An inactive product passed via {@code currentValues} still surfaces ({@code active:false}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class AdminProductsControllerOptionsIT {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private ProductsService productsService;

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
        mockMvc.perform(get("/v1/admin/products/options"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void options_authenticatedWithoutScreenPermission_is200() throws Exception {
        // /options only needs a session (ADR LOC-0013, rule 3): no *_VIEW_ALL required.
        mockMvc.perform(get("/v1/admin/products/options").with(principal()))
                .andExpect(status().isOk());
    }

    @Test
    void options_returnsFlatListWithoutPageWrapper() throws Exception {
        UUID activeUuid = UUID.randomUUID();
        when(productsService.listOptions(isNull(), eq(50), isNull(), isNull()))
                .thenReturn(List.of(new OptionDto(activeUuid, "SKU-001", "SKU-001 — Consulta general", true)));

        mockMvc.perform(get("/v1/admin/products/options").with(principal("PRODUCT_VIEW_ALL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].uuid").value(activeUuid.toString()))
                .andExpect(jsonPath("$[0].active").value(true));
    }

    @Test
    void options_currentValues_inactiveItemStillAppears() throws Exception {
        UUID inactiveUuid = UUID.randomUUID();
        when(productsService.listOptions(any(), eq(50), eq(List.of(inactiveUuid)), isNull()))
                .thenReturn(List.of(new OptionDto(inactiveUuid, "OLD", "Discontinued", false)));

        mockMvc.perform(get("/v1/admin/products/options")
                        .param("currentValues", inactiveUuid.toString())
                        .with(principal("PRODUCT_VIEW_ALL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].active").value(false));
    }
}
