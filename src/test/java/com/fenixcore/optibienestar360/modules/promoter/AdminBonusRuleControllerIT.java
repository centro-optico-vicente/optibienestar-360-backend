package com.fenixcore.optibienestar360.modules.promoter;

import com.fenixcore.optibienestar360.modules.promoter.dto.BonusEvaluationResponse;
import com.fenixcore.optibienestar360.modules.promoter.dto.BonusRuleDto;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule.AccrualMode;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule.BonusMetric;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule.RewardType;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule.WindowStrategy;
import com.fenixcore.optibienestar360.modules.promoter.service.BonusEvaluationService;
import com.fenixcore.optibienestar360.modules.promoter.service.BonusRulesService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

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
 * IT of the bonus-rule admin contract (v2 PDF #5, {@link AdminBonusRuleController}):
 * granular per V79: {@code BONUS_RULE_VIEW_ALL} / {@code _CREATE} / {@code _UPDATE} /
 * {@code _DELETE}; {@code /evaluate} needs create-or-update. Services are mocked.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class AdminBonusRuleControllerIT {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private BonusRulesService bonusRulesService;

    @MockitoBean
    private BonusEvaluationService bonusEvaluationService;

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

    private BonusRuleDto ruleDto() {
        return new BonusRuleDto(UUID.randomUUID(), "300 activos/mes", null, List.of(),
                BonusMetric.ACTIVE_SUBSCRIBERS, AccrualMode.THRESHOLD, 300, null, null, null,
                WindowStrategy.MONTHLY, WindowStrategy.MONTHLY, WindowStrategy.MONTHLY, WindowStrategy.MONTHLY,
                null, null, null, null,
                null, null, RewardType.FLAT, new BigDecimal("50.00"), null, "USD", null, false,
                null, null, null, true, null);
    }

    private static final String VALID_RULE_JSON = """
            {"name":"300 activos/mes","metric":"ACTIVE_SUBSCRIBERS","accrual":"THRESHOLD",
             "thresholdCount":300,"accrualPeriodStrategy":"MONTHLY","rewardType":"FLAT","flatAmount":50.00}
            """;

    // ─── list (read, BONUS_RULE_VIEW_ALL) ────────────────────────────────────

    @Test
    void list_anonymous_is401() throws Exception {
        mockMvc.perform(get("/v1/admin/bonus-rules"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_withoutPermission_is403() throws Exception {
        mockMvc.perform(get("/v1/admin/bonus-rules").with(principal("BONUS_AWARD_VIEW_ALL")))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_withPermission_is200() throws Exception {
        when(bonusRulesService.list(any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(new PageImpl<>(List.of(ruleDto())));

        mockMvc.perform(get("/v1/admin/bonus-rules").with(principal("BONUS_RULE_VIEW_ALL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    // ─── create (write, BONUS_RULE_CREATE) ───────────────────────────────────

    @Test
    void create_withoutPermission_is403() throws Exception {
        mockMvc.perform(post("/v1/admin/bonus-rules")
                        .with(principal("BONUS_VIEW_OWN"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_RULE_JSON))
                .andExpect(status().isForbidden());
        verify(bonusRulesService, never()).create(any());
    }

    @Test
    void create_withPermission_is201() throws Exception {
        when(bonusRulesService.create(any())).thenReturn(ruleDto());

        mockMvc.perform(post("/v1/admin/bonus-rules")
                        .with(principal("BONUS_RULE_CREATE"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_RULE_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.thresholdCount").value(300));
    }

    // ─── evaluate (create-or-update) ─────────────────────────────────────────

    @Test
    void evaluate_withoutPermission_is403() throws Exception {
        mockMvc.perform(post("/v1/admin/bonus-rules/evaluate")
                        .with(principal("BONUS_VIEW_OWN")))
                .andExpect(status().isForbidden());
        verify(bonusEvaluationService, never()).evaluate(any(), anyBoolean());
    }

    @Test
    void evaluate_withPermission_is200() throws Exception {
        when(bonusEvaluationService.evaluate(any(), anyBoolean())).thenReturn(
                new BonusEvaluationResponse(LocalDate.of(2026, 6, 1), true, 2, 1,
                        new BigDecimal("100.00"), "USD", Instant.parse("2026-06-01T12:00:00Z"), List.of()));

        mockMvc.perform(post("/v1/admin/bonus-rules/evaluate")
                        .with(principal("BONUS_RULE_UPDATE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"dryRun\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.awardsCreated").value(1))
                .andExpect(jsonPath("$.dryRun").value(true));
    }
}
