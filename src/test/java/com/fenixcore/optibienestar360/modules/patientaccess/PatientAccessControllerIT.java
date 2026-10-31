package com.fenixcore.optibienestar360.modules.patientaccess;

import com.fenixcore.optibienestar360.modules.member.dto.MedicalRecordDto;
import com.fenixcore.optibienestar360.modules.patientaccess.dto.MyMedicalRecordAccessDto;
import com.fenixcore.optibienestar360.modules.patientaccess.service.PatientAccessService;
import com.fenixcore.optibienestar360.modules.terms.service.TermsAcceptanceService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Route-level security of the patient medical history (hub ADR 0032):
 * {@code MEMBER_MEDICAL_RECORD_VIEW_PATIENT} on the ally side,
 * {@code ALLY_PATIENT_LINK_VIEW_OWN} on the member side. The service (and
 * its eligibility rules) is mocked; see {@code PatientAccessServiceTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class PatientAccessControllerIT {

    @Autowired
    private WebApplicationContext context;

    /** Portal gate ({@code TermsGateInterceptor}) asks it for pending contracts; this IT has no real database. */
    @MockitoBean
    private TermsAcceptanceService termsAcceptanceService;

    @MockitoBean
    private PatientAccessService patientAccessService;

    private MockMvc mockMvc;

    private final UUID allyUuid = UUID.randomUUID();
    private final UUID memberUuid = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private RequestPostProcessor principal(String... authorities) {
        CustomUserDetails p = CustomUserDetails.fromJwt(UUID.randomUUID(), "jti", "es",
                List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList());
        return authentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }

    private String recordPath() {
        return "/v1/me/allies/" + allyUuid + "/patients/" + memberUuid + "/medical-record";
    }

    @Test
    void medicalRecord_anonymous_is401() throws Exception {
        mockMvc.perform(get(recordPath())).andExpect(status().isUnauthorized());
    }

    @Test
    void medicalRecord_withoutPermission_is403() throws Exception {
        mockMvc.perform(get(recordPath()).with(principal("ALLY_VALIDATE_MEMBER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void medicalRecord_withPermission_is200AndNotCached() throws Exception {
        when(patientAccessService.readMedicalRecord(any(), any(), any())).thenReturn(new MedicalRecordDto(
                UUID.randomUUID(), UUID.randomUUID(), "O+", "Penicilina", null, null,
                null, null, null, null, true, "ACTIVE", null, null, true));

        mockMvc.perform(get(recordPath()).with(principal("MEMBER_MEDICAL_RECORD_VIEW_PATIENT")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.allergies").value("Penicilina"));
    }

    @Test
    void myAccess_withoutPermission_is403() throws Exception {
        mockMvc.perform(get("/v1/me/medical-record/access").with(principal("MEMBER_VIEW_OWN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void myAccess_withPermission_is200() throws Exception {
        when(patientAccessService.myAccess(any())).thenReturn(new MyMedicalRecordAccessDto(List.of(), List.of()));

        mockMvc.perform(get("/v1/me/medical-record/access").with(principal("ALLY_PATIENT_LINK_VIEW_OWN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.links").isArray());
    }
}
