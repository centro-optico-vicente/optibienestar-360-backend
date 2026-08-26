package com.fenixcore.optibienestar360.modules.auth;

import com.fenixcore.optibienestar360.modules.auth.dto.PermissionDomainDto;
import com.fenixcore.optibienestar360.modules.auth.dto.PermissionDto;
import com.fenixcore.optibienestar360.modules.auth.service.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT del contrato de {@code GET /v1/admin/permissions} ({@link PermissionController}).
 *
 * <p><b>Alcance:</b> la <i>autorización</i> del endpoint (401 anónimo / 403 sin
 * {@code ROLE_PERMISSION_EDIT} / 200 con el permiso) y la <i>forma</i> de la
 * respuesta (dominios en el orden que entrega el service, con sus permisos
 * anidados). El {@link PermissionService} se mockea a propósito: el perfil
 * {@code test} corre sobre H2 con Flyway apagado (ver
 * {@code application-test.properties}), así que el seed de dominios/permisos no
 * existe — ni local ni en CI, porque todos los {@code @SpringBootTest} fijan
 * {@code @ActiveProfiles("test")}. Los conteos del seed (hoy 13 dominios y el
 * catálogo de permisos que crece con cada migración) se validan al arrancar la
 * app contra Postgres real, no acá; hardcodearlos haría el test frágil.</p>
 *
 * <p>Spring Boot 4 eliminó {@code @WebMvcTest}/{@code @AutoConfigureMockMvc}: se
 * arma el {@link MockMvc} a mano desde el {@link WebApplicationContext}. A
 * diferencia de {@code GlobalExceptionHandlerIT}, acá SÍ se aplica
 * {@code springSecurity()} para ejercer la cadena de filtros real — es lo que
 * distingue el 401 (entry point, request sin autenticar) del 403 (method
 * security niega a un usuario autenticado sin el permiso).</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class PermissionControllerIT {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private PermissionService permissionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    /** Two domains, already ordered by displayOrder, each with its permissions. */
    private List<PermissionDomainDto> fixture() {
        return List.of(
                new PermissionDomainDto(UUID.randomUUID(), "USERS", "Usuarios", "i-lucide-users",
                        "Gestión de usuarios", 10,
                        List.of(
                                new PermissionDto(UUID.randomUUID(), "USER_VIEW_ALL", "Ver usuarios", "Listar y ver el detalle"),
                                new PermissionDto(UUID.randomUUID(), "USER_CREATE", "Crear usuarios", null))),
                new PermissionDomainDto(UUID.randomUUID(), "MEMBERS", "Afiliados", "i-lucide-id-card",
                        "Gestión de afiliados", 20,
                        List.of(
                                new PermissionDto(UUID.randomUUID(), "MEMBER_VIEW_ALL", "Ver afiliados", null)))
        );
    }

    @Test
    void anonymous_request_is_401_and_never_touches_the_service() throws Exception {
        mockMvc.perform(get("/v1/admin/permissions"))
                .andExpect(status().isUnauthorized());

        verify(permissionService, never()).getCatalog();
    }

    @Test
    void authenticated_without_role_permission_edit_is_403_and_never_touches_the_service() throws Exception {
        mockMvc.perform(get("/v1/admin/permissions")
                        .with(user("operador").authorities(new SimpleGrantedAuthority("USER_VIEW_ALL"))))
                .andExpect(status().isForbidden());

        // Authorization must reject before any work runs.
        verify(permissionService, never()).getCatalog();
    }

    @Test
    void with_role_permission_edit_returns_200_with_domains_ordered_by_display_order() throws Exception {
        when(permissionService.getCatalog()).thenReturn(fixture());

        mockMvc.perform(get("/v1/admin/permissions")
                        .with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_PERMISSION_EDIT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                // Order preserved: the domain the service returned first stays first.
                .andExpect(jsonPath("$[0].code").value("USERS"))
                .andExpect(jsonPath("$[0].displayOrder").value(10))
                .andExpect(jsonPath("$[1].code").value("MEMBERS"))
                .andExpect(jsonPath("$[1].displayOrder").value(20))
                // Permissions nested under their domain, both technical code and label exposed.
                .andExpect(jsonPath("$[0].permissions.length()").value(2))
                .andExpect(jsonPath("$[0].permissions[0].code").value("USER_VIEW_ALL"))
                .andExpect(jsonPath("$[0].permissions[0].name").value("Ver usuarios"))
                .andExpect(jsonPath("$[1].permissions.length()").value(1));

        verify(permissionService).getCatalog();
    }
}
