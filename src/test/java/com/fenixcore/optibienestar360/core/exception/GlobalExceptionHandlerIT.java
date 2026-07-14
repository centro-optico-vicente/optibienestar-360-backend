package com.fenixcore.optibienestar360.core.exception;

import com.fenixcore.optibienestar360.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import java.util.Collections;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT del {@link GlobalExceptionHandler}: verifica que los payloads RFC 7807 se
 * localicen según la cadena del resolver (claim JWT > Accept-Language >
 * default es-VE).
 *
 * <p>Spring Boot 4 eliminó {@code @WebMvcTest} y {@code @AutoConfigureMockMvc};
 * se usa {@code @SpringBootTest} con MockMvc construido manualmente desde el
 * {@link WebApplicationContext}. Los filtros de Spring Security NO se aplican
 * (no se invoca {@code .apply(springSecurity())}), así que las rutas de test
 * llegan directo al dispatcher sin requerir auth. Para los tests que validan
 * el claim, el principal se inyecta directo al {@link SecurityContextHolder} —
 * el resolver lo lee igual.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Import(GlobalExceptionHandlerIT.TestThrowController.class)
class GlobalExceptionHandlerIT {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ─── Accept-Language drives localization when there is no auth ──────────

    @Test
    void not_found_with_spanish_header_returns_spanish_payload() throws Exception {
        mockMvc.perform(get("/test-throw/not-found").header("Accept-Language", "es"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("No encontrado"))
                .andExpect(jsonPath("$.detail").value("Usuario no encontrado"));
    }

    @Test
    void not_found_with_english_header_returns_english_payload() throws Exception {
        mockMvc.perform(get("/test-throw/not-found").header("Accept-Language", "en"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.detail").value("User not found"));
    }

    @Test
    void not_found_without_header_falls_to_default_spanish_payload() throws Exception {
        mockMvc.perform(get("/test-throw/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Usuario no encontrado"));
    }

    @Test
    void not_found_with_unsupported_header_falls_to_default_spanish_payload() throws Exception {
        // pt-BR no está en la whitelist → resolver cae al default es-VE.
        mockMvc.perform(get("/test-throw/not-found").header("Accept-Language", "pt-BR"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Usuario no encontrado"));
    }

    // ─── JWT locale claim wins over Accept-Language header ──────────────────

    @Test
    void user_locale_en_wins_over_spanish_accept_language_header() throws Exception {
        authenticateWithLocaleClaim("en");

        mockMvc.perform(get("/test-throw/not-found").header("Accept-Language", "es-VE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.detail").value("User not found"));
    }

    // ─── 403 + 422 paths also localize through the same chain ───────────────

    @Test
    void access_denied_returns_localized_403_in_english() throws Exception {
        mockMvc.perform(get("/test-throw/access-denied").header("Accept-Language", "en"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.detail").value("Only SYSTEM users can edit the SYSTEM role"));
    }

    @Test
    void illegal_argument_returns_localized_422_in_spanish() throws Exception {
        mockMvc.perform(get("/test-throw/illegal-argument").header("Accept-Language", "es"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("Entidad no procesable"))
                .andExpect(jsonPath("$.detail").value("Uno o más permisos no existen"));
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private void authenticateWithLocaleClaim(String locale) {
        CustomUserDetails principal = CustomUserDetails.fromJwt(
                UUID.randomUUID(),
                UUID.randomUUID().toString(),
                locale,
                Collections.emptyList()
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList())
        );
    }

    // ─── test controller — solo existe en este IT ──────────────────────────

    @RestController
    @RequestMapping("/test-throw")
    static class TestThrowController {

        @GetMapping("/not-found")
        public void notFound() {
            throw new NoSuchElementException("user.not_found");
        }

        @GetMapping("/access-denied")
        public void accessDenied() {
            throw new AccessDeniedException("role.system.not_editable");
        }

        @GetMapping("/illegal-argument")
        public void illegalArgument() {
            throw new IllegalArgumentException("role.permission.uuid.unknown");
        }
    }
}
