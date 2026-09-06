package com.fenixcore.optibienestar360.modules.exchangerate.service;

import com.fenixcore.optibienestar360.modules.catalog.entity.Country;
import com.fenixcore.optibienestar360.modules.catalog.repository.CountryRepository;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.currency.repository.CurrencyRepository;
import com.fenixcore.optibienestar360.modules.exchangerate.client.ExchangeRatesApiClient;
import com.fenixcore.optibienestar360.modules.exchangerate.client.RateResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Single orchestration point for fetching + storing BCV exchange rates
 * (ADR 0015 §3) — both {@code FetchExchangeRatesJobRunner} (daily cron) and
 * the admin quick-action endpoint ({@code POST
 * /v1/admin/exchange-rates/fetch-latest}) call {@link #fetchAndStoreLatest()}
 * with no duplicated logic between them.
 *
 * <p>Per ADR 0015 §3 the ingested pairs are always base {@code USD}/{@code
 * EUR} against quote {@code VES}. A currency-level failure (unreachable
 * upstream, unparseable timestamp, already-ingested-today) never aborts the
 * whole run — "degrade, never block": each currency is attempted
 * independently and recorded in the returned {@link IngestionSummary}.</p>
 *
 * <p>Deliberately NOT wrapped in a single {@code @Transactional}: each
 * currency's insert happens in its own {@code REQUIRES_NEW} transaction via
 * {@link ExchangeRateWriter}, so a duplicate-day conflict on EUR can never
 * roll back an already-committed USD insert from the same run (or vice
 * versa).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeRateIngestionService {

    /** ADR 0015 §3 — the pairs ingested today; quote is always VES. */
    private static final List<String> BASE_CURRENCY_CODES = List.of("USD", "EUR");
    private static final String QUOTE_CURRENCY_CODE = "VES";

    /** BCV vigency policy (ADR 0015 §2/§3) — not a generic calendar default. */
    private static final ZoneId CARACAS = ZoneId.of("America/Caracas");
    private static final LocalTime VIGENCY_TIME = LocalTime.of(8, 0);
    private static final String VIGENCY_COUNTRY_ISO_CODE = "VE";

    private final ExchangeRatesApiClient apiClient;
    private final CurrencyRepository currencyRepository;
    private final CountryRepository countryRepository;
    private final BusinessDayCalculator businessDayCalculator;
    private final ExchangeRateWriter writer;

    public IngestionSummary fetchAndStoreLatest() {
        List<IngestionSummary.CurrencyResult> results = new ArrayList<>();

        Optional<Currency> quote = currencyRepository.findByCode(QUOTE_CURRENCY_CODE);
        if (quote.isEmpty()) {
            log.error("Quote currency {} not found in currencies catalog — aborting ingestion run", QUOTE_CURRENCY_CODE);
            for (String code : BASE_CURRENCY_CODES) {
                results.add(new IngestionSummary.CurrencyResult(
                        code, IngestionSummary.Status.FETCH_FAILED, "quote_currency_not_seeded"));
            }
            return new IngestionSummary(results);
        }

        Optional<Country> vigencyCountry = countryRepository.findByIsoCode(VIGENCY_COUNTRY_ISO_CODE);
        if (vigencyCountry.isEmpty()) {
            log.error("Vigency country {} not found in countries catalog — aborting ingestion run", VIGENCY_COUNTRY_ISO_CODE);
            for (String code : BASE_CURRENCY_CODES) {
                results.add(new IngestionSummary.CurrencyResult(
                        code, IngestionSummary.Status.FETCH_FAILED, "vigency_country_not_seeded"));
            }
            return new IngestionSummary(results);
        }

        for (String baseCode : BASE_CURRENCY_CODES) {
            results.add(ingestOne(baseCode, quote.get(), vigencyCountry.get()));
        }
        return new IngestionSummary(results);
    }

    private IngestionSummary.CurrencyResult ingestOne(String baseCode, Currency quote, Country vigencyCountry) {
        Optional<RateResponse> response = apiClient.fetchRate(baseCode);
        if (response.isEmpty()) {
            return new IngestionSummary.CurrencyResult(
                    baseCode, IngestionSummary.Status.FETCH_FAILED, "upstream_unreachable_or_error");
        }

        RateResponse rateResponse = response.get();
        if (rateResponse.rate() == null) {
            return new IngestionSummary.CurrencyResult(
                    baseCode, IngestionSummary.Status.FETCH_FAILED, "response_missing_rate");
        }

        Optional<LocalDate> operationDate = parseOperationDate(rateResponse.timestamp());
        if (operationDate.isEmpty()) {
            log.warn("Could not parse timestamp '{}' for {} — skipping this run's ingestion for that currency",
                    rateResponse.timestamp(), baseCode);
            return new IngestionSummary.CurrencyResult(
                    baseCode, IngestionSummary.Status.PARSE_FAILED, "unparseable_timestamp: " + rateResponse.timestamp());
        }

        Optional<Currency> base = currencyRepository.findByCode(baseCode);
        if (base.isEmpty()) {
            log.error("Base currency {} not found in currencies catalog — skipping", baseCode);
            return new IngestionSummary.CurrencyResult(
                    baseCode, IngestionSummary.Status.FETCH_FAILED, "base_currency_not_seeded");
        }

        Instant validFrom = businessDayCalculator.nextBusinessDayAt(operationDate.get(), vigencyCountry, VIGENCY_TIME, CARACAS);

        ExchangeRateWriter.WriteOutcome outcome = writer.insert(
                base.get(), quote, rateResponse.rate(), operationDate.get(), validFrom);

        return outcome == ExchangeRateWriter.WriteOutcome.INSERTED
                ? new IngestionSummary.CurrencyResult(baseCode, IngestionSummary.Status.FETCHED, null)
                : new IngestionSummary.CurrencyResult(baseCode, IngestionSummary.Status.ALREADY_HAD_TODAY, null);
    }

    /**
     * Defensive parsing of {@code RateResponse.timestamp} — its exact live
     * format is unconfirmed (see {@link RateResponse} javadoc). Tries
     * offset/instant ISO-8601 forms first (treating the value as the BCV
     * publish moment, converted to the equivalent Venezuelan calendar day),
     * then falls back to a bare date-only {@code LocalDate}. Returns empty
     * when neither parses — callers must skip that currency for this run,
     * never throw.
     */
    private Optional<LocalDate> parseOperationDate(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(OffsetDateTime.parse(timestamp).atZoneSameInstant(ZoneId.of("America/Caracas")).toLocalDate());
        } catch (DateTimeException ignored) {
            // covers DateTimeParseException too (it's a DateTimeException subclass) — fall through to Instant, then LocalDate
        }
        try {
            return Optional.of(Instant.parse(timestamp).atZone(ZoneId.of("America/Caracas")).toLocalDate());
        } catch (DateTimeException ignored) {
            // fall through to LocalDate
        }
        try {
            return Optional.of(LocalDate.parse(timestamp));
        } catch (DateTimeParseException ignored) {
            return Optional.empty();
        }
    }
}
