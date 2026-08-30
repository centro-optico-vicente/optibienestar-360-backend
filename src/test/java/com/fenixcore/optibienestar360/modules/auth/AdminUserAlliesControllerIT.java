package com.fenixcore.optibienestar360.modules.auth;

import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.modules.ally.dto.UserAllyDto;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser.AllyRole;
import com.fenixcore.optibienestar360.modules.ally.service.AllyUsersService;
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

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT of {@code GET /v1/admin/users/{userUuid}/allies} — the reverse lookup of
 * {@code /v1/admin/allies/{allyUuid}/users}, backing the "ally memberships"
 * section of the user detail page. {@link AllyUsersService} is mocked; its
 * own business rules are exercised for real in {@code AllyUsersServiceTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class AdminUserAlliesControllerIT {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private AllyUsersService allyUsersService;

    private MockMvc mockMvc;

    private static final String URL = "/v1/admin/users/{userUuid}/allies";

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
    void anonymous_is401() throws Exception {
        mockMvc.perform(get(URL, UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void withoutAllyViewAll_is403() throws Exception {
        mockMvc.perform(get(URL, UUID.randomUUID()).with(principal("USER_VIEW_ALL")))
                .andExpect(status().isForbidden());
    }

    @Test
    void withAllyViewAll_returns200WithMemberships() throws Exception {
        UUID userUuid = UUID.randomUUID();
        UUID allyUuid = UUID.randomUUID();
        when(allyUsersService.listAlliesForUser(userUuid)).thenReturn(List.of(
                new UserAllyDto(new DisplayRef(allyUuid, null, "Optica Central"), AllyRole.STAFF, false,
                        LocalDate.of(2026, 1, 15), true)));

        mockMvc.perform(get(URL, userUuid).with(principal("ALLY_VIEW_ALL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].ally_Uuid").value(allyUuid.toString()))
                .andExpect(jsonPath("$[0].ally_Display").value("Optica Central"))
                .andExpect(jsonPath("$[0].allyRole").value("STAFF"))
                .andExpect(jsonPath("$[0].primary").value(false))
                .andExpect(jsonPath("$[0].active").value(true));
    }

    @Test
    void withNoMemberships_returnsEmptyList() throws Exception {
        UUID userUuid = UUID.randomUUID();
        when(allyUsersService.listAlliesForUser(userUuid)).thenReturn(List.of());

        mockMvc.perform(get(URL, userUuid).with(principal("ALLY_VIEW_ALL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void unknownUser_maps404() throws Exception {
        UUID userUuid = UUID.randomUUID();
        when(allyUsersService.listAlliesForUser(userUuid))
                .thenThrow(new NoSuchElementException("user.not_found"));

        mockMvc.perform(get(URL, userUuid).with(principal("ALLY_VIEW_ALL")))
                .andExpect(status().isNotFound());
    }
}
