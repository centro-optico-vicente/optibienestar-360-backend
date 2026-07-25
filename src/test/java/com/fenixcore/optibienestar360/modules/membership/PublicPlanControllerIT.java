package com.fenixcore.optibienestar360.modules.membership;

import com.fenixcore.optibienestar360.modules.membership.dto.PublicPlanDto;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan.PlanType;
import com.fenixcore.optibienestar360.modules.membership.service.PlansService;
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
 * IT del contrato de {@code GET /v1/public/plans} ({@link PublicPlanController}).
 *
 * <p><b>Alcance:</b> que el endpoint sea realmente <i>público</i> (200 sin
 * ningún JWT — a diferencia de {@code /v1/admin/plans}, que exige
 * {@code PLAN_VIEW_ALL} y respondería 401 anónimo), que la respuesta vaya por el
 * DTO saneado (sin filtrar {@code status}/{@code published}/audit) y que el
 * detalle de un plan no visible se mapee a 404 vía {@code GlobalExceptionHandler}.</p>
 *
 * <p>El {@link PlansService} se mockea: el perfil {@code test} corre sobre H2
 * con Flyway apagado (ver {@code application-test.properties}), así que el seed
 * de planes no existe — ni local ni en CI, porque todos los
 * {@code @SpringBootTest} fijan {@code @ActiveProfiles("test")}. Se aplica
 * {@code springSecurity()} para ejercer la cadena de filtros real: es lo que
 * demuestra que el path {@code /v1/public/**} pasa por {@code permitAll()} y no
 * cae en el entry point 401.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class PublicPlanControllerIT {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private PlansService plansService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    private PublicPlanDto familiarDto() {
        return new PublicPlanDto(
                UUID.randomUUID(), "FAMILIAR", "Plan Familiar",
                "Cobertura para el titular y su grupo familiar", PlanType.FAMILIAR,
                new BigDecimal("20.00"), new BigDecimal("5.00"),
                3, 5, new BigDecimal("5.00"), 7);
    }

    @Test
    void list_isPublic_returns200WithoutAnyToken() throws Exception {
        when(plansService.publicList(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(familiarDto())));

        // No .with(user(...)) — a truly anonymous request must succeed.
        mockMvc.perform(get("/v1/public/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].code").value("FAMILIAR"))
                .andExpect(jsonPath("$.content[0].monthlyFee").value(5.00));
    }

    @Test
    void detail_isPublic_returns200_andBodyIsSanitized() throws Exception {
        PublicPlanDto dto = familiarDto();
        when(plansService.publicGetByUuid(any(UUID.class))).thenReturn(dto);

        mockMvc.perform(get("/v1/public/plans/{uuid}", dto.uuid()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("FAMILIAR"))
                .andExpect(jsonPath("$.includedBeneficiaries").value(3))
                .andExpect(jsonPath("$.maxBeneficiaries").value(5))
                // Internal lifecycle / audit fields must never reach anonymous eyes.
                .andExpect(jsonPath("$.published").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.active").doesNotExist())
                .andExpect(jsonPath("$.createdAt").doesNotExist());
    }

    @Test
    void detail_returns404_whenPlanIsNotPubliclyVisible() throws Exception {
        // Unpublished / inactive / missing all surface as this from the service.
        when(plansService.publicGetByUuid(any(UUID.class)))
                .thenThrow(new NoSuchElementException("plan.not_found"));

        mockMvc.perform(get("/v1/public/plans/{uuid}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
