package com.fenixcore.optibienestar360.modules.ally;

import com.fenixcore.optibienestar360.modules.ally.dto.PublicAllyListItemDto;
import com.fenixcore.optibienestar360.modules.ally.service.AlliesService;
import com.fenixcore.optibienestar360.modules.ally.service.PublicAllySearch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT del directorio público de aliados: {@code GET /v1/public/allies}
 * ({@link PublicAllyController}).
 *
 * <p><b>Alcance:</b> que el endpoint sea público (200 sin JWT) y la caché HTTP
 * corta de {@code PublicHttpCacheConfig}: {@code Cache-Control} breve con
 * revalidación, {@code ETag} y 304 cuando los datos no cambiaron. El landing
 * lo pide en cada visita y se agregan aliados con frecuencia, así que se cachea
 * poco y se revalida — nunca en servidor.</p>
 *
 * <p>{@link AlliesService} se mockea (perfil {@code test} sobre H2 sin seed) y se
 * aplica {@code springSecurity()} para ejercer la cadena de filtros real, igual
 * que {@code PublicPlanControllerIT}.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class PublicAllyControllerIT {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FilterRegistrationBean<ShallowEtagHeaderFilter> publicCatalogEtagFilter;

    @MockitoBean
    private AlliesService alliesService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                // The manual MockMvc doesn't pick up servlet filter beans: register the
                // real ETag filter with its own URL patterns, after the security chain.
                .addFilter(publicCatalogEtagFilter.getFilter(),
                        publicCatalogEtagFilter.getUrlPatterns().toArray(String[]::new))
                .build();
    }

    private PublicAllyListItemDto clinicDto() {
        return new PublicAllyListItemDto(
                UUID.randomUUID(), "Clínica Demo",
                List.of("Clínica"), List.of(), "Acarigua",
                null, "Atención integral", null, null, null, null, null, null);
    }

    @Test
    void directory_isPublic_andBrieflyCacheable_andRevalidatesWithEtag() throws Exception {
        // Same instance on both calls: identical bodies are what yield the same ETag.
        PublicAllyListItemDto dto = clinicDto();
        when(alliesService.publicDirectory(eq(PublicAllySearch.none()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(dto)));

        // No .with(user(...)) — a truly anonymous request must succeed.
        MvcResult first = mockMvc.perform(get("/v1/public/allies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Clínica Demo"))
                .andExpect(header().string("Cache-Control", "max-age=60, public, stale-while-revalidate=300"))
                // Spring Security's no-cache writer must back off, not pile on Pragma/Expires.
                .andExpect(header().doesNotExist("Pragma"))
                .andExpect(header().doesNotExist("Expires"))
                .andExpect(header().exists("ETag"))
                .andReturn();

        // Unchanged data -> bodiless 304 on revalidation.
        mockMvc.perform(get("/v1/public/allies")
                        .header("If-None-Match", first.getResponse().getHeader("ETag")))
                .andExpect(status().isNotModified());
    }

    @Test
    void detail_returns404_withoutCacheHeaders_whenAllyIsNotPubliclyVisible() throws Exception {
        when(alliesService.publicGetByUuid(any(UUID.class)))
                .thenThrow(new NoSuchElementException("ally.not_found"));

        // Errors go through GlobalExceptionHandler, not the cached ResponseEntity:
        // a 404 must not be cached as public.
        mockMvc.perform(get("/v1/public/allies/{uuid}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", not(containsString("public"))));
    }
}
