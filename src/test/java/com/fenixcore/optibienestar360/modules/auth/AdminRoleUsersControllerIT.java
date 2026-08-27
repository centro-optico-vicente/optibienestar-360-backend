package com.fenixcore.optibienestar360.modules.auth;

import com.fenixcore.optibienestar360.modules.auth.dto.RoleUserDto;
import com.fenixcore.optibienestar360.modules.auth.service.RoleService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
 * IT of the HTTP contract for {@code /v1/admin/roles/{roleUuid}/users}
 * ({@link AdminRoleUsersController}): authorization gates and exception →
 * HTTP status mapping. {@link RoleService} is mocked; the business rules
 * behind assign/remove are exercised for real in
 * {@code RoleUsersServiceTest}. Mirrors {@code AdminRoleControllerIT}'s
 * pattern.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class AdminRoleUsersControllerIT {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private RoleService roleService;

    private MockMvc mockMvc;

    private static final String LIST_URL = "/v1/admin/roles/{roleUuid}/users";
    private static final String DELETE_URL = "/v1/admin/roles/{roleUuid}/users/{userUuid}";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    private RequestPostProcessor actorWith(String... authorities) {
        CustomUserDetails principal = CustomUserDetails.fromJwt(
                UUID.randomUUID(), UUID.randomUUID().toString(), "es",
                List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList());
        return authentication(new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()));
    }

    private String assignBody(UUID userUuid) {
        return "{\"userUuid\":\"" + userUuid + "\"}";
    }

    // ─── GET (ROLE_USER_VIEW_ALL) ───────────────────────────────────────────

    @Test
    void anonymous_list_is_401() throws Exception {
        mockMvc.perform(get(LIST_URL, UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
        verify(roleService, never()).listUsers(any());
    }

    @Test
    void list_withoutRoleUserViewAll_is403() throws Exception {
        mockMvc.perform(get(LIST_URL, UUID.randomUUID()).with(actorWith("ROLE_VIEW")))
                .andExpect(status().isForbidden());
        verify(roleService, never()).listUsers(any());
    }

    @Test
    void list_withRoleUserViewAll_returns200() throws Exception {
        UUID roleUuid = UUID.randomUUID();
        when(roleService.listUsers(roleUuid)).thenReturn(
                List.of(new RoleUserDto(UUID.randomUUID(), "a@b.com", "Ana Perez", "ACTIVE", true)));

        mockMvc.perform(get(LIST_URL, roleUuid).with(actorWith("ROLE_USER_VIEW_ALL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].active").value(true));
    }

    @Test
    void list_unknownRole_maps404() throws Exception {
        UUID roleUuid = UUID.randomUUID();
        when(roleService.listUsers(roleUuid)).thenThrow(new NoSuchElementException("role.not_found"));

        mockMvc.perform(get(LIST_URL, roleUuid).with(actorWith("ROLE_USER_VIEW_ALL")))
                .andExpect(status().isNotFound());
    }

    // ─── POST (ROLE_USER_CREATE) ────────────────────────────────────────────

    private MockHttpServletRequestBuilder postAssign(UUID roleUuid, UUID userUuid) {
        return post(LIST_URL, roleUuid).contentType(MediaType.APPLICATION_JSON).content(assignBody(userUuid));
    }

    @Test
    void anonymous_assign_is401() throws Exception {
        mockMvc.perform(postAssign(UUID.randomUUID(), UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
        verify(roleService, never()).assignUser(any(), any());
    }

    @Test
    void assign_withoutRoleUserCreate_is403() throws Exception {
        mockMvc.perform(postAssign(UUID.randomUUID(), UUID.randomUUID()).with(actorWith("USER_VIEW_ALL")))
                .andExpect(status().isForbidden());
        verify(roleService, never()).assignUser(any(), any());
    }

    @Test
    void assign_withRoleUserCreate_returns201() throws Exception {
        UUID roleUuid = UUID.randomUUID();
        UUID userUuid = UUID.randomUUID();

        mockMvc.perform(postAssign(roleUuid, userUuid).with(actorWith("ROLE_USER_CREATE")))
                .andExpect(status().isCreated());
        verify(roleService).assignUser(roleUuid, userUuid);
    }

    @Test
    void assign_unknownRoleOrUser_maps404() throws Exception {
        doThrow(new NoSuchElementException("user.not_found"))
                .when(roleService).assignUser(any(), any());

        mockMvc.perform(postAssign(UUID.randomUUID(), UUID.randomUUID()).with(actorWith("ROLE_USER_CREATE")))
                .andExpect(status().isNotFound());
    }

    // ─── DELETE (ROLE_USER_DELETE) ──────────────────────────────────────────

    @Test
    void anonymous_remove_is401() throws Exception {
        mockMvc.perform(delete(DELETE_URL, UUID.randomUUID(), UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
        verify(roleService, never()).removeUser(any(), any());
    }

    @Test
    void remove_withoutRoleUserDelete_is403() throws Exception {
        mockMvc.perform(delete(DELETE_URL, UUID.randomUUID(), UUID.randomUUID()).with(actorWith("USER_VIEW_ALL")))
                .andExpect(status().isForbidden());
        verify(roleService, never()).removeUser(any(), any());
    }

    @Test
    void remove_withRoleUserDelete_returns204() throws Exception {
        UUID roleUuid = UUID.randomUUID();
        UUID userUuid = UUID.randomUUID();

        mockMvc.perform(delete(DELETE_URL, roleUuid, userUuid).with(actorWith("ROLE_USER_DELETE")))
                .andExpect(status().isNoContent());
        verify(roleService).removeUser(roleUuid, userUuid);
    }

    @Test
    void remove_unknownRoleOrUser_maps404() throws Exception {
        doThrow(new NoSuchElementException("role.not_found"))
                .when(roleService).removeUser(any(), any());

        mockMvc.perform(delete(DELETE_URL, UUID.randomUUID(), UUID.randomUUID()).with(actorWith("ROLE_USER_DELETE")))
                .andExpect(status().isNotFound());
    }
}
