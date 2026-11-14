package com.fenixcore.optibienestar360.modules.member;

import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.modules.member.dto.MemberCandidateDto;
import com.fenixcore.optibienestar360.modules.member.dto.MemberCandidateFilter;
import com.fenixcore.optibienestar360.modules.member.dto.MemberCandidateSource;
import com.fenixcore.optibienestar360.modules.member.dto.MemberDistributionMode;
import com.fenixcore.optibienestar360.modules.member.dto.MemberDistributionRequest;
import com.fenixcore.optibienestar360.modules.member.dto.MemberDistributionResultDto;
import com.fenixcore.optibienestar360.modules.member.dto.MemberListFilter;
import com.fenixcore.optibienestar360.modules.member.service.MemberPortfolioDistributionService;
import com.fenixcore.optibienestar360.modules.member.service.MembersService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
 * IT of the portfolio distribution endpoints (ADR LOC-0016): {@code GET /v1/admin/members/advisor/candidates},
 * {@code POST .../advisor/distribute[/preview]} and the {@code unassigned} filter of the members list.
 * Covers the {@code MEMBER_ASSIGN_PROMOTER} gate, the param/body binding, the {@code _Uuid}/{@code _Display}
 * shape and the 400/422 mapping. Services are mocked (H2 test profile, no Flyway).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class AdminMemberDistributionControllerIT {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private MemberPortfolioDistributionService distributionService;

    @MockitoBean
    private MembersService membersService;

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

    private static String body(UUID target) {
        return """
                {"memberUuids":["%s"],"targets":[{"promoterUuid":"%s"}],"mode":"EQUAL","reason":"Rotación"}
                """.formatted(UUID.randomUUID(), target);
    }

    @Test
    void candidates_withoutPermission_is403() throws Exception {
        mockMvc.perform(get("/v1/admin/members/advisor/candidates").param("source", "UNASSIGNED")
                        .with(principal("MEMBER_VIEW_ALL")))
                .andExpect(status().isForbidden());
        verify(distributionService, never()).candidates(any(), any());
    }

    @Test
    void candidates_withoutSource_is400() throws Exception {
        mockMvc.perform(get("/v1/admin/members/advisor/candidates").with(principal("MEMBER_ASSIGN_PROMOTER")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void candidates_bindsTheFilterAndPaging_andFlattensPlanAndAdvisor() throws Exception {
        UUID source = UUID.randomUUID();
        UUID plan = UUID.randomUUID();
        UUID advisor = UUID.randomUUID();
        ArgumentCaptor<MemberCandidateFilter> filter = ArgumentCaptor.forClass(MemberCandidateFilter.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        when(distributionService.candidates(filter.capture(), pageable.capture())).thenReturn(new PageImpl<>(List.of(
                new MemberCandidateDto(UUID.randomUUID(), "Juan Pérez", "V-12345678",
                        DisplayRef.of(plan, null, "Plan Individual"), "ACTIVE",
                        DisplayRef.of(advisor, null, "Ana Ventas"), Instant.parse("2026-10-01T12:00:00Z")),
                new MemberCandidateDto(UUID.randomUUID(), "María Gómez", "V-87654321",
                        null, "ACTIVE", null, Instant.parse("2026-10-02T12:00:00Z"))),
                PageRequest.of(0, 20), 2));

        mockMvc.perform(get("/v1/admin/members/advisor/candidates")
                        .param("source", "ADVISOR").param("sourcePromoterUuid", source.toString())
                        .param("q", "juan").param("planUuid", plan.toString()).param("status", "ACTIVE")
                        .param("page", "0").param("size", "20").param("sort", "createdAt,desc")
                        .with(principal("MEMBER_ASSIGN_PROMOTER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].displayName").value("Juan Pérez"))
                .andExpect(jsonPath("$.content[0].document").value("V-12345678"))
                .andExpect(jsonPath("$.content[0].plan_Uuid").value(plan.toString()))
                .andExpect(jsonPath("$.content[0].plan_Display").value("Plan Individual"))
                .andExpect(jsonPath("$.content[0].promoter_Uuid").value(advisor.toString()))
                .andExpect(jsonPath("$.content[0].promoter_Display").value("Ana Ventas"))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.content[1].promoter_Display").isEmpty());

        assertThat(filter.getValue()).isEqualTo(
                new MemberCandidateFilter(MemberCandidateSource.ADVISOR, source, "juan", plan, "ACTIVE"));
        assertThat(pageable.getValue().getSort().getOrderFor("createdAt")).isNotNull();
    }

    @Test
    void distribute_withoutPermission_is403_andNeverTouchesService() throws Exception {
        mockMvc.perform(post("/v1/admin/members/advisor/distribute").with(principal("MEMBER_VIEW_ALL"))
                        .contentType(MediaType.APPLICATION_JSON).content(body(UUID.randomUUID())))
                .andExpect(status().isForbidden());
        verify(distributionService, never()).distribute(any(), any());
    }

    @Test
    void distribute_returnsTheSummary() throws Exception {
        UUID target = UUID.randomUUID();
        ArgumentCaptor<MemberDistributionRequest> request = ArgumentCaptor.forClass(MemberDistributionRequest.class);
        when(distributionService.distribute(request.capture(), any())).thenReturn(new MemberDistributionResultDto(1, 0,
                List.of(new MemberDistributionResultDto.TargetResult(target, "Ana Ventas", 1))));

        mockMvc.perform(post("/v1/admin/members/advisor/distribute").with(principal("MEMBER_ASSIGN_PROMOTER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body(target)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.skipped").value(0))
                .andExpect(jsonPath("$.targets[0].promoterUuid").value(target.toString()))
                .andExpect(jsonPath("$.targets[0].promoterDisplay").value("Ana Ventas"))
                .andExpect(jsonPath("$.targets[0].count").value(1));

        assertThat(request.getValue().mode()).isEqualTo(MemberDistributionMode.EQUAL);
        assertThat(request.getValue().reason()).isEqualTo("Rotación");
    }

    @Test
    void preview_acceptsAFilterSelection() throws Exception {
        UUID target = UUID.randomUUID();
        ArgumentCaptor<MemberDistributionRequest> request = ArgumentCaptor.forClass(MemberDistributionRequest.class);
        when(distributionService.preview(request.capture())).thenReturn(new MemberDistributionResultDto(10, 0,
                List.of(new MemberDistributionResultDto.TargetResult(target, "Ana Ventas", 10))));
        UUID excluded = UUID.randomUUID();

        mockMvc.perform(post("/v1/admin/members/advisor/distribute/preview").with(principal("MEMBER_ASSIGN_PROMOTER"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"filter":{"source":"UNASSIGNED","q":"per"},"excludeMemberUuids":["%s"],
                                 "targets":[{"promoterUuid":"%s","count":10}],"mode":"COUNT","reason":"Rotación"}
                                """.formatted(excluded, target)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(10));

        assertThat(request.getValue().filter().source()).isEqualTo(MemberCandidateSource.UNASSIGNED);
        assertThat(request.getValue().excludeMemberUuids()).containsExactly(excluded);
        assertThat(request.getValue().targets().get(0).count()).isEqualTo(10);
    }

    @Test
    void distribute_withBlankReason_is400() throws Exception {
        mockMvc.perform(post("/v1/admin/members/advisor/distribute").with(principal("MEMBER_ASSIGN_PROMOTER"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"memberUuids":["%s"],"targets":[{"promoterUuid":"%s"}],"mode":"EQUAL","reason":" "}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isBadRequest());
        verify(distributionService, never()).distribute(any(), any());
    }

    @Test
    void distribute_businessRuleViolation_is422() throws Exception {
        when(distributionService.distribute(any(), any()))
                .thenThrow(new IllegalArgumentException("member.distribution.count_exceeds"));

        mockMvc.perform(post("/v1/admin/members/advisor/distribute").with(principal("MEMBER_ASSIGN_PROMOTER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body(UUID.randomUUID())))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void membersList_passesTheUnassignedFilter() throws Exception {
        ArgumentCaptor<MemberListFilter> filter = ArgumentCaptor.forClass(MemberListFilter.class);
        when(membersService.list(any(), any(), any(), anyBoolean(), filter.capture()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/v1/admin/members").param("unassigned", "true").with(principal("MEMBER_VIEW_ALL")))
                .andExpect(status().isOk());

        assertThat(filter.getValue().unassigned()).isTrue();
    }
}
