package com.fenixcore.optibienestar360.modules.currency.service;

import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.currency.exception.NoExchangeRateAvailableException;
import com.fenixcore.optibienestar360.modules.exchangerate.entity.ExchangeRate;
import com.fenixcore.optibienestar360.modules.exchangerate.repository.ExchangeRateRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
                .findFirstByBaseCurrencyAndQuoteCurrencyAndActiveTrueAndValidFromLessThanEqualOrderByValidFromDesc(usd, ves, asOf))
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
                .findFirstByBaseCurrencyAndQuoteCurrencyAndActiveTrueAndValidFromLessThanEqualOrderByValidFromDesc(ves, usd, asOf))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository
                .findFirstByBaseCurrencyAndQuoteCurrencyAndActiveTrueAndValidFromLessThanEqualOrderByValidFromDesc(usd, ves, asOf))
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
                .findFirstByBaseCurrencyAndQuoteCurrencyAndActiveTrueAndValidFromLessThanEqualOrderByValidFromDesc(any(), any(), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceWithRepo().convert(new BigDecimal("100.00"), usd, ves, asOf))
                .isInstanceOf(NoExchangeRateAvailableException.class);
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static ExchangeRate rate(Currency base, Currency quote, String value, String validFrom, LocalDate operationDate) {
        ExchangeRate r = new ExchangeRate();
        r.setBaseCurrency(base);
        r.setQuoteCurrency(quote);
        r.setRate(new BigDecimal(value));
        r.setValidFrom(Instant.parse(validFrom));
        r.setOperationDate(operationDate);
        return r;
    }

    @Test
    void withinATransactionRepeatedLookupsOfAPairResolveFromOneHistoryLoad() {
        // A listing converting every row: 1 single-row query, then 1 history load,
        // then everything in memory — instead of one query per row.
        TransactionSynchronizationManager.initSynchronization();
        Currency usd = currency(1L, "USD", 2);
        Currency ves = currency(2L, "VES", 2);
        ExchangeRate older = rate(usd, ves, "800.00", "2026-08-01T00:00:00Z", LocalDate.of(2026, 7, 31));
        ExchangeRate newer = rate(usd, ves, "900.00", "2026-09-01T00:00:00Z", LocalDate.of(2026, 8, 31));
        Instant first = Instant.parse("2026-09-05T12:00:00Z");

        when(exchangeRateRepository
                .findFirstByBaseCurrencyAndQuoteCurrencyAndActiveTrueAndValidFromLessThanEqualOrderByValidFromDesc(usd, ves, first))
                .thenReturn(Optional.of(newer));
        when(exchangeRateRepository
                .findByBaseCurrencyAndQuoteCurrencyAndActiveTrueAndValidFromLessThanEqualOrderByValidFromAsc(eq(usd), eq(ves), any()))
                .thenReturn(List.of(older, newer));

        var svc = serviceWithRepo();
        var a = svc.convert(BigDecimal.ONE, usd, ves, first);
        var b = svc.convert(BigDecimal.ONE, usd, ves, Instant.parse("2026-09-10T12:00:00Z"));
        var c = svc.convert(BigDecimal.ONE, usd, ves, Instant.parse("2026-08-15T12:00:00Z"));
        var d = svc.convert(BigDecimal.ONE, usd, ves, Instant.parse("2026-09-20T12:00:00Z"));

        assertThat(a.rate()).isEqualByComparingTo("900.00");
        assertThat(b.rate()).isEqualByComparingTo("900.00");
        assertThat(c.rate()).isEqualByComparingTo("800.00");      // floor picks the rate vigente back then
        assertThat(c.rateDate()).isEqualTo(LocalDate.of(2026, 7, 31));
        assertThat(d.rate()).isEqualByComparingTo("900.00");
        verify(exchangeRateRepository, times(1))
                .findFirstByBaseCurrencyAndQuoteCurrencyAndActiveTrueAndValidFromLessThanEqualOrderByValidFromDesc(any(), any(), any());
        verify(exchangeRateRepository, times(1))
                .findByBaseCurrencyAndQuoteCurrencyAndActiveTrueAndValidFromLessThanEqualOrderByValidFromAsc(any(), any(), any());
    }

    @Test
    void withinATransactionAnEmptyDirectHistoryKeepsTheInverseFallbackInMemory() {
        // VES -> USD: the direct pair is never ingested. After the second lookup
        // both directions are memoized, so further rows cost no query at all.
        TransactionSynchronizationManager.initSynchronization();
        Currency usd = currency(1L, "USD", 2);
        Currency ves = currency(2L, "VES", 2);
        ExchangeRate usdVes = rate(usd, ves, "800.00", "2026-08-01T00:00:00Z", LocalDate.of(2026, 7, 31));

        when(exchangeRateRepository
                .findFirstByBaseCurrencyAndQuoteCurrencyAndActiveTrueAndValidFromLessThanEqualOrderByValidFromDesc(eq(ves), eq(usd), any()))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository
                .findFirstByBaseCurrencyAndQuoteCurrencyAndActiveTrueAndValidFromLessThanEqualOrderByValidFromDesc(eq(usd), eq(ves), any()))
                .thenReturn(Optional.of(usdVes));
        when(exchangeRateRepository
                .findByBaseCurrencyAndQuoteCurrencyAndActiveTrueAndValidFromLessThanEqualOrderByValidFromAsc(eq(ves), eq(usd), any()))
                .thenReturn(List.of());
        when(exchangeRateRepository
                .findByBaseCurrencyAndQuoteCurrencyAndActiveTrueAndValidFromLessThanEqualOrderByValidFromAsc(eq(usd), eq(ves), any()))
                .thenReturn(List.of(usdVes));

        var svc = serviceWithRepo();
        for (int i = 0; i < 5; i++) {
            var r = svc.convert(new BigDecimal("800.00"), ves, usd, Instant.parse("2026-09-0" + (i + 1) + "T12:00:00Z"));
            assertThat(r.convertedAmount()).isEqualByComparingTo("1.00");
        }
        verify(exchangeRateRepository, times(2))
                .findFirstByBaseCurrencyAndQuoteCurrencyAndActiveTrueAndValidFromLessThanEqualOrderByValidFromDesc(any(), any(), any());
        verify(exchangeRateRepository, times(2))
                .findByBaseCurrencyAndQuoteCurrencyAndActiveTrueAndValidFromLessThanEqualOrderByValidFromAsc(any(), any(), any());
    }

    @Test
    void withoutATransactionEveryLookupQueriesAsBefore() {
        Currency usd = currency(1L, "USD", 2);
        Currency ves = currency(2L, "VES", 2);
        ExchangeRate r = rate(usd, ves, "800.00", "2026-08-01T00:00:00Z", LocalDate.of(2026, 7, 31));
        when(exchangeRateRepository
                .findFirstByBaseCurrencyAndQuoteCurrencyAndActiveTrueAndValidFromLessThanEqualOrderByValidFromDesc(eq(usd), eq(ves), any()))
                .thenReturn(Optional.of(r));

        var svc = serviceWithRepo();
        svc.convert(BigDecimal.ONE, usd, ves, Instant.parse("2026-09-01T12:00:00Z"));
        svc.convert(BigDecimal.ONE, usd, ves, Instant.parse("2026-09-02T12:00:00Z"));

        verify(exchangeRateRepository, times(2))
                .findFirstByBaseCurrencyAndQuoteCurrencyAndActiveTrueAndValidFromLessThanEqualOrderByValidFromDesc(any(), any(), any());
        verify(exchangeRateRepository, never())
                .findByBaseCurrencyAndQuoteCurrencyAndActiveTrueAndValidFromLessThanEqualOrderByValidFromAsc(any(), any(), any());
    }
}
