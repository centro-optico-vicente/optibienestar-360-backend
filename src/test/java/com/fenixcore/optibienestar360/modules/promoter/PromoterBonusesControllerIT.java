package com.fenixcore.optibienestar360.modules.promoter;

import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.modules.promoter.dto.BonusAwardDto;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule.RewardType;
import com.fenixcore.optibienestar360.modules.promoter.service.BonusAwardsService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
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
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT of the promoter self-service bonuses contract (v2 PDF #5,
 * {@link PromoterBonusesController}): {@code BONUS_VIEW_OWN} gate + the 404 when
 * the caller is not a promoter. {@link BonusAwardsService} is mocked; the real
 * {@link CustomUserDetails} principal is needed ({@code actor.getUuid()}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class PromoterBonusesControllerIT {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private BonusAwardsService bonusAwardsService;

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

    private BonusAwardDto awardDto() {
        return new BonusAwardDto(UUID.randomUUID(),
                new DisplayRef(UUID.randomUUID(), null, "cada 500 nuevos"),
                new DisplayRef(UUID.randomUUID(), null, "Juan Pérez"), 523, 1,
                LocalDate.of(1970, 1, 1), LocalDate.of(2026, 6, 15),
                RewardType.FLAT, new BigDecimal("100.00"), null, null, new BigDecimal("100.00"),
                "USD", null, null, null, null,
                "PENDING", Instant.parse("2026-06-15T12:00:00Z"), Instant.parse("2026-06-15T12:00:00Z"));
    }

    @Test
    void myBonuses_anonymous_is401() throws Exception {
        mockMvc.perform(get("/v1/promoter/me/bonuses"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void myBonuses_withoutPermission_is403() throws Exception {
        mockMvc.perform(get("/v1/promoter/me/bonuses").with(principal("BONUS_AWARD_VIEW_ALL")))
                .andExpect(status().isForbidden());
    }

    @Test
    void myBonuses_withPermission_is200() throws Exception {
        when(bonusAwardsService.listOwn(any(UUID.class), any()))
                .thenReturn(new PageImpl<>(List.of(awardDto())));

        mockMvc.perform(get("/v1/promoter/me/bonuses").with(principal("BONUS_VIEW_OWN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].amount").value(100.00));
    }

    @Test
    void myBonuses_notAPromoter_is404() throws Exception {
        when(bonusAwardsService.listOwn(any(UUID.class), any()))
                .thenThrow(new NoSuchElementException("me.promoter.not_found"));

        mockMvc.perform(get("/v1/promoter/me/bonuses").with(principal("BONUS_VIEW_OWN")))
                .andExpect(status().isNotFound());
    }
}
