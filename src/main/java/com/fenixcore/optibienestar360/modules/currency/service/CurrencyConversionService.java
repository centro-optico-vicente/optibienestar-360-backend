package com.fenixcore.optibienestar360.modules.currency.service;

import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.currency.exception.NoExchangeRateAvailableException;
import com.fenixcore.optibienestar360.modules.exchangerate.entity.ExchangeRate;
import com.fenixcore.optibienestar360.modules.exchangerate.repository.ExchangeRateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * The single conversion authority for ADR 0015 §7 — every "show me this
 * amount in another currency" need in the codebase goes through
 * {@link #convert}, never a bespoke rate lookup.
 *
 * <p>Delegates to {@link ExchangeRateRepository}'s current-rate query — no
 * second conversion path. Same-currency is a trivial passthrough that never
 * touches {@code exchange_rates}. Missing rate throws
 * {@link NoExchangeRateAvailableException}; per the ADR, callers decide
 * whether that's fatal or a degrade-to-null in a DTO — this service never
 * silently swallows it itself, so the caller's intent stays explicit at the
 * call site.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CurrencyConversionService {

    private final ExchangeRateRepository exchangeRateRepository;

    /**
     * Scale used when inverting a reverse-direction rate ({@code 1/rate}) —
     * matches {@code exchange_rates.rate}'s own {@code NUMERIC(18,8)} column.
     */
    private static final int RATE_SCALE = 8;

    /**
     * Converts {@code amount} from {@code from} to {@code to} using the rate
     * vigente at {@code asOf}. Currencies compared by entity id — pass
     * already-loaded {@link Currency} instances (or the same reference).
     *
     * <p>{@code exchange_rates} only ever stores one direction per pair (BCV
     * always publishes against USD as the base — see the not-yet-built
     * {@code FetchExchangeRatesJob} spec in ADR 0015). Callers routinely need
     * the opposite direction too (e.g. a VES payment settling against a
     * USD-denominated plan: {@code from=VES, to=USD}), so this method tries
     * the direct pair first and falls back to the inverse pair (inverting the
     * rate) before giving up — never assumes the caller already knows which
     * direction was actually ingested.</p>
     *
     * @throws NoExchangeRateAvailableException when {@code from != to} and no
     *         {@code exchange_rates} row is vigente for that pair — in either
     *         direction — at {@code asOf}.
     */
    // noRollbackFor: this method participates in the caller's ambient
    // transaction (propagation REQUIRED). Without it, the default rollback
    // rule marks that *shared* transaction rollback-only the instant this
    // throws — even when the caller (e.g. ConversionEnricher) catches it and
    // degrades gracefully — so an unrelated read-only listing later fails at
    // commit with an opaque UnexpectedRollbackException. A missing rate is
    // expected, caller-handled data, never a reason to poison the transaction.
    @Transactional(readOnly = true, noRollbackFor = NoExchangeRateAvailableException.class)
    public ConversionResult convert(BigDecimal amount, Currency from, Currency to, Instant asOf) {
        LocalDate asOfDate = asOf.atZone(ZoneId.of("America/Caracas")).toLocalDate();

        if (sameCurrency(from, to)) {
            return new ConversionResult(amount, amount, BigDecimal.ONE, asOfDate);
        }

        var direct = exchangeRateRepository
                .findFirstByBaseCurrencyAndQuoteCurrencyAndValidFromLessThanEqualOrderByValidFromDesc(from, to, asOf);
        if (direct.isPresent()) {
            ExchangeRate rate = direct.get();
            BigDecimal converted = amount.multiply(rate.getRate())
                    .setScale(to.getDecimalPlaces(), RoundingMode.HALF_UP);
            return new ConversionResult(amount, converted, rate.getRate(), rate.getOperationDate());
        }

        var inverse = exchangeRateRepository
                .findFirstByBaseCurrencyAndQuoteCurrencyAndValidFromLessThanEqualOrderByValidFromDesc(to, from, asOf);
        if (inverse.isPresent()) {
            ExchangeRate rate = inverse.get();
            BigDecimal effectiveRate = BigDecimal.ONE.divide(rate.getRate(), RATE_SCALE, RoundingMode.HALF_UP);
            BigDecimal converted = amount.multiply(effectiveRate)
                    .setScale(to.getDecimalPlaces(), RoundingMode.HALF_UP);
            return new ConversionResult(amount, converted, effectiveRate, rate.getOperationDate());
        }

        throw new NoExchangeRateAvailableException(
                "exchange_rate.not_available:" + from.getCode() + "->" + to.getCode());
    }

    private static boolean sameCurrency(Currency a, Currency b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.getId() != null && a.getId().equals(b.getId());
    }

    /**
     * @param amount          the original amount, unchanged
     * @param convertedAmount {@code amount} expressed in the target currency
     *                        (equal to {@code amount} when {@code from == to})
     * @param rate            units of {@code to} per 1 unit of {@code from}
     *                        ({@link BigDecimal#ONE} for the same-currency case)
     * @param rateDate        the vigency date of {@code rate} ({@code asOf}'s
     *                        own date for the same-currency case)
     */
    public record ConversionResult(BigDecimal amount, BigDecimal convertedAmount, BigDecimal rate, LocalDate rateDate) {}
}
