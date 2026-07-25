package com.fenixcore.optibienestar360.modules.ally;

import com.fenixcore.optibienestar360.modules.ally.dto.PublicAllyServiceDto;
import com.fenixcore.optibienestar360.modules.ally.dto.PublicServiceListItemDto;
import com.fenixcore.optibienestar360.modules.ally.service.PublicServicesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT del contrato público de servicios: {@code GET /v1/public/services}
 * (cross-ally, {@link PublicServiceController}) y
 * {@code GET /v1/public/allies/{uuid}/services} (per-ally,
 * {@link PublicAllyController}). Ambos delegan en {@link PublicServicesService},
 * que se mockea acá.
 *
 * <p><b>Alcance:</b> que ambos endpoints sean realmente <i>públicos</i> (200 sin
 * ningún JWT), que el resultado cross-ally traiga la identidad del aliado
 * aplanada, y que el path per-ally mapee a 404 vía {@code GlobalExceptionHandler}
 * cuando el aliado no es visible. El seed no existe bajo el perfil {@code test}
 * (H2 + Flyway off), por eso el service se mockea; el filtrado real por
 * Specification se valida en CI contra Postgres. Se aplica {@code springSecurity()}
 * para ejercer la cadena de filtros y demostrar que {@code /v1/public/**} pasa
 * por {@code permitAll()} y no cae en el entry point 401.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class PublicServiceControllerIT {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private PublicServicesService publicServicesService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    private PublicServiceListItemDto crossAllyRow() {
        return new PublicServiceListItemDto(
                UUID.randomUUID(), "Optometría", "Examen de la vista",
                "Evaluación optométrica completa", new BigDecimal("15.00"), null, true,
                UUID.randomUUID(), "Óptica Vicente", "Óptica", "Mérida",
                "https://cdn.example/logo.png", "+58 274 5550100");
    }

    private PublicAllyServiceDto leanRow() {
        return new PublicAllyServiceDto(
                UUID.randomUUID(), "Optometría", "Examen de la vista",
                "Evaluación optométrica completa", new BigDecimal("15.00"), null, true);
    }

    // ─── /v1/public/services (cross-ally) ────────────────────────────────────

    @Test
    void search_isPublic_returns200WithoutToken_andCarriesAllyIdentity() throws Exception {
        when(publicServicesService.search(any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(crossAllyRow())));

        // No .with(user(...)) — a truly anonymous request must succeed.
        mockMvc.perform(get("/v1/public/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Examen de la vista"))
                .andExpect(jsonPath("$.content[0].categoryName").value("Optometría"))
                // The parent-ally identity travels with each row.
                .andExpect(jsonPath("$.content[0].allyName").value("Óptica Vicente"))
                .andExpect(jsonPath("$.content[0].allyCityName").value("Mérida"));
    }

    // ─── /v1/public/allies/{uuid}/services (per-ally) ────────────────────────

    @Test
    void listByAlly_isPublic_returns200WithoutToken() throws Exception {
        when(publicServicesService.listByAlly(any(UUID.class), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(leanRow())));

        mockMvc.perform(get("/v1/public/allies/{uuid}/services", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Examen de la vista"))
                // Lean per-ally DTO carries no ally identity (the ally is the path).
                .andExpect(jsonPath("$.content[0].allyName").doesNotExist());
    }

    @Test
    void listByAlly_returns404_whenAllyIsNotPubliclyVisible() throws Exception {
        when(publicServicesService.listByAlly(any(UUID.class), any(), any(), any(Pageable.class)))
                .thenThrow(new NoSuchElementException("ally.not_found"));

        mockMvc.perform(get("/v1/public/allies/{uuid}/services", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
