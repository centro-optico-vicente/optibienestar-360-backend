package com.fenixcore.optibienestar360.modules.ally;

import com.fenixcore.optibienestar360.modules.ally.dto.AllyBulkAssignResultDto;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyListFilter;
import com.fenixcore.optibienestar360.modules.ally.service.AllyAdvisorBulkService;
import com.fenixcore.optibienestar360.modules.ally.service.AlliesService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT of {@code POST /v1/admin/allies/advisor/bulk-assign} and the {@code unassigned} filter of the allies
 * list (ADR LOC-0016). Services mocked (H2 test profile, no Flyway).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class AdminAllyAdvisorBulkControllerIT {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private AlliesService alliesService;

    @MockitoBean
    private AllyAdvisorBulkService allyAdvisorBulkService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private RequestPostProcessor principal(String... authorities) {
        CustomUserDetails p = CustomUserDetails.fromJwt(UUID.randomUUID(), "jti", "es",
                List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList());
        return authentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }

    @Test
    void bulkAssign_requiresAllyAssignPromoter() throws Exception {
        String body = "{\"allyUuids\":[\"" + UUID.randomUUID() + "\"],\"promoterUuid\":\"" + UUID.randomUUID() + "\"}";
        mockMvc.perform(post("/v1/admin/allies/advisor/bulk-assign").with(principal("ALLY_UPDATE"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        verify(allyAdvisorBulkService, never()).bulkAssign(any(), any());
    }

    @Test
    void bulkAssign_returnsTotalAndSkipped() throws Exception {
        UUID ally1 = UUID.randomUUID();
        UUID ally2 = UUID.randomUUID();
        UUID promoter = UUID.randomUUID();
        when(allyAdvisorBulkService.bulkAssign(List.of(ally1, ally2), promoter))
                .thenReturn(new AllyBulkAssignResultDto(2, 1));

        mockMvc.perform(post("/v1/admin/allies/advisor/bulk-assign").with(principal("ALLY_ASSIGN_PROMOTER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"allyUuids\":[\"" + ally1 + "\",\"" + ally2 + "\"],\"promoterUuid\":\"" + promoter + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.skipped").value(1));
    }

    @Test
    void bulkAssign_emptySelection_is400() throws Exception {
        mockMvc.perform(post("/v1/admin/allies/advisor/bulk-assign").with(principal("ALLY_ASSIGN_PROMOTER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"allyUuids\":[],\"promoterUuid\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bulkAssign_systemTarget_is422() throws Exception {
        when(allyAdvisorBulkService.bulkAssign(any(), any()))
                .thenThrow(new IllegalArgumentException("promoter.system_not_assignable"));

        mockMvc.perform(post("/v1/admin/allies/advisor/bulk-assign").with(principal("ALLY_ASSIGN_PROMOTER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"allyUuids\":[\"" + UUID.randomUUID() + "\"],\"promoterUuid\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void list_passesTheUnassignedFilter() throws Exception {
        ArgumentCaptor<AllyListFilter> filter = ArgumentCaptor.forClass(AllyListFilter.class);
        when(alliesService.list(any(), any(), any(), anyBoolean(), filter.capture())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/v1/admin/allies").param("unassigned", "true").with(principal("ALLY_VIEW_ALL")))
                .andExpect(status().isOk());

        assertThat(filter.getValue().unassigned()).isTrue();
    }
}
