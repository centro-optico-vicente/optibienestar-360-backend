package com.fenixcore.optibienestar360.modules.exchangerate.service;

import com.fenixcore.optibienestar360.modules.catalog.entity.Country;
import com.fenixcore.optibienestar360.modules.catalog.repository.CountryRepository;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.currency.repository.CurrencyRepository;
import com.fenixcore.optibienestar360.modules.exchangerate.client.ExchangeRatesApiClient;
import com.fenixcore.optibienestar360.modules.exchangerate.client.RateResponse;
import com.fenixcore.optibienestar360.modules.scheduling.entity.ScheduledJob;
import com.fenixcore.optibienestar360.modules.scheduling.repository.ScheduledJobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ExchangeRateIngestionService} (ADR 0015 §3) — the
 * single orchestration point shared by {@code FetchExchangeRatesJobRunner}
 * and the admin quick-action endpoint.
 */
@ExtendWith(MockitoExtension.class)
class ExchangeRateIngestionServiceTest {

    private static final String BASE_URL = "https://rates-api.jeaninformatico.com";

    @Mock private ExchangeRatesApiClient apiClient;
    @Mock private CurrencyRepository currencyRepository;
    @Mock private CountryRepository countryRepository;
    @Mock private ScheduledJobRepository scheduledJobRepository;
    @Mock private BusinessDayCalculator businessDayCalculator;
    @Mock private ExchangeRateWriter writer;

    private ExchangeRateIngestionService service() {
        return new ExchangeRateIngestionService(
                apiClient, currencyRepository, countryRepository, scheduledJobRepository, businessDayCalculator, writer);
    }

    /** Stubs the FETCH_EXCHANGE_RATES job row's parameters.baseUrl (V94) — required by every test. */
    private void stubConfiguredJob() {
        ScheduledJob job = new ScheduledJob();
        job.setCode("FETCH_EXCHANGE_RATES");
        job.setParameters(Map.of("baseUrl", BASE_URL));
        when(scheduledJobRepository.findByCode("FETCH_EXCHANGE_RATES")).thenReturn(Optional.of(job));
    }

    private static Country venezuela() {
        Country country = new Country();
        country.setId(1L);
        country.setIsoCode("VE");
        country.setName("Venezuela");
        return country;
    }

    private static Currency currency(long id, String code) {
        Currency c = new Currency();
        c.setId(id);
        c.setCode(code);
        c.setName(code);
        c.setSymbol(code);
        c.setDecimalPlaces((short) 2);
        return c;
    }

    private static RateResponse response(String code, String rate, String timestamp) {
        return new RateResponse("BCV", code, "VES", timestamp, new BigDecimal(rate),
                null, null, null, null, "ok");
    }

    @Test
    void successfullyIngestsBothUsdAndEur() {
        Currency ves = currency(1L, "VES");
        Currency usd = currency(2L, "USD");
        Currency eur = currency(3L, "EUR");
        when(currencyRepository.findByCode("VES")).thenReturn(Optional.of(ves));
        when(currencyRepository.findByCode("USD")).thenReturn(Optional.of(usd));
        when(currencyRepository.findByCode("EUR")).thenReturn(Optional.of(eur));
        when(countryRepository.findByIsoCode("VE")).thenReturn(Optional.of(venezuela()));
        stubConfiguredJob();

        when(apiClient.fetchRate("USD", BASE_URL)).thenReturn(Optional.of(response("USD", "805.42", "2026-09-04T16:00:00-04:00")));
        when(apiClient.fetchRate("EUR", BASE_URL)).thenReturn(Optional.of(response("EUR", "870.10", "2026-09-04T16:00:00-04:00")));

        Instant validFrom = Instant.parse("2026-09-07T12:00:00Z");
        when(businessDayCalculator.nextBusinessDayAt(eq(LocalDate.of(2026, 9, 4)), any(), eq(LocalTime.of(8, 0)), eq(ZoneId.of("America/Caracas"))))
                .thenReturn(validFrom);

        when(writer.insert(eq(usd), eq(ves), eq(new BigDecimal("805.42")), eq(LocalDate.of(2026, 9, 4)), eq(validFrom)))
                .thenReturn(ExchangeRateWriter.WriteOutcome.INSERTED);
        when(writer.insert(eq(eur), eq(ves), eq(new BigDecimal("870.10")), eq(LocalDate.of(2026, 9, 4)), eq(validFrom)))
                .thenReturn(ExchangeRateWriter.WriteOutcome.INSERTED);

        IngestionSummary summary = service().fetchAndStoreLatest();

        assertThat(summary.currencies()).hasSize(2);
        assertThat(summary.currencies()).allMatch(r -> r.status() == IngestionSummary.Status.FETCHED);
        assertThat(summary.toSummaryMap()).containsEntry("fetched", 2).containsEntry("failed", 0);
    }

