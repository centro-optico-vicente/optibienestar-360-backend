package com.fenixcore.optibienestar360.modules.member;

import com.fenixcore.optibienestar360.core.dto.OptionDto;
import com.fenixcore.optibienestar360.modules.member.service.MembersService;
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
 * IT of {@code GET /v1/admin/members/options} — the typeahead surface used by
 * memberships/payments/promoter forms. Verifies the flat {@code List<OptionDto>}
 * shape and that an inactive member passed via {@code currentValues} still
 * surfaces (with {@code active:false}) instead of being silently dropped.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class AdminMemberControllerOptionsIT {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private MembersService membersService;

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
        mockMvc.perform(get("/v1/admin/members/options"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void options_withoutPermission_is403() throws Exception {
        mockMvc.perform(get("/v1/admin/members/options").with(principal("USER_VIEW_ALL")))
                .andExpect(status().isForbidden());
    }

    @Test
    void options_returnsFlatListWithoutPageWrapper() throws Exception {
        UUID activeUuid = UUID.randomUUID();
        when(membersService.listOptions(any(), eq(50), any()))
                .thenReturn(List.of(new OptionDto(activeUuid, "12345678", "Juan Pérez", true)));

        mockMvc.perform(get("/v1/admin/members/options").param("q", "juan").with(principal("MEMBER_VIEW_ALL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("12345678"))
                .andExpect(jsonPath("$[0].label").value("Juan Pérez"));
    }

    @Test
    void options_currentValues_inactiveMemberStillAppears() throws Exception {
        UUID inactiveUuid = UUID.randomUUID();
        when(membersService.listOptions(any(), eq(50), eq(List.of(inactiveUuid))))
                .thenReturn(List.of(new OptionDto(inactiveUuid, "99999999", "Ex Miembro", false)));

        mockMvc.perform(get("/v1/admin/members/options")
                        .param("currentValues", inactiveUuid.toString())
                        .with(principal("MEMBER_VIEW_ALL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].active").value(false));
    }
}
