package com.fenixcore.optibienestar360.modules.currency.service;

import com.fenixcore.optibienestar360.core.display.DisplayFormatter;
import com.fenixcore.optibienestar360.core.util.AppTimeZone;
import com.fenixcore.optibienestar360.modules.currency.dto.ConvertedAmountDto;
import com.fenixcore.optibienestar360.modules.currency.dto.CurrentExchangeRateDto;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.currency.exception.NoExchangeRateAvailableException;
import com.fenixcore.optibienestar360.modules.currency.repository.CurrencyRepository;
import com.fenixcore.optibienestar360.modules.organization.entity.Organization;
import com.fenixcore.optibienestar360.modules.organization.repository.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ExchangeRateLookupService} — the generic "rate
 * vigente" lookup any feature previewing a conversion goes through (ADR
 * 0015 §7), including its {@code asOf} fallback chain (exact
 * instant/offset → bare date → "now").
 */
@ExtendWith(MockitoExtension.class)
class ExchangeRateLookupServiceTest {

    @Mock private CurrencyRepository currencyRepository;
    @Mock private CurrencyConversionService currencyConversionService;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private DisplayFormatter displayFormatter;

    /**
     * Built per-call, not as a field initializer — {@code @Mock} fields are
     * injected by {@link MockitoExtension} after test-instance construction,
     * so a field initializer here would capture them as {@code null}.
     */
    private ExchangeRateLookupService service() {
        return new ExchangeRateLookupService(currencyRepository, currencyConversionService, organizationRepository, displayFormatter);
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

        CurrentExchangeRateDto result = service().current("usd", "ves", null);

        assertThat(result.available()).isTrue();
        assertThat(result.baseCurrencyCode()).isEqualTo("USD");
        assertThat(result.quoteCurrencyCode()).isEqualTo("VES");
        assertThat(result.rate()).isEqualByComparingTo("400.00");
        assertThat(result.rateDate()).isEqualTo(LocalDate.of(2026, 9, 5));
    }

    @Test
    void currentWithABareDateResolvesToStartOfDayInAppTimeZone() {
        Currency usd = currency("USD");
        Currency ves = currency("VES");
        when(currencyRepository.findByCode("USD")).thenReturn(Optional.of(usd));
        when(currencyRepository.findByCode("VES")).thenReturn(Optional.of(ves));
        Instant expectedAsOf = LocalDate.of(2026, 8, 20).atStartOfDay(AppTimeZone.ZONE).toInstant();
        when(currencyConversionService.convert(eq(BigDecimal.ONE), eq(usd), eq(ves), eq(expectedAsOf)))
                .thenReturn(new CurrencyConversionService.ConversionResult(
                        BigDecimal.ONE, new BigDecimal("380.00"), new BigDecimal("380.00"), LocalDate.of(2026, 8, 20)));

        CurrentExchangeRateDto result = service().current("USD", "VES", "2026-08-20");

        assertThat(result.available()).isTrue();
        assertThat(result.rate()).isEqualByComparingTo("380.00");
        assertThat(result.rateDate()).isEqualTo(LocalDate.of(2026, 8, 20));
    }

    @Test
    void currentWithAnExactOffsetTimestampConvertsToThatPreciseInstant() {
        Currency usd = currency("USD");
        Currency ves = currency("VES");
        when(currencyRepository.findByCode("USD")).thenReturn(Optional.of(usd));
        when(currencyRepository.findByCode("VES")).thenReturn(Optional.of(ves));
        Instant expectedAsOf = Instant.parse("2026-08-20T18:30:00Z");
        when(currencyConversionService.convert(eq(BigDecimal.ONE), eq(usd), eq(ves), eq(expectedAsOf)))
                .thenReturn(new CurrencyConversionService.ConversionResult(
                        BigDecimal.ONE, new BigDecimal("390.00"), new BigDecimal("390.00"), LocalDate.of(2026, 8, 20)));

        CurrentExchangeRateDto result = service().current("USD", "VES", "2026-08-20T14:30:00-04:00");

        assertThat(result.available()).isTrue();
        assertThat(result.rate()).isEqualByComparingTo("390.00");
    }

    @Test
    void currentWithAnUnparseableAsOfDegradesToNowRatherThanFailing() {
        Currency usd = currency("USD");
        Currency ves = currency("VES");
        when(currencyRepository.findByCode("USD")).thenReturn(Optional.of(usd));
        when(currencyRepository.findByCode("VES")).thenReturn(Optional.of(ves));
        when(currencyConversionService.convert(eq(BigDecimal.ONE), eq(usd), eq(ves), any()))
                .thenReturn(new CurrencyConversionService.ConversionResult(
                        BigDecimal.ONE, new BigDecimal("400.00"), new BigDecimal("400.00"), LocalDate.of(2026, 9, 5)));

        CurrentExchangeRateDto result = service().current("USD", "VES", "not-a-date");

        assertThat(result.available()).isTrue();
    }

    @Test
    void currentDegradesToUnavailable_whenNoRateExists() {
        Currency usd = currency("USD");
        Currency ves = currency("VES");
        when(currencyRepository.findByCode("USD")).thenReturn(Optional.of(usd));
        when(currencyRepository.findByCode("VES")).thenReturn(Optional.of(ves));
        when(currencyConversionService.convert(any(), any(), any(), any()))
                .thenThrow(new NoExchangeRateAvailableException("exchange_rate.not_available:USD->VES"));

        assertThat(service().current("USD", "VES", null)).isEqualTo(CurrentExchangeRateDto.UNAVAILABLE);
    }