    @Test
    void treatsSameDayDuplicateAsAlreadyHadTodayNotFailure() {
        Currency ves = currency(1L, "VES");
        Currency usd = currency(2L, "USD");
        Currency eur = currency(3L, "EUR");
        when(currencyRepository.findByCode("VES")).thenReturn(Optional.of(ves));
        when(currencyRepository.findByCode("USD")).thenReturn(Optional.of(usd));
        when(currencyRepository.findByCode("EUR")).thenReturn(Optional.of(eur));
        when(countryRepository.findByIsoCode("VE")).thenReturn(Optional.of(venezuela()));
        stubConfiguredJob();

        when(apiClient.fetchRate(any(), eq(BASE_URL))).thenReturn(Optional.of(response("X", "800.00", "2026-09-04T16:00:00-04:00")));
        when(businessDayCalculator.nextBusinessDayAt(any(), any(), any(), any())).thenReturn(Instant.parse("2026-09-07T12:00:00Z"));
        when(writer.insert(any(), any(), any(), any(), any())).thenReturn(ExchangeRateWriter.WriteOutcome.ALREADY_HAD_TODAY);

        IngestionSummary summary = service().fetchAndStoreLatest();

        assertThat(summary.currencies()).allMatch(r -> r.status() == IngestionSummary.Status.ALREADY_HAD_TODAY);
        assertThat(summary.toSummaryMap()).containsEntry("failed", 0).containsEntry("alreadyHadToday", 2);
    }

    /**
     * Regression test: a genuine race (another run inserted the same
     * pair/day between {@code ExchangeRateWriter}'s existence check and its
     * flush) surfaces as {@code writer.insert} throwing — outside any
     * transaction of this service's own, so it must be caught and treated
     * as ALREADY_HAD_TODAY, never left to bubble up as a 500 (this is
     * exactly the bug the "actualizar ahora" quick action hit in
     * production: catching it *inside* the same @Transactional method as
     * the failed flush still left Spring trying to commit an
     * already-rollback-only transaction).
     */
    @Test
    void aRaceLostToAnotherRunIsTreatedAsAlreadyHadTodayNotAFailure() {
        Currency ves = currency(1L, "VES");
        Currency usd = currency(2L, "USD");
        Currency eur = currency(3L, "EUR");
        when(currencyRepository.findByCode("VES")).thenReturn(Optional.of(ves));
        when(currencyRepository.findByCode("USD")).thenReturn(Optional.of(usd));
        when(currencyRepository.findByCode("EUR")).thenReturn(Optional.of(eur));
        when(countryRepository.findByIsoCode("VE")).thenReturn(Optional.of(venezuela()));
        stubConfiguredJob();

        when(apiClient.fetchRate(any(), eq(BASE_URL))).thenReturn(Optional.of(response("X", "800.00", "2026-09-04T16:00:00-04:00")));
        when(businessDayCalculator.nextBusinessDayAt(any(), any(), any(), any())).thenReturn(Instant.parse("2026-09-07T12:00:00Z"));
        when(writer.insert(any(), any(), any(), any(), any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate key"));

        IngestionSummary summary = service().fetchAndStoreLatest();

        assertThat(summary.currencies()).allMatch(r -> r.status() == IngestionSummary.Status.ALREADY_HAD_TODAY);
        assertThat(summary.toSummaryMap()).containsEntry("failed", 0).containsEntry("alreadyHadToday", 2);
    }

    @Test
    void oneCurrencyFetchFailureDoesNotPreventTheOtherFromSucceeding() {
        Currency ves = currency(1L, "VES");
        Currency eur = currency(3L, "EUR");
        when(currencyRepository.findByCode("VES")).thenReturn(Optional.of(ves));
        when(currencyRepository.findByCode("EUR")).thenReturn(Optional.of(eur));
        when(countryRepository.findByIsoCode("VE")).thenReturn(Optional.of(venezuela()));
        stubConfiguredJob();

        when(apiClient.fetchRate("USD", BASE_URL)).thenReturn(Optional.empty());
        when(apiClient.fetchRate("EUR", BASE_URL)).thenReturn(Optional.of(response("EUR", "870.10", "2026-09-04T16:00:00-04:00")));

        Instant validFrom = Instant.parse("2026-09-07T12:00:00Z");
        when(businessDayCalculator.nextBusinessDayAt(eq(LocalDate.of(2026, 9, 4)), any(), eq(LocalTime.of(8, 0)), eq(ZoneId.of("America/Caracas"))))
                .thenReturn(validFrom);
        when(writer.insert(eq(eur), eq(ves), any(), any(), any())).thenReturn(ExchangeRateWriter.WriteOutcome.INSERTED);

        IngestionSummary summary = service().fetchAndStoreLatest();

        assertThat(summary.currencies()).hasSize(2);
        assertThat(summary.currencies().stream().filter(r -> r.currencyCode().equals("USD")).findFirst().orElseThrow().status())
                .isEqualTo(IngestionSummary.Status.FETCH_FAILED);
        assertThat(summary.currencies().stream().filter(r -> r.currencyCode().equals("EUR")).findFirst().orElseThrow().status())
                .isEqualTo(IngestionSummary.Status.FETCHED);
    }

    /**
     * V94: baseUrl lives in FETCH_EXCHANGE_RATES.parameters, not a Spring
     * property — an admin who blanks/misconfigures it must degrade the run,
     * never NPE or throw, same "degrade, never block" rule as a missing
     * currency/country seed.
     */
    @Test
    void missingJobBaseUrlDegradesEveryCurrencyRatherThanThrowing() {
        when(currencyRepository.findByCode("VES")).thenReturn(Optional.of(currency(1L, "VES")));
        when(countryRepository.findByIsoCode("VE")).thenReturn(Optional.of(venezuela()));
        when(scheduledJobRepository.findByCode("FETCH_EXCHANGE_RATES")).thenReturn(Optional.empty());

        IngestionSummary summary = service().fetchAndStoreLatest();

        assertThat(summary.currencies()).hasSize(2);
        assertThat(summary.currencies()).allMatch(r -> r.status() == IngestionSummary.Status.FETCH_FAILED);
    }
}
