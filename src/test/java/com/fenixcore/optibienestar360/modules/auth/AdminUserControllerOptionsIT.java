package com.fenixcore.optibienestar360.modules.auth;

import com.fenixcore.optibienestar360.core.dto.OptionDto;
import com.fenixcore.optibienestar360.modules.auth.service.UserService;
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
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT of {@code GET /v1/admin/users/options} — the typeahead surface used by
 * {@code PromoterFormModal.vue}. Verifies the flat {@code List<OptionDto>}
 * shape (no {@code code} — User has none) and that an inactive user passed via
 * {@code currentValues} still surfaces with {@code active:false}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class AdminUserControllerOptionsIT {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private UserService userService;

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
        mockMvc.perform(get("/v1/admin/users/options"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void options_withoutPermission_is403() throws Exception {
        mockMvc.perform(get("/v1/admin/users/options").with(principal("PLAN_VIEW_ALL")))
                .andExpect(status().isForbidden());
    }

    @Test
    void options_returnsFlatListWithoutPageWrapper() throws Exception {
        UUID activeUuid = UUID.randomUUID();
        when(userService.listOptions(any(), eq(50), any()))
                .thenReturn(List.of(new OptionDto(activeUuid, null, "Ana Gómez", true)));

        mockMvc.perform(get("/v1/admin/users/options").param("q", "ana").with(principal("USER_VIEW_ALL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$.totalElements").doesNotExist())
                .andExpect(jsonPath("$[0].code").doesNotExist())
                .andExpect(jsonPath("$[0].label").value("Ana Gómez"));
    }

    @Test
    void options_currentValues_inactiveUserStillAppears() throws Exception {
        UUID inactiveUuid = UUID.randomUUID();
        when(userService.listOptions(any(), eq(50), eq(List.of(inactiveUuid))))
                .thenReturn(List.of(new OptionDto(inactiveUuid, null, "Ex Usuario", false)));

        mockMvc.perform(get("/v1/admin/users/options")
                        .param("currentValues", inactiveUuid.toString())
                        .with(principal("USER_VIEW_ALL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].active").value(false));
    }
}
