package com.fenixcore.optibienestar360.modules.currency;

import com.fenixcore.optibienestar360.modules.currency.dto.ConvertedAmountDto;
import com.fenixcore.optibienestar360.modules.currency.dto.CurrencyDto;
import com.fenixcore.optibienestar360.modules.currency.dto.CurrentExchangeRateDto;
import com.fenixcore.optibienestar360.modules.currency.service.CurrencyService;
import com.fenixcore.optibienestar360.modules.currency.service.ExchangeRateLookupService;
import com.fenixcore.optibienestar360.modules.organization.dto.OrganizationCurrenciesDto;
import com.fenixcore.optibienestar360.modules.organization.service.OrganizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT of {@code /v1/public/exchange-rates/**}, {@code /v1/public/currencies} and
 * {@code /v1/public/organization/currencies} ({@link PublicExchangeRateController}).
 *
 * <p><b>Scope:</b> that every endpoint is really public (200 with no JWT, unlike
 * their authenticated twins which answer 401 anonymously) and that the currency
 * list goes through the sanitized {@code PublicCurrencyDto} (no {@code uuid}).
 * The services are mocked — the {@code test} profile runs on H2 with Flyway off,
 * so there's no seed data; {@code springSecurity()} exercises the real filter
 * chain, which is what proves {@code /v1/public/**} passes through
 * {@code permitAll()}.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class PublicExchangeRateControllerIT {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private ExchangeRateLookupService lookupService;

    @MockitoBean
    private CurrencyService currencyService;

    @MockitoBean
    private OrganizationService organizationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void currentIsPublic() throws Exception {
        when(lookupService.current(eq("USD"), eq("VES"), any()))
                .thenReturn(new CurrentExchangeRateDto(true, "USD", "VES", new BigDecimal("886.23"), LocalDate.of(2026, 10, 22)));

        mockMvc.perform(get("/v1/public/exchange-rates/current").param("base", "USD").param("quote", "VES"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.rate").value(886.23));
    }

    @Test
    void convertIsPublicAndDegradesToUnavailable() throws Exception {
        when(lookupService.convert(any(), eq("USD"), eq("EUR"), any())).thenReturn(ConvertedAmountDto.UNAVAILABLE);

        mockMvc.perform(get("/v1/public/exchange-rates/convert")
                        .param("amount", "10").param("from", "USD").param("to", "EUR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void currenciesIsPublicAndOmitsTheInternalUuid() throws Exception {
        when(currencyService.loadAllForDropdown()).thenReturn(List.of(
                new CurrencyDto(UUID.randomUUID(), "USD", "Dólar estadounidense", "$", (short) 2, true)));

        mockMvc.perform(get("/v1/public/currencies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("USD"))
                .andExpect(jsonPath("$[0].symbol").value("$"))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].uuid").doesNotExist());
    }

    @Test
    void organizationCurrenciesIsPublic() throws Exception {
        when(organizationService.getCurrencies()).thenReturn(new OrganizationCurrenciesDto(
                new OrganizationCurrenciesDto.CurrencyRef("VES", "Bs.", (short) 2),
                new OrganizationCurrenciesDto.CurrencyRef("USD", "$", (short) 2)));

        mockMvc.perform(get("/v1/public/organization/currencies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.official.code").value("VES"))
                .andExpect(jsonPath("$.reference.code").value("USD"));
    }
}
