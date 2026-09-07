package com.fenixcore.optibienestar360.modules.currency.service;

import com.fenixcore.optibienestar360.modules.currency.dto.CurrentExchangeRateDto;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.currency.exception.NoExchangeRateAvailableException;
import com.fenixcore.optibienestar360.modules.currency.repository.CurrencyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ExchangeRateLookupService} — the generic "rate
 * vigente right now" lookup any feature previewing a conversion goes
 * through (ADR 0015 §7).
 */
@ExtendWith(MockitoExtension.class)
class ExchangeRateLookupServiceTest {

    @Mock private CurrencyRepository currencyRepository;
    @Mock private CurrencyConversionService currencyConversionService;

    /**
     * Built per-call, not as a field initializer — {@code @Mock} fields are
     * injected by {@link MockitoExtension} after test-instance construction,
     * so a field initializer here would capture them as {@code null}.
     */
    private ExchangeRateLookupService service() {
        return new ExchangeRateLookupService(currencyRepository, currencyConversionService);
    }

    private static Currency currency(String code) {
        Currency c = new Currency();
        c.setCode(code);
        return c;
    }

    @Test
    void currentReturnsTheVigenteRate() {
        Currency usd = currency("USD");
        Currency ves = currency("VES");
        when(currencyRepository.findByCode("USD")).thenReturn(Optional.of(usd));
        when(currencyRepository.findByCode("VES")).thenReturn(Optional.of(ves));
        when(currencyConversionService.convert(eq(BigDecimal.ONE), eq(usd), eq(ves), any()))
                .thenReturn(new CurrencyConversionService.ConversionResult(
                        BigDecimal.ONE, new BigDecimal("400.00"), new BigDecimal("400.00"), LocalDate.of(2026, 9, 5)));

        CurrentExchangeRateDto result = service().current("usd", "ves");

        assertThat(result.available()).isTrue();
        assertThat(result.baseCurrencyCode()).isEqualTo("USD");
        assertThat(result.quoteCurrencyCode()).isEqualTo("VES");
        assertThat(result.rate()).isEqualByComparingTo("400.00");
        assertThat(result.rateDate()).isEqualTo(LocalDate.of(2026, 9, 5));
    }

    @Test
    void currentDegradesToUnavailable_whenNoRateExists() {
        Currency usd = currency("USD");
        Currency ves = currency("VES");
        when(currencyRepository.findByCode("USD")).thenReturn(Optional.of(usd));
        when(currencyRepository.findByCode("VES")).thenReturn(Optional.of(ves));
        when(currencyConversionService.convert(any(), any(), any(), any()))
                .thenThrow(new NoExchangeRateAvailableException("exchange_rate.not_available:USD->VES"));

        assertThat(service().current("USD", "VES")).isEqualTo(CurrentExchangeRateDto.UNAVAILABLE);
    }

    @Test
    void currentDegradesToUnavailable_whenACurrencyCodeIsUnknown() {
        when(currencyRepository.findByCode("XXX")).thenReturn(Optional.empty());

        assertThat(service().current("XXX", "VES")).isEqualTo(CurrentExchangeRateDto.UNAVAILABLE);
    }
}
