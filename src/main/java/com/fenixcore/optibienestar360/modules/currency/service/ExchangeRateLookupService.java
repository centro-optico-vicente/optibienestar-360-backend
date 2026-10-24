package com.fenixcore.optibienestar360.modules.currency.service;

import com.fenixcore.optibienestar360.core.display.DisplayFormatter;
import com.fenixcore.optibienestar360.core.util.AppTimeZone;
import com.fenixcore.optibienestar360.modules.currency.dto.ConvertedAmountDto;
import com.fenixcore.optibienestar360.modules.currency.dto.CurrentExchangeRateDto;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.currency.exception.NoExchangeRateAvailableException;
import com.fenixcore.optibienestar360.modules.currency.repository.CurrencyRepository;
import com.fenixcore.optibienestar360.modules.organization.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final OrganizationRepository organizationRepository;
    private final DisplayFormatter displayFormatter;

    private static final int RATE_SCALE = 8;
    private static final int DEFAULT_DECIMAL_PLACES = 2;

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
     * Converts {@code amount} from {@code fromCode} to {@code toCode} as of
     * {@code asOf} (same flexible format as {@link #current}). Tries the
     * direct/inverse pair first ({@link CurrencyConversionService#convert});
     * when neither exists and neither side is the organization's official
     * currency, crosses both legs through it — BCV only ever publishes
     * {@code X -> VES}, so e.g. USD→EUR has no row of its own. A crossed
     * rate reports the older of its two legs' dates, so the popover never
     * claims a fresher rate than the stalest one actually used.
     */
    public ConvertedAmountDto convert(BigDecimal amount, String fromCode, String toCode, String asOf) {
        Currency from = resolve(fromCode);
        Currency to = resolve(toCode);
        if (amount == null || amount.signum() < 0 || from == null || to == null) {
            return ConvertedAmountDto.UNAVAILABLE;
        }
        Instant instant = resolveAsOf(asOf);
        try {
            var direct = currencyConversionService.convert(amount, from, to, instant);
            return toConvertedDto(amount, from, to, direct.convertedAmount(), direct.rate(), direct.rateDate(), null);
        } catch (NoExchangeRateAvailableException noDirectRate) {
            // fall through to the cross rate below
        }
        Currency official = organizationRepository.findSingleton().getOfficialCurrency();
        if (official == null || official.getCode().equals(from.getCode()) || official.getCode().equals(to.getCode())) {
            return ConvertedAmountDto.UNAVAILABLE;
        }
        try {
            var fromLeg = currencyConversionService.convert(BigDecimal.ONE, from, official, instant);
            var toLeg = currencyConversionService.convert(BigDecimal.ONE, to, official, instant);
            BigDecimal rate = fromLeg.rate().divide(toLeg.rate(), RATE_SCALE, RoundingMode.HALF_UP);
            BigDecimal converted = amount.multiply(rate).setScale(decimalPlaces(to), RoundingMode.HALF_UP);
            LocalDate rateDate = olderOf(fromLeg.rateDate(), toLeg.rateDate());
            return toConvertedDto(amount, from, to, converted, rate, rateDate, official.getCode());
        } catch (NoExchangeRateAvailableException noCrossRate) {
            return ConvertedAmountDto.UNAVAILABLE;
        }
    }

    private ConvertedAmountDto toConvertedDto(BigDecimal amount, Currency from, Currency to, BigDecimal converted,
                                              BigDecimal rate, LocalDate rateDate, String viaCode) {
        Locale locale = LocaleContextHolder.getLocale();
        return new ConvertedAmountDto(
                true,
                amount,
                displayFormatter.money(amount, from.getCode(), locale),
                from.getCode(),
                to.getCode(),
                converted,
                displayFormatter.money(converted, to.getCode(), locale),
                rate,
                rateDisplay(rate, from.getCode(), to.getCode(), locale),
                rateDate,
                displayFormatter.date(rateDate, locale),
                viaCode);
    }

    /** "1 X = Y Z" in whichever direction gives {@code Y >= 1} — see {@link ConvertedAmountDto}. */
    private String rateDisplay(BigDecimal rate, String fromCode, String toCode, Locale locale) {
        if (rate == null || rate.signum() == 0) {
            return null;
        }
        if (rate.compareTo(BigDecimal.ONE) >= 0) {
            return displayFormatter.money(BigDecimal.ONE, fromCode, locale) + " = " + displayFormatter.money(rate, toCode, locale);
        }
        BigDecimal inverse = BigDecimal.ONE.divide(rate, RATE_SCALE, RoundingMode.HALF_UP);
        return displayFormatter.money(BigDecimal.ONE, toCode, locale) + " = " + displayFormatter.money(inverse, fromCode, locale);
    }

    private static int decimalPlaces(Currency c) {
        return c.getDecimalPlaces() != null ? c.getDecimalPlaces() : DEFAULT_DECIMAL_PLACES;
    }

    private static LocalDate olderOf(LocalDate a, LocalDate b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isBefore(b) ? a : b;
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
