package com.fenixcore.optibienestar360.modules.exchangerate.service;

import com.fenixcore.optibienestar360.core.util.AppTimeZone;
import com.fenixcore.optibienestar360.modules.catalog.entity.Country;
import com.fenixcore.optibienestar360.modules.catalog.repository.CountryRepository;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.currency.repository.CurrencyRepository;
import com.fenixcore.optibienestar360.modules.exchangerate.client.ExchangeRatesApiClient;
import com.fenixcore.optibienestar360.modules.exchangerate.client.RateResponse;
import com.fenixcore.optibienestar360.modules.scheduling.repository.ScheduledJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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
    private static final ZoneId CARACAS = AppTimeZone.ZONE;
    private static final LocalTime VIGENCY_TIME = LocalTime.of(8, 0);
    private static final String VIGENCY_COUNTRY_ISO_CODE = "VE";

    /**
     * Code of the {@code scheduled_jobs} row this service reads its
     * {@code baseUrl} parameter from (V94) — resolved here rather than a
     * {@code app.exchange-rates-api.base-url} Spring property so a future
     * job hitting a different external API just carries its own
     * {@code parameters}, instead of the app accumulating one env var per
     * integration.
     */
    private static final String JOB_CODE = "FETCH_EXCHANGE_RATES";

    private final ExchangeRatesApiClient apiClient;
    private final CurrencyRepository currencyRepository;
    private final CountryRepository countryRepository;
    private final ScheduledJobRepository scheduledJobRepository;
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

        Optional<String> baseUrl = resolveApiBaseUrl();
        if (baseUrl.isEmpty()) {
            log.error("{} has no 'baseUrl' parameter configured — aborting ingestion run "
                    + "(set it via PUT /v1/admin/scheduled-jobs/{{uuid}})", JOB_CODE);
            for (String code : BASE_CURRENCY_CODES) {
                results.add(new IngestionSummary.CurrencyResult(
                        code, IngestionSummary.Status.FETCH_FAILED, "job_base_url_not_configured"));
            }
            return new IngestionSummary(results);
        }

        for (String baseCode : BASE_CURRENCY_CODES) {
            results.add(ingestOne(baseCode, quote.get(), vigencyCountry.get(), baseUrl.get()));
        }
        return new IngestionSummary(results);
    }

    /**
     * Reads {@code baseUrl} out of {@code FETCH_EXCHANGE_RATES.parameters}
     * (V94) — the single source of truth for this integration's endpoint,
     * shared by both the scheduled run and the admin quick-action endpoint
     * (neither hardcodes it or reads a Spring property).
     */
    private Optional<String> resolveApiBaseUrl() {
        return scheduledJobRepository.findByCode(JOB_CODE)
                .map(job -> job.getParameters().get("baseUrl"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(url -> !url.isBlank());
    }

    private IngestionSummary.CurrencyResult ingestOne(String baseCode, Currency quote, Country vigencyCountry, String baseUrl) {
        Optional<RateResponse> response = apiClient.fetchRate(baseCode, baseUrl);
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

        ExchangeRateWriter.WriteOutcome outcome;
        try {
            outcome = writer.insert(base.get(), quote, rateResponse.rate(), operationDate.get(), validFrom);
        } catch (DataIntegrityViolationException raceLostToAnotherRun) {
            // ExchangeRateWriter already checks existence before inserting —
            // this only fires on a genuine race (another run for the same
            // pair/day committed between that check and this flush). Caught
            // here, outside any transaction of our own, so there is nothing
            // for Spring to mark rollback-only over.
            log.info("Rate for {}->{} on {} was ingested by a concurrent run — treating as already-had-today",
                    baseCode, quote.getCode(), operationDate.get());
            outcome = ExchangeRateWriter.WriteOutcome.ALREADY_HAD_TODAY;
        }

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
            return Optional.of(OffsetDateTime.parse(timestamp).atZoneSameInstant(CARACAS).toLocalDate());
        } catch (DateTimeException ignored) {
            // covers DateTimeParseException too (it's a DateTimeException subclass) — fall through to Instant, then LocalDate
        }
        try {
            return Optional.of(Instant.parse(timestamp).atZone(CARACAS).toLocalDate());
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
