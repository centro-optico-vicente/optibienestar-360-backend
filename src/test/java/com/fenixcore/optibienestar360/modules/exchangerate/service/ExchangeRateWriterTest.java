package com.fenixcore.optibienestar360.modules.exchangerate.service;

import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.exchangerate.entity.ExchangeRate;
import com.fenixcore.optibienestar360.modules.exchangerate.repository.ExchangeRateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ExchangeRateWriter} — in particular that the
 * same-day-duplicate case is short-circuited by an existence check
 * {@code BEFORE} any insert is attempted, never by catching the flush's
 * {@link DataIntegrityViolationException}: catching it inside this same
 * {@code @Transactional(REQUIRES_NEW)} method doesn't stop Hibernate from
 * having already marked that transaction unusable, so Spring's proxy would
 * still fail the commit with {@code UnexpectedRollbackException} once the
 * method returns normally — the exact 500 the "actualizar ahora" quick
 * action hit in production.
 */
@ExtendWith(MockitoExtension.class)
class ExchangeRateWriterTest {

    @Mock private ExchangeRateRepository exchangeRateRepository;

    /**
     * Built per-call, not as a field initializer — {@code @Mock} fields are
     * injected by {@link MockitoExtension} after test-instance construction,
     * so a field initializer here would capture them as {@code null}.
     */
    private ExchangeRateWriter writer() {
        return new ExchangeRateWriter(exchangeRateRepository);
    }

    private static Currency currency(String code) {
        Currency c = new Currency();
        c.setCode(code);
        return c;
    }

    @Test
    void insertSkipsTheDatabaseEntirelyWhenTheDayAlreadyHasARow() {
        Currency usd = currency("USD");
        Currency ves = currency("VES");
        when(exchangeRateRepository.existsByBaseCurrencyAndQuoteCurrencyAndOperationDate(
                usd, ves, LocalDate.of(2026, 9, 7))).thenReturn(true);

        ExchangeRateWriter.WriteOutcome outcome = writer().insert(
                usd, ves, new BigDecimal("805.42"), LocalDate.of(2026, 9, 7), Instant.now());

        assertThat(outcome).isEqualTo(ExchangeRateWriter.WriteOutcome.ALREADY_HAD_TODAY);
        verify(exchangeRateRepository, never()).saveAndFlush(any(ExchangeRate.class));
    }

    @Test
    void insertSavesAndReturnsInsertedWhenNoRowExistsYet() {
        Currency usd = currency("USD");
        Currency ves = currency("VES");
        when(exchangeRateRepository.existsByBaseCurrencyAndQuoteCurrencyAndOperationDate(
                usd, ves, LocalDate.of(2026, 9, 7))).thenReturn(false);

        ExchangeRateWriter.WriteOutcome outcome = writer().insert(
                usd, ves, new BigDecimal("805.42"), LocalDate.of(2026, 9, 7), Instant.now());

        assertThat(outcome).isEqualTo(ExchangeRateWriter.WriteOutcome.INSERTED);
        verify(exchangeRateRepository).saveAndFlush(any(ExchangeRate.class));
    }

    @Test
    void insertLetsARaceLostToAnotherRunPropagateRatherThanCatchingIt() {
        Currency usd = currency("USD");
        Currency ves = currency("VES");
        when(exchangeRateRepository.existsByBaseCurrencyAndQuoteCurrencyAndOperationDate(
                usd, ves, LocalDate.of(2026, 9, 7))).thenReturn(false);
        when(exchangeRateRepository.saveAndFlush(any(ExchangeRate.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> writer().insert(
                usd, ves, new BigDecimal("805.42"), LocalDate.of(2026, 9, 7), Instant.now()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
