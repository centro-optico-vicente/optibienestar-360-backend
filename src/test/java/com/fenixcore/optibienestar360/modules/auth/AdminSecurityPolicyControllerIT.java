package com.fenixcore.optibienestar360.modules.auth;

import com.fenixcore.optibienestar360.modules.auth.dto.SecurityPolicyDto;
import com.fenixcore.optibienestar360.modules.auth.service.SecurityPolicyService;
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
 * Authorization and request-validation contract of
 * {@code GET/PUT /v1/admin/security-policy} ({@link AdminSecurityPolicyController}).
 * The service is mocked — same reasoning as {@code PermissionControllerIT}:
 * the {@code test} profile runs on H2 with Flyway off, so the V6 seed row and
 * the V191 permissions do not exist here.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class AdminSecurityPolicyControllerIT {

    private static final String URL = "/v1/admin/security-policy";
    private static final String VALID_BODY = """
            {"maxLoginAttempts":5,"lockoutDurationMinutes":30,"daysPasswordExpires":0,
             "passwordHistoryCount":0,"maxConcurrentSessions":3}
            """;

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private SecurityPolicyService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    private static SecurityPolicyDto dto(int historyCount) {
        return new SecurityPolicyDto(UUID.randomUUID(), 5, 30, 0, historyCount, 3);
    }

    @Test
    void anonymous_get_is_401() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        verify(service, never()).get();
    }

    @Test
    void get_without_view_permission_is_403() throws Exception {
        mockMvc.perform(get(URL)
                        .with(user("operador").authorities(new SimpleGrantedAuthority("USER_VIEW_ALL"))))
                .andExpect(status().isForbidden());
        verify(service, never()).get();
    }

    @Test
    void get_with_view_permission_returns_the_policy() throws Exception {
        when(service.get()).thenReturn(dto(5));

        mockMvc.perform(get(URL)
                        .with(user("admin").authorities(new SimpleGrantedAuthority("SECURITY_POLICY_VIEW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxLoginAttempts").value(5))
                .andExpect(jsonPath("$.lockoutDurationMinutes").value(30))
                .andExpect(jsonPath("$.passwordHistoryCount").value(5))
                .andExpect(jsonPath("$.maxConcurrentSessions").value(3));
    }

    @Test
    void put_with_only_view_permission_is_403() throws Exception {
        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY)
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("SECURITY_POLICY_VIEW"))))
                .andExpect(status().isForbidden());
        verify(service, never()).update(any());
    }

    @Test
    void put_with_update_permission_accepts_zero_history() throws Exception {
        when(service.update(any())).thenReturn(dto(0));

        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY)
                        .with(user("admin").authorities(new SimpleGrantedAuthority("SECURITY_POLICY_UPDATE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordHistoryCount").value(0));
    }

    @Test
    void put_with_negative_history_is_400_and_never_touches_the_service() throws Exception {
        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("\"passwordHistoryCount\":0", "\"passwordHistoryCount\":-1"))
                        .with(user("admin").authorities(new SimpleGrantedAuthority("SECURITY_POLICY_UPDATE"))))
                .andExpect(status().isBadRequest());
        verify(service, never()).update(any());
    }
}
