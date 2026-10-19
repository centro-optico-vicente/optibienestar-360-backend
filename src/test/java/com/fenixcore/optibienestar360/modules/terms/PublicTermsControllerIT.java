package com.fenixcore.optibienestar360.modules.terms;

import com.fenixcore.optibienestar360.modules.terms.dto.PublicTermsDto;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion.TermType;
import com.fenixcore.optibienestar360.modules.terms.service.TermsVersionService;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT of {@code GET /v1/public/terms/{type}} ({@link PublicTermsController}):
 * the path variable is the Spanish term code ({@code ASESOR}), bound to the
 * English constant ({@link TermType#ADVISOR}) by {@code CodedEnumConversionConfig},
 * and the JSON echoes the code back. The retired code {@code PROMOTOR} and the
 * Java constant name are not accepted. {@link TermsVersionService} is mocked
 * (H2 test profile, no Flyway seed).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class PublicTermsControllerIT {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private TermsVersionService termsVersionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void advisorCode_bindsToAdvisorAndIsEchoedInTheBody() throws Exception {
        when(termsVersionService.getCurrentPublicRendered(TermType.ADVISOR)).thenReturn(new PublicTermsDto(
                UUID.randomUUID(), TermType.ADVISOR, "Contrato de Mandato", "Texto", Instant.parse("2026-10-01T00:00:00Z")));

        mockMvc.perform(get("/v1/public/terms/ASESOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.termType").value("ASESOR"))
                .andExpect(jsonPath("$.title").value("Contrato de Mandato"));
    }

    @Test
    void memberCode_bindsToMember() throws Exception {
        when(termsVersionService.getCurrentPublicRendered(TermType.MEMBER)).thenReturn(new PublicTermsDto(
                UUID.randomUUID(), TermType.MEMBER, "Acuerdo de Membresía", "Texto", Instant.parse("2026-10-01T00:00:00Z")));

        mockMvc.perform(get("/v1/public/terms/AFILIADO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.termType").value("AFILIADO"));
    }

    @Test
    void retiredOrEnglishCodes_areRejected() throws Exception {
        mockMvc.perform(get("/v1/public/terms/PROMOTOR")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/v1/public/terms/ADVISOR")).andExpect(status().isBadRequest());
        verify(termsVersionService, never()).getCurrentPublicRendered(any());
    }
}
