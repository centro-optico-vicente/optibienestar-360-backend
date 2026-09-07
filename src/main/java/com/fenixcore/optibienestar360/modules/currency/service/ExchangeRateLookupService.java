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
import java.util.Locale;

/**
 * Backs the generic "rate vigente right now" lookup (ADR 0015 §7) — any
 * feature that needs to preview a conversion before saving (payments,
 * commissions, ally services, ...) goes through this, never a bespoke
 * lookup of its own. Delegates the actual rate resolution to
 * {@link CurrencyConversionService#convert} (same bidirectional-pair
 * fallback every other conversion uses) with {@code amount = 1} — the
 * caller only wants the rate itself, not a converted amount.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExchangeRateLookupService {

    private final CurrencyRepository currencyRepository;
    private final CurrencyConversionService currencyConversionService;

    public CurrentExchangeRateDto current(String baseCode, String quoteCode) {
        Currency base = resolve(baseCode);
        Currency quote = resolve(quoteCode);
        if (base == null || quote == null) {
            return CurrentExchangeRateDto.UNAVAILABLE;
        }
        try {
            var result = currencyConversionService.convert(BigDecimal.ONE, base, quote, Instant.now());
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
