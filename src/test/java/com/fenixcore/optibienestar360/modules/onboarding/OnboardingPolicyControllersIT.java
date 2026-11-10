package com.fenixcore.optibienestar360.modules.onboarding;

import com.fenixcore.optibienestar360.modules.onboarding.dto.OnboardingPolicyDto;
import com.fenixcore.optibienestar360.modules.onboarding.dto.PublicOnboardingPolicyDto;
import com.fenixcore.optibienestar360.modules.onboarding.entity.OnboardingPolicy.AllyApprovalMode;
import com.fenixcore.optibienestar360.modules.onboarding.entity.OnboardingPolicy.AllyPublishMode;
import com.fenixcore.optibienestar360.modules.onboarding.entity.OnboardingPolicy.MemberActivationMode;
import com.fenixcore.optibienestar360.modules.onboarding.entity.OnboardingPolicy.MemberApprovalMode;
import com.fenixcore.optibienestar360.modules.onboarding.service.OnboardingPolicyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authorization and validation contract of {@code /v1/admin/onboarding-policy}
 * and {@code GET /v1/public/onboarding-policy}. The service is mocked, same
 * reasoning as {@code AdminSecurityPolicyControllerIT} (H2, Flyway off).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class OnboardingPolicyControllersIT {

    private static final String URL = "/v1/admin/onboarding-policy";
    private static final String VALID_BODY = """
            {"allowMemberSelfRegistration":true,"allowAllySelfRegistration":false,
             "memberApprovalMode":"AUTO_ON_SUBMIT","memberActivationMode":"ON_PAYMENT_APPROVED",
             "allyApprovalMode":"MANUAL_WITH_AGREEMENT","allyPublishMode":"MANUAL",
             "memberDraftRetentionDays":15,"allyDraftRetentionDays":30}
            """;

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private OnboardingPolicyService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static OnboardingPolicyDto dto() {
        return new OnboardingPolicyDto(UUID.randomUUID(), true, false, MemberApprovalMode.AUTO_ON_SUBMIT,
                MemberActivationMode.ON_PAYMENT_APPROVED, AllyApprovalMode.MANUAL_WITH_AGREEMENT, AllyPublishMode.MANUAL, 15, 30);
    }

    @Test
    void anonymous_admin_get_is_401() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        verify(service, never()).get();
    }

    @Test
    void get_without_view_permission_is_403() throws Exception {
        mockMvc.perform(get(URL).with(user("op").authorities(new SimpleGrantedAuthority("USER_VIEW_ALL"))))
                .andExpect(status().isForbidden());
        verify(service, never()).get();
    }

    @Test
    void get_with_view_permission_returns_the_policy() throws Exception {
        when(service.get()).thenReturn(dto());

        mockMvc.perform(get(URL).with(user("admin").authorities(new SimpleGrantedAuthority("ONBOARDING_POLICY_VIEW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowAllySelfRegistration").value(false))
                .andExpect(jsonPath("$.memberApprovalMode").value("AUTO_ON_SUBMIT"));
    }

    @Test
    void put_with_only_view_permission_is_403() throws Exception {
        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY)
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("ONBOARDING_POLICY_VIEW"))))
                .andExpect(status().isForbidden());
        verify(service, never()).update(any());
    }

    @Test
    void put_with_update_permission_replaces_the_policy() throws Exception {
        when(service.update(any())).thenReturn(dto());

        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY)
                        .with(user("admin").authorities(new SimpleGrantedAuthority("ONBOARDING_POLICY_UPDATE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowMemberSelfRegistration").value(true));
    }

    @Test
    void put_with_a_missing_mode_is_400_and_never_touches_the_service() throws Exception {
        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("\"allyPublishMode\":\"MANUAL\"", "\"allyPublishMode\":null"))
                        .with(user("admin").authorities(new SimpleGrantedAuthority("ONBOARDING_POLICY_UPDATE"))))
                .andExpect(status().isBadRequest());
        verify(service, never()).update(any());
    }

    @Test
    void public_policy_is_readable_without_login() throws Exception {
        when(service.publicView()).thenReturn(new PublicOnboardingPolicyDto(false, true, "MANUAL",
                "ON_PAYMENT_APPROVED", "MANUAL_WITH_AGREEMENT", "MANUAL"));

        mockMvc.perform(get("/v1/public/onboarding-policy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberSelfRegistrationEnabled").value(false))
                .andExpect(jsonPath("$.allySelfRegistrationEnabled").value(true));
    }
}
