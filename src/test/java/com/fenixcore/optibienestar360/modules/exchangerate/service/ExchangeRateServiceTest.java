package com.fenixcore.optibienestar360.modules.exchangerate.service;

import com.fenixcore.optibienestar360.core.util.DefaultSortResolver;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.currency.repository.CurrencyRepository;
import com.fenixcore.optibienestar360.modules.exchangerate.dto.ExchangeRateDto;
import com.fenixcore.optibienestar360.modules.exchangerate.dto.ExchangeRateUpdateRequest;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ExchangeRateService#update}/{@link ExchangeRateService#delete}
 * (ADR 0015, plan "CRUD admin de Currency + ExchangeRate") — both operations
 * succeed on a {@code MANUAL} row and reject any ingested
 * ({@code BCV}/{@code EXCHANGE_RATES_API}) row.
 */
@ExtendWith(MockitoExtension.class)
class ExchangeRateServiceTest {

    @Mock private ExchangeRateRepository repository;
    @Mock private CurrencyRepository currencyRepository;
    @Mock private DefaultSortResolver defaultSortResolver;

    /**
     * Built per-call, not as a field initializer — {@code @Mock} fields are
     * injected by {@link MockitoExtension} after test-instance construction,
     * so a field initializer here would capture them as {@code null} (same
     * fix as {@code CurrencyConversionServiceTest.serviceWithRepo()}).
     */
    private ExchangeRateService service() {
        return new ExchangeRateService(repository, currencyRepository, defaultSortResolver);
    }

    private static ExchangeRate rate(UUID uuid, ExchangeRate.Source source) {
        ExchangeRate r = new ExchangeRate();
        r.setUuid(uuid);
        Currency usd = new Currency();
        usd.setCode("USD");
        Currency ves = new Currency();
        ves.setCode("VES");
        r.setBaseCurrency(usd);
        r.setQuoteCurrency(ves);
        r.setRate(new BigDecimal("805.42"));
        r.setOperationDate(LocalDate.of(2026, 8, 29));
        r.setValidFrom(Instant.parse("2026-08-29T12:00:00Z"));
        r.setSource(source);
        r.setFetchedAt(Instant.parse("2026-08-29T12:00:00Z"));
        r.setActive(true);
        return r;
    }

    @Test
    void updateAppliesNonNullFieldsOnAManualRow() {
        UUID uuid = UUID.randomUUID();
        ExchangeRate existing = rate(uuid, ExchangeRate.Source.MANUAL);
        when(repository.findByUuid(uuid)).thenReturn(Optional.of(existing));
        when(repository.save(any(ExchangeRate.class))).thenAnswer(inv -> inv.getArgument(0));

        ExchangeRateDto result = service().update(uuid,
                new ExchangeRateUpdateRequest(new BigDecimal("810.00"), LocalDate.of(2026, 8, 30), null));

        assertThat(result.rate()).isEqualByComparingTo("810.00");
        assertThat(result.operationDate()).isEqualTo(LocalDate.of(2026, 8, 30));
        assertThat(result.validFrom()).isEqualTo(Instant.parse("2026-08-29T12:00:00Z")); // untouched (null in request)
    }

    @Test
    void updateThrowsOnABcvRow() {
        UUID uuid = UUID.randomUUID();
        ExchangeRate existing = rate(uuid, ExchangeRate.Source.BCV);
        when(repository.findByUuid(uuid)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service().update(uuid, new ExchangeRateUpdateRequest(BigDecimal.TEN, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("exchange_rate.immutable_source");
        verify(repository, never()).save(any());
    }

    @Test
    void updateThrowsOnAnExchangeRatesApiRow() {
        UUID uuid = UUID.randomUUID();
        ExchangeRate existing = rate(uuid, ExchangeRate.Source.EXCHANGE_RATES_API);
        when(repository.findByUuid(uuid)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service().update(uuid, new ExchangeRateUpdateRequest(BigDecimal.TEN, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("exchange_rate.immutable_source");
    }

    @Test
    void deleteSoftDeletesAManualRow() {
        UUID uuid = UUID.randomUUID();
        ExchangeRate existing = rate(uuid, ExchangeRate.Source.MANUAL);
        when(repository.findByUuid(uuid)).thenReturn(Optional.of(existing));
        when(repository.save(any(ExchangeRate.class))).thenAnswer(inv -> inv.getArgument(0));

        service().delete(uuid);

        assertThat(existing.isActive()).isFalse();
    }

    @Test
    void deleteThrowsOnABcvRow() {
        UUID uuid = UUID.randomUUID();
        ExchangeRate existing = rate(uuid, ExchangeRate.Source.BCV);
        when(repository.findByUuid(uuid)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service().delete(uuid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("exchange_rate.immutable_source");
        verify(repository, never()).save(any());
    }
}
