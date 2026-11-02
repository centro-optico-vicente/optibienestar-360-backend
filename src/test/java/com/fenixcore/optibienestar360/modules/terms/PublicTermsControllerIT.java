package com.fenixcore.optibienestar360.modules.terms;

import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType;
import com.fenixcore.optibienestar360.modules.terms.dto.PublicTermsDto;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion.TermType;
import com.fenixcore.optibienestar360.modules.terms.service.MemberContractService;
import com.fenixcore.optibienestar360.modules.terms.service.TermsVersionService;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT of {@code GET /v1/public/terms/{type}} ({@link PublicTermsController}):
 * the path variable is the Spanish term code ({@code ASESOR}), bound to the
 * English constant ({@link TermType#ADVISOR}) by {@code CodedEnumConversionConfig},
 * and the JSON echoes the code back. The retired code {@code PROMOTOR} (V185)
 * is a 400. (Spring's data binder falls back to {@code Enum.valueOf} when the
 * conversion service rejects a value, so the Java constant name, e.g.
 * {@code ADVISOR}, also binds on path variables — harmless, not part of the
 * contract.) {@link TermsVersionService} is mocked (H2 test profile, no Flyway seed).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class PublicTermsControllerIT {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private TermsVersionService termsVersionService;

    @MockitoBean
    private MemberContractService memberContractService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void advisorCode_bindsToAdvisorAndIsEchoedInTheBody() throws Exception {
        when(termsVersionService.getCurrentPublicRendered(TermType.ADVISOR, null)).thenReturn(new PublicTermsDto(
                UUID.randomUUID(), TermType.ADVISOR, "Contrato de Mandato", "Texto", Instant.parse("2026-10-01T00:00:00Z")));

        mockMvc.perform(get("/v1/public/terms/ASESOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.termType").value("ASESOR"))
                .andExpect(jsonPath("$.title").value("Contrato de Mandato"));
    }

    @Test
    void memberCode_bindsToMember() throws Exception {
        when(termsVersionService.getCurrentPublicRendered(TermType.MEMBER, null)).thenReturn(new PublicTermsDto(
                UUID.randomUUID(), TermType.MEMBER, "Acuerdo de Membresía", "Texto", Instant.parse("2026-10-01T00:00:00Z")));

        mockMvc.perform(get("/v1/public/terms/AFILIADO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.termType").value("AFILIADO"));
    }

    @Test
    void aliadoSegment_isPassedToTheService() throws Exception {
        when(termsVersionService.getCurrentPublicRendered(TermType.ALLY, AllyType.Segment.B2P)).thenReturn(new PublicTermsDto(
                UUID.randomUUID(), TermType.ALLY, "Convenio Profesional", "Texto", Instant.parse("2026-11-01T00:00:00Z"),
                AllyType.Segment.B2P));

        mockMvc.perform(get("/v1/public/terms/ALIADO").param("segment", "B2P"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Convenio Profesional"))
                .andExpect(jsonPath("$.segment").value("B2P"));
    }

    @Test
    void unknownSegment_isRejected() throws Exception {
        mockMvc.perform(get("/v1/public/terms/ALIADO").param("segment", "B2X")).andExpect(status().isBadRequest());
    }

    @Test
    void list_isAnonymous_andReturnsEveryCurrentPublicVersion() throws Exception {
        when(termsVersionService.listCurrentPublicRendered()).thenReturn(List.of(
                new PublicTermsDto(UUID.randomUUID(), TermType.MEMBER, "Acuerdo de Membresía", "Texto",
                        Instant.parse("2026-11-01T00:00:00Z")),
                new PublicTermsDto(UUID.randomUUID(), TermType.ALLY, "Convenio Comercial", "Texto",
                        Instant.parse("2026-11-01T00:00:00Z"), AllyType.Segment.B2B)));

        mockMvc.perform(get("/v1/public/terms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].segment").value("B2B"));
    }

    @Test
    void retiredCode_isRejected() throws Exception {
        mockMvc.perform(get("/v1/public/terms/PROMOTOR")).andExpect(status().isBadRequest());
        verify(termsVersionService, never()).getCurrentPublicRendered(any(), any());
    }

    // ─── POST /v1/public/terms/{type}/preview ───────────────────────────────

    @Test
    void preview_isAnonymous_andRendersWithTheFormData() throws Exception {
        when(memberContractService.preview(eq(TermType.MEMBER), argThat(r -> "María".equals(r.firstName())
                && "12345678".equals(r.documentNumber())))).thenReturn(new PublicTermsDto(
                UUID.randomUUID(), TermType.MEMBER, "Acuerdo de Membresía", "Contrato de María Pérez, V-12345678",
                Instant.parse("2026-10-01T00:00:00Z")));

        mockMvc.perform(post("/v1/public/terms/AFILIADO/preview").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"María\",\"lastName\":\"Pérez\",\"documentType\":\"V\",\"documentNumber\":\"12345678\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.termType").value("AFILIADO"))
                .andExpect(jsonPath("$.contentMarkdown").value("Contrato de María Pérez, V-12345678"));
    }

    @Test
    void preview_ofAnotherKind_is422() throws Exception {
        when(memberContractService.preview(eq(TermType.ADVISOR), any()))
                .thenThrow(new IllegalArgumentException("terms.preview.member_only"));

        mockMvc.perform(post("/v1/public/terms/ASESOR/preview").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity());
    }
}
