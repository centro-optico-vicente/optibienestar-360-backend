package com.fenixcore.optibienestar360.modules.member;

import com.fenixcore.optibienestar360.modules.member.dto.MemberPromoterAssignmentDto;
import com.fenixcore.optibienestar360.modules.member.service.MemberPromoterService;
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

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT del contrato de {@code POST /v1/admin/members/{uuid}/assign-promoter}
 * ({@link AdminMemberPromoterController}). Cubre autorización
 * ({@code MEMBER_ASSIGN_PROMOTER}) y el mapeo de excepciones de negocio a 422/404.
 * El {@link MemberPromoterService} se mockea (perfil {@code test} sobre H2 sin
 * seed). Se usa un principal {@link CustomUserDetails} real porque el controller
 * lee {@code actor.getUuid()}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class AdminMemberPromoterControllerIT {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private MemberPromoterService memberPromoterService;

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

    private String body(UUID promoterUuid) {
        return "{\"promoterUuid\":\"" + promoterUuid + "\",\"reason\":\"Cartera nueva\"}";
    }

    @Test
    void anonymous_is401_andNeverTouchesService() throws Exception {
        mockMvc.perform(post("/v1/admin/members/{uuid}/assign-promoter", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(body(UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
        verify(memberPromoterService, never()).assign(any(), any(), anyString(), any());
    }

    @Test
    void authenticatedWithoutPermission_is403_andNeverTouchesService() throws Exception {
        mockMvc.perform(post("/v1/admin/members/{uuid}/assign-promoter", UUID.randomUUID())
                        .with(principal("MEMBER_VIEW_ALL"))
                        .contentType(MediaType.APPLICATION_JSON).content(body(UUID.randomUUID())))
                .andExpect(status().isForbidden());
        verify(memberPromoterService, never()).assign(any(), any(), anyString(), any());
    }

    @Test
    void withPermission_returns200_withAssignmentDto() throws Exception {
        UUID memberUuid = UUID.randomUUID();
        UUID toPromoter = UUID.randomUUID();
        when(memberPromoterService.assign(any(UUID.class), any(UUID.class), anyString(), any(UUID.class)))
                .thenReturn(new MemberPromoterAssignmentDto(
                        UUID.randomUUID(), memberUuid, "Juan Pérez",
                        null, null, toPromoter, "Óptica Vicente",
                        UUID.randomUUID(), "Cartera nueva", Instant.parse("2026-07-25T12:00:00Z")));

        mockMvc.perform(post("/v1/admin/members/{uuid}/assign-promoter", memberUuid)
                        .with(principal("MEMBER_ASSIGN_PROMOTER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body(toPromoter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toPromoterUuid").value(toPromoter.toString()))
                .andExpect(jsonPath("$.toPromoterName").value("Óptica Vicente"))
                .andExpect(jsonPath("$.reason").value("Cartera nueva"))
                // from* omitted (null) by @JsonInclude(NON_NULL).
                .andExpect(jsonPath("$.fromPromoterUuid").doesNotExist());
    }

    @Test
    void unchangedReassignment_maps_to_422() throws Exception {
        when(memberPromoterService.assign(any(), any(), anyString(), any()))
                .thenThrow(new IllegalArgumentException("member.promoter.unchanged"));

        mockMvc.perform(post("/v1/admin/members/{uuid}/assign-promoter", UUID.randomUUID())
                        .with(principal("MEMBER_ASSIGN_PROMOTER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body(UUID.randomUUID())))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void unknownMember_maps_to_404() throws Exception {
        when(memberPromoterService.assign(any(), any(), anyString(), any()))
                .thenThrow(new NoSuchElementException("member.not_found"));

        mockMvc.perform(post("/v1/admin/members/{uuid}/assign-promoter", UUID.randomUUID())
                        .with(principal("MEMBER_ASSIGN_PROMOTER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body(UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }
}
