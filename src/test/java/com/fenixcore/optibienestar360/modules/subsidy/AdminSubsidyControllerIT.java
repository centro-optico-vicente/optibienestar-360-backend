package com.fenixcore.optibienestar360.modules.subsidy;

import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.modules.subsidy.dto.SubsidyDto;
import com.fenixcore.optibienestar360.modules.subsidy.service.SubsidiesService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT of {@link AdminSubsidyController} (V41): reads gated by
 * {@code SUBSIDY_VIEW_ALL}. Mutations are granular per V78: {@code SUBSIDY_CREATE} /
 * {@code SUBSIDY_UPDATE} / {@code SUBSIDY_DELETE}. Service mocked.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class AdminSubsidyControllerIT {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private SubsidiesService service;

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

    private SubsidyDto dto() {
        return new SubsidyDto(UUID.randomUUID(),
                new DisplayRef(UUID.randomUUID(), null, "Ana Pérez"),
                new BigDecimal("100"), null, null, "Fundación X", UUID.randomUUID(),
                LocalDate.of(2026, 1, 1), null, true, null, null, null, List.of());
    }

    private static final String VALID_CREATE_JSON = """
            {"memberUuid":"11111111-1111-1111-1111-111111111111","monthlyPercentage":100,
             "reason":"Fundación X","validFrom":"2026-01-01"}
            """;

    // ─── list (read, VIEW_ALL) ───────────────────────────────────────────────

    @Test
    void list_anonymous_is401() throws Exception {
        mockMvc.perform(get("/v1/admin/subsidies")).andExpect(status().isUnauthorized());
    }

    @Test
    void list_withoutPermission_is403() throws Exception {
        mockMvc.perform(get("/v1/admin/subsidies").with(principal("SUBSIDY_CREATE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_withPermission_is200() throws Exception {
        when(service.list(any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of(dto())));

        mockMvc.perform(get("/v1/admin/subsidies").with(principal("SUBSIDY_VIEW_ALL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    // ─── create (write, CREATE) ──────────────────────────────────────────────

    @Test
    void create_withoutPermission_is403() throws Exception {
        mockMvc.perform(post("/v1/admin/subsidies")
                        .with(principal("SUBSIDY_VIEW_ALL"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE_JSON))
                .andExpect(status().isForbidden());
        verify(service, never()).create(any(), any());
    }

    @Test
    void create_withPermission_is201() throws Exception {
        when(service.create(any(), any())).thenReturn(dto());

        mockMvc.perform(post("/v1/admin/subsidies")
                        .with(principal("SUBSIDY_CREATE"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.monthlyPercentage").value(100));
    }

    @Test
    void create_blankReason_is400() throws Exception {
        String badJson = """
                {"memberUuid":"11111111-1111-1111-1111-111111111111","monthlyPercentage":100,
                 "reason":"  ","validFrom":"2026-01-01"}
                """;
        mockMvc.perform(post("/v1/admin/subsidies")
                        .with(principal("SUBSIDY_CREATE"))
                        .contentType(MediaType.APPLICATION_JSON).content(badJson))
                .andExpect(status().isBadRequest());
        verify(service, never()).create(any(), any());
    }

    // ─── revoke (write, DELETE) ──────────────────────────────────────────────

    @Test
    void revoke_withPermission_is204() throws Exception {
        mockMvc.perform(delete("/v1/admin/subsidies/{u}", UUID.randomUUID())
                        .with(principal("SUBSIDY_DELETE")))
                .andExpect(status().isNoContent());
        verify(service).revoke(any(), any());
    }

    @Test
    void revoke_withoutPermission_is403() throws Exception {
        mockMvc.perform(delete("/v1/admin/subsidies/{u}", UUID.randomUUID())
                        .with(principal("SUBSIDY_VIEW_ALL")))
                .andExpect(status().isForbidden());
        verify(service, never()).revoke(any(), any());
    }

    // ─── audit log (read, VIEW_ALL) ──────────────────────────────────────────

    @Test
    void getLog_withPermission_is200() throws Exception {
        when(service.getLog(any())).thenReturn(List.of());

        mockMvc.perform(get("/v1/admin/subsidies/{u}/log", UUID.randomUUID())
                        .with(principal("SUBSIDY_VIEW_ALL")))
                .andExpect(status().isOk());
    }
}
