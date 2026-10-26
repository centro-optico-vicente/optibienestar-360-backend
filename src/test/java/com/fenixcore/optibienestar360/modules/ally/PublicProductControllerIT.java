package com.fenixcore.optibienestar360.modules.ally;

import com.fenixcore.optibienestar360.modules.ally.dto.PublicAllyProductDto;
import com.fenixcore.optibienestar360.modules.ally.dto.PublicProductDetailDto;
import com.fenixcore.optibienestar360.modules.ally.dto.PublicProductListItemDto;
import com.fenixcore.optibienestar360.modules.ally.dto.PublicProductOfferDto;
import com.fenixcore.optibienestar360.modules.ally.service.PublicProductSearch;
import com.fenixcore.optibienestar360.modules.ally.service.PublicProductsService;
import com.fenixcore.optibienestar360.modules.media.dto.PublicImageDto;
import com.fenixcore.optibienestar360.modules.product.entity.Product.ProductType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT del contrato público del catálogo (ADR 0026 del hub): {@code GET /v1/public/products}
 * (cross-ally, {@link PublicProductController}) y
 * + detalle {@code GET /v1/public/products/{uuid}} y {@code GET /v1/public/allies/{uuid}/products} (per-ally,
 * {@link PublicAllyController}). Ambos delegan en {@link PublicProductsService},
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
class PublicProductControllerIT {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private PublicProductsService publicProductsService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    private PublicProductListItemDto crossAllyRow() {
        return new PublicProductListItemDto(
                UUID.randomUUID(), UUID.randomUUID(), ProductType.SERVICE, "Optometría", "Examen de la vista",
                "Evaluación optométrica completa", new BigDecimal("15.00"), null, new BigDecimal("15.00"), null, true, null,
                UUID.randomUUID(), "Óptica Vicente", List.of("Óptica"), "Mérida",
                "https://cdn.example/logo.png", "+58 274 5550100");
    }

    private PublicAllyProductDto leanRow() {
        return new PublicAllyProductDto(
                UUID.randomUUID(), UUID.randomUUID(), ProductType.SERVICE, true, "Optometría", "Examen de la vista",
                "Evaluación optométrica completa", new BigDecimal("15.00"), null, new BigDecimal("15.00"), null, true, null,
                List.of());
    }

    private PublicProductDetailDto detail() {
        PublicProductOfferDto cheapest = new PublicProductOfferDto(UUID.randomUUID(),
                new BigDecimal("20.00"), new BigDecimal("25.00"), new BigDecimal("15.00"), null,
                UUID.randomUUID(), "Óptica Vicente", List.of("Óptica"), "Mérida", null, "+58 274 5550100", null,
                "https://cdn.example/optica.jpg", List.of(new PublicImageDto("https://cdn.example/optica.jpg", null, true)));
        PublicProductOfferDto other = new PublicProductOfferDto(UUID.randomUUID(),
                new BigDecimal("18.00"), null, new BigDecimal("18.00"), "Solo de lunes a viernes",
                UUID.randomUUID(), "Clínica Andes", List.of("Clínica"), "Mérida", null, null, null, null, List.of());
        return new PublicProductDetailDto(UUID.randomUUID(), "Examen de la vista", null, ProductType.SERVICE,
                "Optometría", true, null, 2, new BigDecimal("15.00"), new BigDecimal("25.00"), List.of(cheapest, other),
                List.of(new PublicImageDto("https://cdn.example/examen.jpg", "Examen", true)));
    }

    // ─── /v1/public/products (cross-ally) ────────────────────────────────────

