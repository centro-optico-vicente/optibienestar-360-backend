package com.fenixcore.optibienestar360.modules.exchangerate.service;

import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.exchangerate.entity.ExchangeRate;
import com.fenixcore.optibienestar360.modules.exchangerate.repository.ExchangeRateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Persists a single {@link ExchangeRate} row in its own transaction
 * ({@code REQUIRES_NEW}) so that a same-day duplicate for one currency pair
 * (caught via the V85 unique index on {@code (base_currency_id,
 * quote_currency_id, operation_date)}) can never roll back a sibling
 * currency's successful insert in the same ingestion run — see
 * {@link ExchangeRateIngestionService}, which calls this once per currency.
 *
 * <p>Must be a separate Spring bean, not a private method on the
 * orchestrator: {@code @Transactional} propagation only applies through the
 * proxy, so a self-invoked method would silently run in the caller's
 * transaction (or none) instead of its own — same reasoning as
 * {@code DataChangeAuditWriter}.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExchangeRateWriter {

    private final ExchangeRateRepository exchangeRateRepository;

    public enum WriteOutcome { INSERTED, ALREADY_HAD_TODAY }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public WriteOutcome insert(Currency base, Currency quote, BigDecimal rate,
                                LocalDate operationDate, Instant validFrom) {
        ExchangeRate exchangeRate = new ExchangeRate();
        exchangeRate.setBaseCurrency(base);
        exchangeRate.setQuoteCurrency(quote);
        exchangeRate.setRate(rate);
        exchangeRate.setOperationDate(operationDate);
        exchangeRate.setValidFrom(validFrom);
        exchangeRate.setSource(ExchangeRate.Source.EXCHANGE_RATES_API);
        exchangeRate.setFetchedAt(Instant.now());
        exchangeRate.setStatus("ACTIVE");

        try {
            exchangeRateRepository.saveAndFlush(exchangeRate);
            return WriteOutcome.INSERTED;
        } catch (DataIntegrityViolationException ex) {
            log.info("Rate for {}->{} on {} already ingested today — skipping duplicate",
                    base.getCode(), quote.getCode(), operationDate);
            return WriteOutcome.ALREADY_HAD_TODAY;
        }
    }
}
