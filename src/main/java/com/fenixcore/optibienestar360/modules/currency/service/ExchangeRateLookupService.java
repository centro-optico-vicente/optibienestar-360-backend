package com.fenixcore.optibienestar360.modules.currency.service;

import com.fenixcore.optibienestar360.modules.currency.dto.CurrentExchangeRateDto;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.currency.exception.NoExchangeRateAvailableException;
import com.fenixcore.optibienestar360.modules.currency.repository.CurrencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;

/**
 * Backs the generic "rate vigente" lookup (ADR 0015 §7) — any feature that
 * needs to preview a conversion before saving (payments, commissions, ally
 * services, ...) goes through this, never a bespoke lookup of its own.
 * Delegates the actual rate resolution to
 * {@link CurrencyConversionService#convert} (same bidirectional-pair
 * fallback every other conversion uses) with {@code amount = 1} — the
 * caller only wants the rate itself, not a converted amount.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExchangeRateLookupService {

    private static final ZoneId CARACAS = ZoneId.of("America/Caracas");

    private final CurrencyRepository currencyRepository;
    private final CurrencyConversionService currencyConversionService;

    /**
     * @param asOfDate the operation date to preview the rate for — e.g. a
     *                 payment being registered for an earlier date than
     *                 today, so its preview matches what the eventual
     *                 settlement snapshot will actually use (same
     *                 start-of-day America/Caracas convention as
     *                 {@code PaymentsService.snapshotExchangeRate}).
     *                 {@code null} means "right now" (today).
     */
    public CurrentExchangeRateDto current(String baseCode, String quoteCode, LocalDate asOfDate) {
        Currency base = resolve(baseCode);
        Currency quote = resolve(quoteCode);
        if (base == null || quote == null) {
            return CurrentExchangeRateDto.UNAVAILABLE;
        }
        Instant asOf = asOfDate != null ? asOfDate.atStartOfDay(CARACAS).toInstant() : Instant.now();
        try {
            var result = currencyConversionService.convert(BigDecimal.ONE, base, quote, asOf);
            return new CurrentExchangeRateDto(true, base.getCode(), quote.getCode(), result.rate(), result.rateDate());
        } catch (NoExchangeRateAvailableException noRate) {
            return CurrentExchangeRateDto.UNAVAILABLE;
        }
    }

    private Currency resolve(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return currencyRepository.findByCode(code.toUpperCase(Locale.ROOT)).orElse(null);
    }
}
