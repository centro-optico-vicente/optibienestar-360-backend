package com.fenixcore.optibienestar360.modules.auth;

import com.fenixcore.optibienestar360.modules.auth.service.RoleService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT del contrato HTTP de {@code PUT /v1/admin/roles/{uuid}/permissions}
 * ({@link AdminRoleController}): la autorización del endpoint y el mapeo de las
 * excepciones del {@link RoleService} a códigos HTTP.
 *
 * <p>El {@code RoleService} se mockea; que lance cada excepción por la razón
 * correcta (SYSTEM, permiso desconocido, auto-lockout) se prueba con lógica real
 * en {@code RoleServiceGuardsTest}. Acá se verifica que esas excepciones salgan
 * como el estado correcto y que el gate de permiso funcione. Se aplica
 * {@code springSecurity()} para ejercer la cadena de filtros real (distingue 401
 * anónimo de 403 autenticado-sin-permiso).</p>
 *
 * <p>Nota de códigos: el ítem del checklist decía 400 para "permiso inexistente"
 * y "auto-lockout", pero el service lanza {@code IllegalArgumentException}, que
 * {@code GlobalExceptionHandler} mapea a <b>422</b>.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class AdminRoleControllerIT {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private RoleService roleService;

    private MockMvc mockMvc;

    private static final String URL = "/v1/admin/roles/{uuid}/permissions";
    private static final String BODY = "{\"permissionUuids\":[\"" + UUID.randomUUID() + "\"]}";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    /** Authenticated principal holding the given authorities, as a JWT-derived CustomUserDetails. */
    private RequestPostProcessor actorWith(String... authorities) {
        CustomUserDetails principal = CustomUserDetails.fromJwt(
                UUID.randomUUID(), UUID.randomUUID().toString(), "es",
                List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList());
        return authentication(new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder putPermissions() {
        return put(URL, UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(BODY);
    }

    @Test
    void anonymous_is_401() throws Exception {
        mockMvc.perform(putPermissions())
                .andExpect(status().isUnauthorized());
        verify(roleService, never()).updateRolePermissions(any(), any(), any());
    }

    @Test
    void authenticated_without_role_permission_edit_is_403() throws Exception {
        mockMvc.perform(putPermissions().with(actorWith("ROLE_VIEW")))
                .andExpect(status().isForbidden());
        // The gate rejects before the service is touched.
        verify(roleService, never()).updateRolePermissions(any(), any(), any());
    }

    @Test
    void valid_update_returns_204() throws Exception {
        // Void service method: the default mock does nothing → success path.
        mockMvc.perform(putPermissions().with(actorWith("ROLE_PERMISSION_EDIT")))
                .andExpect(status().isNoContent());
        verify(roleService).updateRolePermissions(any(), any(), any());
    }

    @Test
    void editing_the_system_role_maps_to_403() throws Exception {
        doThrow(new AccessDeniedException("role.system.not_editable"))
                .when(roleService).updateRolePermissions(any(), any(), any());

        mockMvc.perform(putPermissions().with(actorWith("ROLE_PERMISSION_EDIT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknown_permission_maps_to_422() throws Exception {
        doThrow(new IllegalArgumentException("role.permission.uuid.unknown"))
                .when(roleService).updateRolePermissions(any(), any(), any());

        mockMvc.perform(putPermissions().with(actorWith("ROLE_PERMISSION_EDIT")))
                .andExpect(status().is(422));
    }

    @Test
    void self_lockout_maps_to_422() throws Exception {
        doThrow(new IllegalArgumentException("role.auto_lockout"))
                .when(roleService).updateRolePermissions(any(), any(), any());

        mockMvc.perform(putPermissions().with(actorWith("ROLE_PERMISSION_EDIT")))
                .andExpect(status().is(422));
    }
}