    @Test
    void currentDegradesToUnavailable_whenACurrencyCodeIsUnknown() {
        when(currencyRepository.findByCode("XXX")).thenReturn(Optional.empty());

        assertThat(service().current("XXX", "VES", null)).isEqualTo(CurrentExchangeRateDto.UNAVAILABLE);
    }

    // --- convert (GET /v1/exchange-rates/convert) ---

    private static Currency currency(String code, int decimalPlaces) {
        Currency c = currency(code);
        c.setDecimalPlaces((short) decimalPlaces);
        return c;
    }

    private void officialIs(Currency official) {
        Organization org = new Organization();
        org.setOfficialCurrency(official);
        when(organizationRepository.findSingleton()).thenReturn(org);
    }

    @Test
    void convertUsesTheDirectPairWhenOneExists() {
        Currency usd = currency("USD", 2);
        Currency ves = currency("VES", 2);
        when(currencyRepository.findByCode("USD")).thenReturn(Optional.of(usd));
        when(currencyRepository.findByCode("VES")).thenReturn(Optional.of(ves));
        when(currencyConversionService.convert(eq(new BigDecimal("10")), eq(usd), eq(ves), any()))
                .thenReturn(new CurrencyConversionService.ConversionResult(
                        new BigDecimal("10"), new BigDecimal("8862.30"), new BigDecimal("886.23"), LocalDate.of(2026, 10, 22)));
        when(displayFormatter.money(any(), anyString(), any()))
                .thenAnswer(inv -> inv.getArgument(1) + " " + ((BigDecimal) inv.getArgument(0)).toPlainString());

        ConvertedAmountDto result = service().convert(new BigDecimal("10"), "USD", "VES", null);

        assertThat(result.available()).isTrue();
        assertThat(result.convertedAmount()).isEqualByComparingTo("8862.30");
        assertThat(result.convertedAmount_Display()).isEqualTo("VES 8862.30");
        assertThat(result.rate()).isEqualByComparingTo("886.23");
        assertThat(result.viaCurrencyCode()).isNull();
    }

    @Test
    void convertCrossesThroughTheOfficialCurrencyWhenThePairHasNoRate() {
        Currency usd = currency("USD", 2);
        Currency eur = currency("EUR", 2);
        Currency ves = currency("VES", 2);
        when(currencyRepository.findByCode("USD")).thenReturn(Optional.of(usd));
        when(currencyRepository.findByCode("EUR")).thenReturn(Optional.of(eur));
        officialIs(ves);
        when(currencyConversionService.convert(eq(new BigDecimal("10")), eq(usd), eq(eur), any()))
                .thenThrow(new NoExchangeRateAvailableException("exchange_rate.not_available:USD->EUR"));
        when(currencyConversionService.convert(eq(BigDecimal.ONE), eq(usd), eq(ves), any()))
                .thenReturn(new CurrencyConversionService.ConversionResult(
                        BigDecimal.ONE, new BigDecimal("400"), new BigDecimal("400"), LocalDate.of(2026, 10, 22)));
        when(currencyConversionService.convert(eq(BigDecimal.ONE), eq(eur), eq(ves), any()))
                .thenReturn(new CurrencyConversionService.ConversionResult(
                        BigDecimal.ONE, new BigDecimal("500"), new BigDecimal("500"), LocalDate.of(2026, 10, 21)));

        ConvertedAmountDto result = service().convert(new BigDecimal("10"), "USD", "EUR", null);

        assertThat(result.available()).isTrue();
        assertThat(result.rate()).isEqualByComparingTo("0.8");
        assertThat(result.convertedAmount()).isEqualByComparingTo("8.00");
        assertThat(result.rateDate()).isEqualTo(LocalDate.of(2026, 10, 21));
        assertThat(result.viaCurrencyCode()).isEqualTo("VES");
    }

    @Test
    void convertDegradesToUnavailable_whenNoDirectOrCrossRateExists() {
        Currency usd = currency("USD", 2);
        Currency eur = currency("EUR", 2);
        Currency ves = currency("VES", 2);
        when(currencyRepository.findByCode("USD")).thenReturn(Optional.of(usd));
        when(currencyRepository.findByCode("EUR")).thenReturn(Optional.of(eur));
        officialIs(ves);
        when(currencyConversionService.convert(any(), any(), any(), any()))
                .thenThrow(new NoExchangeRateAvailableException("exchange_rate.not_available"));

        assertThat(service().convert(new BigDecimal("10"), "USD", "EUR", null)).isEqualTo(ConvertedAmountDto.UNAVAILABLE);
    }

    @Test
    void convertDegradesToUnavailable_whenTheAmountIsNegative() {
        when(currencyRepository.findByCode("USD")).thenReturn(Optional.of(currency("USD")));
        when(currencyRepository.findByCode("VES")).thenReturn(Optional.of(currency("VES")));

        assertThat(service().convert(new BigDecimal("-1"), "USD", "VES", null)).isEqualTo(ConvertedAmountDto.UNAVAILABLE);
    }
}
