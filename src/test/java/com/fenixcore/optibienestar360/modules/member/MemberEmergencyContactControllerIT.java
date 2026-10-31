package com.fenixcore.optibienestar360.modules.member;

import com.fenixcore.optibienestar360.modules.member.dto.EmergencyContactDto;
import com.fenixcore.optibienestar360.modules.member.entity.MedicalRecord.EmergencyContactRelationship;
import com.fenixcore.optibienestar360.modules.member.service.MemberEmergencyContactService;
import com.fenixcore.optibienestar360.modules.terms.service.TermsAcceptanceService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authorization IT of {@link MemberEmergencyContactController} (V212): each
 * surface answers only to its own permission, and the advisor's permission
 * does not open the staff surface. {@link MemberEmergencyContactService} is mocked.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class MemberEmergencyContactControllerIT {

    @Autowired
    private WebApplicationContext context;

    /** Portal gate ({@code TermsGateInterceptor}) asks it for pending contracts; this IT has no real database. */
    @MockitoBean
    private TermsAcceptanceService termsAcceptanceService;

    @MockitoBean
    private MemberEmergencyContactService service;

    private MockMvc mockMvc;

    private final UUID memberUuid = UUID.randomUUID();
    private static final String BODY = """
            {"name":"María Pérez","phone":"04145550000","relationship":"SPOUSE"}
            """;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private RequestPostProcessor principal(String... authorities) {
        CustomUserDetails p = CustomUserDetails.fromJwt(UUID.randomUUID(), "jti", "es",
                List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList());
        return authentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }

    private static EmergencyContactDto dto() {
        return new EmergencyContactDto("María Pérez", "04145550000", EmergencyContactRelationship.SPOUSE, true);
    }

    @Test
    void admin_put_withUpdate_is200() throws Exception {
        when(service.update(any(), any())).thenReturn(dto());

        mockMvc.perform(put("/v1/admin/members/{u}/emergency-contact", memberUuid)
                        .with(principal("MEMBER_EMERGENCY_CONTACT_UPDATE"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("04145550000"));
    }

    @Test
    void admin_put_withAdvisorPermissionOnly_is403() throws Exception {
        mockMvc.perform(put("/v1/admin/members/{u}/emergency-contact", memberUuid)
                        .with(principal("MEMBER_EMERGENCY_CONTACT_UPDATE_DOWNLINE", "MEMBER_EMERGENCY_CONTACT_UPDATE_OWN"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_get_withViewAll_is200_andWithOwnOnly_is403() throws Exception {
        when(service.get(any())).thenReturn(dto());

        mockMvc.perform(get("/v1/admin/members/{u}/emergency-contact", memberUuid)
                        .with(principal("MEMBER_EMERGENCY_CONTACT_VIEW_ALL")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v1/admin/members/{u}/emergency-contact", memberUuid)
                        .with(principal("MEMBER_EMERGENCY_CONTACT_VIEW_OWN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void advisor_put_withDownline_is200_andWithoutIt_is403() throws Exception {
        when(service.updateForAdvisor(any(), any(), any())).thenReturn(dto());

        mockMvc.perform(put("/v1/me/advisor/portfolio/members/{u}/emergency-contact", memberUuid)
                        .with(principal("MEMBER_EMERGENCY_CONTACT_UPDATE_DOWNLINE"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
        mockMvc.perform(put("/v1/me/advisor/portfolio/members/{u}/emergency-contact", memberUuid)
                        .with(principal("MEMBER_EMERGENCY_CONTACT_UPDATE_OWN"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void own_put_withUpdateOwn_is200_andAnonymous_is401() throws Exception {
        when(service.updateOwn(any(), any())).thenReturn(dto());

        mockMvc.perform(put("/v1/me/member/emergency-contact")
                        .with(principal("MEMBER_EMERGENCY_CONTACT_UPDATE_OWN"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
        mockMvc.perform(put("/v1/me/member/emergency-contact")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }
}
