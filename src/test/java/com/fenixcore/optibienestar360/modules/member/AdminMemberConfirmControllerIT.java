package com.fenixcore.optibienestar360.modules.member;

import com.fenixcore.optibienestar360.modules.member.service.MemberConfirmationService;
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

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT del contrato de {@code POST /v1/admin/members/{uuid}/confirm}
 * ({@link AdminMemberConfirmController}). Cubre autorización
 * ({@code MEMBER_CONFIRM}) y el mapeo de excepciones de negocio a 404/422.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class AdminMemberConfirmControllerIT {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private MemberConfirmationService memberConfirmationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    private RequestPostProcessor principal(String... authorities) {
        CustomUserDetails p = CustomUserDetails.fromJwt(UUID.randomUUID(), "jti", "es",
                List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList());
        return authentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }

    @Test
    void anonymous_is401_andNeverTouchesService() throws Exception {
        mockMvc.perform(post("/v1/admin/members/{uuid}/confirm", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
        verify(memberConfirmationService, never()).confirm(any());
    }

    @Test
    void authenticatedWithoutPermission_is403_andNeverTouchesService() throws Exception {
        mockMvc.perform(post("/v1/admin/members/{uuid}/confirm", UUID.randomUUID())
                        .with(principal("MEMBER_VIEW_ALL")))
                .andExpect(status().isForbidden());
        verify(memberConfirmationService, never()).confirm(any());
    }

    @Test
    void withPermission_returns200_withConfirmedAt() throws Exception {
        UUID memberUuid = UUID.randomUUID();
        when(memberConfirmationService.confirm(memberUuid)).thenReturn(Instant.parse("2026-08-06T12:00:00Z"));

        mockMvc.perform(post("/v1/admin/members/{uuid}/confirm", memberUuid)
                        .with(principal("MEMBER_CONFIRM")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberUuid").value(memberUuid.toString()))
                .andExpect(jsonPath("$.confirmedAt").value("2026-08-06T12:00:00Z"));
    }

    @Test
    void alreadyConfirmed_maps_to_422() throws Exception {
        when(memberConfirmationService.confirm(any()))
                .thenThrow(new IllegalArgumentException("member.already_confirmed"));

        mockMvc.perform(post("/v1/admin/members/{uuid}/confirm", UUID.randomUUID())
                        .with(principal("MEMBER_CONFIRM")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void unknownMember_maps_to_404() throws Exception {
        when(memberConfirmationService.confirm(any()))
                .thenThrow(new NoSuchElementException("member.not_found"));

        mockMvc.perform(post("/v1/admin/members/{uuid}/confirm", UUID.randomUUID())
                        .with(principal("MEMBER_CONFIRM")))
                .andExpect(status().isNotFound());
    }
}
