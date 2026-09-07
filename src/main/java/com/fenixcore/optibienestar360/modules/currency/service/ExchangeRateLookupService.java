package com.fenixcore.optibienestar360.modules.currency.service;

import com.fenixcore.optibienestar360.core.util.AppTimeZone;
import com.fenixcore.optibienestar360.modules.currency.dto.CurrentExchangeRateDto;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.currency.exception.NoExchangeRateAvailableException;
import com.fenixcore.optibienestar360.modules.currency.repository.CurrencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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

    private final CurrencyRepository currencyRepository;
    private final CurrencyConversionService currencyConversionService;

    /**
     * @param asOf the point in time to preview the rate for, as flexible as
     *             the caller's own data allows — an exact ISO-8601
     *             instant/offset timestamp (e.g. {@code
     *             2026-08-20T14:30:00-04:00}) when the caller genuinely
     *             knows the document's own time (a payment's exact receipt
     *             time, a commission's calculation instant, ...), or a bare
     *             {@code yyyy-MM-dd} when only a calendar date is known (a
     *             payment registration form typically only has that) — in
     *             which case it resolves to the start of that day in
     *             {@link AppTimeZone#ZONE}, the same convention {@code
     *             PaymentsService.snapshotExchangeRate} already uses, so the
     *             preview matches what the eventual settlement will
     *             actually snapshot. {@code null}/blank/unparseable all
     *             mean "right now" — never a reason to fail the preview.
     */
    public CurrentExchangeRateDto current(String baseCode, String quoteCode, String asOf) {
        Currency base = resolve(baseCode);
        Currency quote = resolve(quoteCode);
        if (base == null || quote == null) {
            return CurrentExchangeRateDto.UNAVAILABLE;
        }
        try {
            var result = currencyConversionService.convert(BigDecimal.ONE, base, quote, resolveAsOf(asOf));
            return new CurrentExchangeRateDto(true, base.getCode(), quote.getCode(), result.rate(), result.rateDate());
        } catch (NoExchangeRateAvailableException noRate) {
            return CurrentExchangeRateDto.UNAVAILABLE;
        }
    }

    /**
     * Tries progressively looser formats — exact offset/instant first, bare
     * date last — mirroring {@code ExchangeRateIngestionService.parseOperationDate}'s
     * fallback chain. Never throws: a caller that couldn't provide a usable
     * timestamp still gets a preview for "now" rather than an error.
     */
    private static Instant resolveAsOf(String asOf) {
        if (asOf == null || asOf.isBlank()) {
            return Instant.now();
        }
        try {
            return OffsetDateTime.parse(asOf).toInstant();
        } catch (DateTimeException ignored) {
            // covers DateTimeParseException too (it's a DateTimeException subclass) — fall through to Instant, then LocalDate
        }
        try {
            return Instant.parse(asOf);
        } catch (DateTimeException ignored) {
            // fall through to LocalDate
        }
        try {
            return LocalDate.parse(asOf).atStartOfDay(AppTimeZone.ZONE).toInstant();
        } catch (DateTimeException ignored) {
            return Instant.now(); // unparseable — degrade to "now", never fail the preview over this
        }
    }

    private Currency resolve(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return currencyRepository.findByCode(code.toUpperCase(Locale.ROOT)).orElse(null);
    }
}