    @Test
    void search_isPublic_returns200WithoutToken_andCarriesAllyIdentity() throws Exception {
        when(publicProductsService.search(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(crossAllyRow())));

        // No .with(user(...)) — a truly anonymous request must succeed.
        mockMvc.perform(get("/v1/public/products").param("type", "SERVICE"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Cache-Control"))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Examen de la vista"))
                .andExpect(jsonPath("$.content[0].productType").value("SERVICE"))
                .andExpect(jsonPath("$.content[0].categoryName").value("Optometría"))
                // The parent-ally identity travels with each row.
                .andExpect(jsonPath("$.content[0].allyName").value("Óptica Vicente"))
                .andExpect(jsonPath("$.content[0].allyCityName").value("Mérida"));
    }

    @Test
    void search_takesPlainWords_notIds() throws Exception {
        when(publicProductsService.search(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(crossAllyRow())));

        mockMvc.perform(get("/v1/public/products")
                        .param("q", "lentes")
                        .param("type", "servicio")
                        .param("category", "consulta")
                        .param("city", "Acarigua")
                        .param("ally", "J-12345678-9"))
                .andExpect(status().isOk());

        ArgumentCaptor<PublicProductSearch> captor = ArgumentCaptor.forClass(PublicProductSearch.class);
        verify(publicProductsService).search(captor.capture(), any(Pageable.class));
        assertThat(captor.getValue()).isEqualTo(new PublicProductSearch(
                "lentes", "servicio", "consulta", null, "Acarigua", null, "J-12345678-9", null));
    }

    @Test
    void deprecatedServicesAlias_stillAnswers() throws Exception {
        when(publicProductsService.search(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(crossAllyRow())));

        mockMvc.perform(get("/v1/public/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    // ─── /v1/public/products/{uuid} (product detail + ally comparison) ───────

    @Test
    void detail_isPublic_andListsEveryOfferingAllyWithItsPrice() throws Exception {
        when(publicProductsService.productDetail(any(UUID.class), any(), any())).thenReturn(detail());

        mockMvc.perform(get("/v1/public/products/{uuid}", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offersCount").value(2))
                .andExpect(jsonPath("$.minFinalPrice").value(15.00))
                .andExpect(jsonPath("$.offers[0].allyName").value("Óptica Vicente"))
                .andExpect(jsonPath("$.offers[0].discountPct").value(25.00))
                .andExpect(jsonPath("$.offers[0].finalPrice").value(15.00))
                .andExpect(jsonPath("$.offers[1].exclusions").value("Solo de lunes a viernes"))
                // Galleries: the product's and each offer's (an offer's own, else the product's).
                .andExpect(jsonPath("$.images[0].url").value("https://cdn.example/examen.jpg"))
                .andExpect(jsonPath("$.offers[0].imageUrl").value("https://cdn.example/optica.jpg"))
                .andExpect(jsonPath("$.offers[0].images[0].primary").value(true));
    }

    @Test
    void detail_returns404_whenNobodyOffersTheProductPublicly() throws Exception {
        when(publicProductsService.productDetail(any(UUID.class), any(), any()))
                .thenThrow(new NoSuchElementException("product.not_found"));

        mockMvc.perform(get("/v1/public/products/{uuid}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    // ─── /v1/public/allies/{uuid}/products (per-ally) ────────────────────────

    @Test
    void listByAlly_isPublic_returns200WithoutToken() throws Exception {
        when(publicProductsService.listByAlly(any(UUID.class), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(leanRow())));

        mockMvc.perform(get("/v1/public/allies/{uuid}/products", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Examen de la vista"))
                // Lean per-ally DTO carries no ally identity (the ally is the path).
                .andExpect(jsonPath("$.content[0].allyName").doesNotExist());

        // Deprecated alias, same handler.
        mockMvc.perform(get("/v1/public/allies/{uuid}/services", UUID.randomUUID()))
                .andExpect(status().isOk());
    }

    @Test
    void listByAlly_returns404_whenAllyIsNotPubliclyVisible() throws Exception {
        when(publicProductsService.listByAlly(any(UUID.class), any(), any(), any(), any(), any(Pageable.class)))
                .thenThrow(new NoSuchElementException("ally.not_found"));

        mockMvc.perform(get("/v1/public/allies/{uuid}/products", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
