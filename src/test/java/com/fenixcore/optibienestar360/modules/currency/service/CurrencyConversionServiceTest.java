package com.fenixcore.optibienestar360.modules.currency.service;

import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.currency.exception.NoExchangeRateAvailableException;
import com.fenixcore.optibienestar360.modules.exchangerate.entity.ExchangeRate;
import com.fenixcore.optibienestar360.modules.exchangerate.repository.ExchangeRateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CurrencyConversionService} (ADR 0015 §7) — the
 * single conversion authority every payout-snapshot and DTO live-conversion
 * caller goes through.
 */
@ExtendWith(MockitoExtension.class)
class CurrencyConversionServiceTest {

    @Mock private ExchangeRateRepository exchangeRateRepository;

    private final CurrencyConversionService service = new CurrencyConversionService(null);

    private CurrencyConversionService serviceWithRepo() {
        return new CurrencyConversionService(exchangeRateRepository);
    }

    private static Currency currency(long id, String code, int decimalPlaces) {
        Currency c = new Currency();
        c.setId(id);
        c.setCode(code);
        c.setName(code);
        c.setSymbol(code);
        c.setDecimalPlaces((short) decimalPlaces);
        return c;
    }

    @Test
    void sameCurrencyPassesThroughWithoutTouchingTheRepository() {
        Currency usd = currency(1L, "USD", 2);
        Instant asOf = Instant.parse("2026-09-01T12:00:00Z");

        var result = service.convert(new BigDecimal("100.00"), usd, usd, asOf);

        assertThat(result.amount()).isEqualByComparingTo("100.00");
        assertThat(result.convertedAmount()).isEqualByComparingTo("100.00");
        assertThat(result.rate()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(result.rateDate()).isEqualTo(LocalDate.of(2026, 9, 1));
    }

    @Test
    void convertsUsingTheVigentRateAndRoundsToTheTargetCurrencyScale() {
        Currency usd = currency(1L, "USD", 2);
        Currency ves = currency(2L, "VES", 2);
        Instant asOf = Instant.parse("2026-09-01T12:00:00Z");

        ExchangeRate rate = new ExchangeRate();
        rate.setBaseCurrency(usd);
        rate.setQuoteCurrency(ves);
        rate.setRate(new BigDecimal("805.42"));
        rate.setOperationDate(LocalDate.of(2026, 8, 29));

        when(exchangeRateRepository
                .findFirstByBaseCurrencyAndQuoteCurrencyAndValidFromLessThanEqualOrderByValidFromDesc(usd, ves, asOf))
                .thenReturn(Optional.of(rate));

        var result = serviceWithRepo().convert(new BigDecimal("100.00"), usd, ves, asOf);

        assertThat(result.convertedAmount()).isEqualByComparingTo("80542.00");
        assertThat(result.rate()).isEqualByComparingTo("805.42");
        assertThat(result.rateDate()).isEqualTo(LocalDate.of(2026, 8, 29));
    }

    @Test
    void convertsUsingTheInverseRateWhenOnlyTheOppositeDirectionWasIngested() {
        // exchange_rates only ever stores USD(base)->VES(quote), per how BCV
        // publishes and how FetchExchangeRatesJob is specced to ingest — but a
        // VES payment settling against a USD-denominated plan needs the
        // opposite direction. This is the exact case PaymentsService hits.
        Currency usd = currency(1L, "USD", 2);
        Currency ves = currency(2L, "VES", 2);
        Instant asOf = Instant.parse("2026-09-01T12:00:00Z");

        ExchangeRate rate = new ExchangeRate();
        rate.setBaseCurrency(usd);
        rate.setQuoteCurrency(ves);
        rate.setRate(new BigDecimal("805.42"));
        rate.setOperationDate(LocalDate.of(2026, 8, 29));

        when(exchangeRateRepository
                .findFirstByBaseCurrencyAndQuoteCurrencyAndValidFromLessThanEqualOrderByValidFromDesc(ves, usd, asOf))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository
                .findFirstByBaseCurrencyAndQuoteCurrencyAndValidFromLessThanEqualOrderByValidFromDesc(usd, ves, asOf))
                .thenReturn(Optional.of(rate));

        var result = serviceWithRepo().convert(new BigDecimal("80542.00"), ves, usd, asOf);

        assertThat(result.convertedAmount()).isEqualByComparingTo("100.00");
        assertThat(result.rate()).isEqualByComparingTo(BigDecimal.ONE.divide(new BigDecimal("805.42"), 8, java.math.RoundingMode.HALF_UP));
        assertThat(result.rateDate()).isEqualTo(LocalDate.of(2026, 8, 29));
    }

    @Test
    void throwsWhenNoRateIsVigente() {
        Currency usd = currency(1L, "USD", 2);
        Currency ves = currency(2L, "VES", 2);
        Instant asOf = Instant.parse("2026-09-01T12:00:00Z");

        when(exchangeRateRepository
                .findFirstByBaseCurrencyAndQuoteCurrencyAndValidFromLessThanEqualOrderByValidFromDesc(any(), any(), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceWithRepo().convert(new BigDecimal("100.00"), usd, ves, asOf))
                .isInstanceOf(NoExchangeRateAvailableException.class);
    }
}
