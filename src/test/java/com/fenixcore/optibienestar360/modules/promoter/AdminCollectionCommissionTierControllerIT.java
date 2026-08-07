package com.fenixcore.optibienestar360.modules.promoter;

import com.fenixcore.optibienestar360.modules.promoter.dto.CollectionCommissionTierDto;
import com.fenixcore.optibienestar360.modules.promoter.service.CollectionCommissionTiersService;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT covering authorization + CRUD wiring of the collection-commission-tier
 * admin surface (ADR 0013 §3, V44), gated by {@code COLLECTION_COMMISSION_TIER_MANAGE}.
 * Service mocked.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class AdminCollectionCommissionTierControllerIT {

    @Autowired private WebApplicationContext context;

    @MockitoBean private CollectionCommissionTiersService service;

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
    void list_anonymous_is401() throws Exception {
        mockMvc.perform(get("/v1/admin/collection-commission-tiers")).andExpect(status().isUnauthorized());
    }

    @Test
    void list_withoutPermission_is403() throws Exception {
        mockMvc.perform(get("/v1/admin/collection-commission-tiers").with(principal("COMMISSION_TIER_MANAGE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_withPermission_is200() throws Exception {
        when(service.list(any(), any(), any(), anyBoolean())).thenReturn(new PageImpl<>(List.of()));
        mockMvc.perform(get("/v1/admin/collection-commission-tiers").with(principal("COLLECTION_COMMISSION_TIER_MANAGE")))
                .andExpect(status().isOk());
    }

    @Test
    void create_withPermission_is201() throws Exception {
        when(service.create(any())).thenReturn(tierDto());
        String json = """
                {"name":"Hasta 5 días","maxDays":5,"commissionPct":35}
                """;
        mockMvc.perform(post("/v1/admin/collection-commission-tiers").with(principal("COLLECTION_COMMISSION_TIER_MANAGE"))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
    }

    @Test
    void update_withPermission_is200() throws Exception {
        when(service.update(any(), any())).thenReturn(tierDto());
        String json = """
                {"commissionPct":30}
                """;
        mockMvc.perform(put("/v1/admin/collection-commission-tiers/" + UUID.randomUUID())
                        .with(principal("COLLECTION_COMMISSION_TIER_MANAGE"))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());
    }

    @Test
    void delete_withPermission_is204() throws Exception {
        doNothing().when(service).delete(any());
        mockMvc.perform(delete("/v1/admin/collection-commission-tiers/" + UUID.randomUUID())
                        .with(principal("COLLECTION_COMMISSION_TIER_MANAGE")))
                .andExpect(status().isNoContent());
    }

    private static CollectionCommissionTierDto tierDto() {
        return new CollectionCommissionTierDto(UUID.randomUUID(), "Hasta 5 días", 5,
                new BigDecimal("35"), true, null, null, null);
    }
}
