package com.fenixcore.optibienestar360.modules.currency.service;

import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.currency.exception.NoExchangeRateAvailableException;
import com.fenixcore.optibienestar360.modules.organization.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Read-side helper for DTOs whose amount has no settled/persisted snapshot
 * (ADR 0015 §6 "Caso B" — a plan's sticker price, an active membership, a
 * PENDING bonus/prize award). Converts {@code amount} from its own currency
 * to the organization's official currency <b>as of now</b>, degrading to
 * {@link #none()} (all-null) rather than throwing when no rate is vigente —
 * same "degrade, never block" rule as everywhere else this ADR touches a
 * read path.
 *
 * <p>Settled rows (an approved payment, a PAID bonus/prize award) never use
 * this — they read their own persisted {@code exchange_rate_used}/
 * {@code exchange_rate_date} snapshot columns instead, so a receipt keeps
 * showing the rate that was vigente at payout regardless of what this
 * helper would compute today.</p>
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConversionEnricher {

    private final CurrencyConversionService conversionService;
    private final OrganizationRepository organizationRepository;

    /** Live conversion of {@code amount} (in {@code from}) to the org's official currency, as of now. */
    public Conversion toOfficial(BigDecimal amount, Currency from) {
        if (amount == null || from == null) {
            return none();
        }
        Currency official = organizationRepository.findSingleton().getOfficialCurrency();
        if (official.getId().equals(from.getId())) {
            return none();
        }
        try {
            CurrencyConversionService.ConversionResult r = conversionService.convert(amount, from, official, Instant.now());
            return new Conversion(r.convertedAmount(), official.getCode(), r.rate(), r.rateDate());
        } catch (NoExchangeRateAvailableException noRate) {
            return none();
        }
    }

    /** The organization's official currency code — e.g. to label a persisted snapshot that only recorded the rate, not the target currency. */
    public String officialCurrencyCode() {
        return organizationRepository.findSingleton().getOfficialCurrency().getCode();
    }

    public static Conversion none() {
        return new Conversion(null, null, null, null);
    }

    public record Conversion(BigDecimal amountConverted, String currencyCode, BigDecimal rate, LocalDate rateDate) {}
}
