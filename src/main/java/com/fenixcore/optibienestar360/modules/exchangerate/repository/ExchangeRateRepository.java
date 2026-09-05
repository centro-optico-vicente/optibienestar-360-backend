package com.fenixcore.optibienestar360.modules.exchangerate.repository;

import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.exchangerate.entity.ExchangeRate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long>,
        JpaSpecificationExecutor<ExchangeRate> {

    Optional<ExchangeRate> findByUuid(UUID uuid);

    /**
     * "Current rate" lookup (ADR 0015 §2): the latest vigency {@code <= at} for
     * a given (base, quote) pair. Backed by the V85 index
     * {@code idx_exchange_rates_pair_valid_from}.
     */
    Optional<ExchangeRate> findFirstByBaseCurrencyAndQuoteCurrencyAndValidFromLessThanEqualOrderByValidFromDesc(
            Currency baseCurrency, Currency quoteCurrency, Instant at);

    Page<ExchangeRate> findByBaseCurrencyAndQuoteCurrency(Currency baseCurrency, Currency quoteCurrency, Pageable pageable);
}
