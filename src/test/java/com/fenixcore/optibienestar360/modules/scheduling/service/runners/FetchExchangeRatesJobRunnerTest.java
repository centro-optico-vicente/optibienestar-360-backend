package com.fenixcore.optibienestar360.modules.scheduling.service.runners;

import com.fenixcore.optibienestar360.modules.exchangerate.service.ExchangeRateIngestionService;
import com.fenixcore.optibienestar360.modules.exchangerate.service.IngestionSummary;
import com.fenixcore.optibienestar360.modules.scheduling.entity.ScheduledJob;
import com.fenixcore.optibienestar360.modules.scheduling.repository.ScheduledJobRepository;
import com.fenixcore.optibienestar360.modules.scheduling.service.JobRunResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FetchExchangeRatesJobRunner} — specifically the
 * unchanged-rate retry signal: this runner deliberately reports
 * {@link JobRunResult#failure} when any currency comes back
 * {@code ALREADY_HAD_TODAY}, so {@code JobExecutionService}'s existing
 * fixed-backoff retry engine (job's own {@code max_retry_attempts}/
 * {@code retry_delay_seconds}, V143) retries the whole run. A per-currency
 * FETCH_FAILED/PARSE_FAILED triggers it too since V229 (the upstream only
 * exposes the latest publication, so an outage would lose that day for good);
 * an unchanged rate only counts inside the afternoon publish window.
 */
@ExtendWith(MockitoExtension.class)
class FetchExchangeRatesJobRunnerTest {

    @Mock private ScheduledJobRepository jobRepository;
    @Mock private ExchangeRateIngestionService ingestionService;

    /** 17:00 Caracas — inside the BCV publish window. */
    private static final Clock AFTERNOON = Clock.fixed(Instant.parse("2026-11-06T21:00:00Z"), ZoneOffset.UTC);
    /** 07:00 Caracas — the morning catch-up run. */
    private static final Clock MORNING = Clock.fixed(Instant.parse("2026-11-06T11:00:00Z"), ZoneOffset.UTC);

    private FetchExchangeRatesJobRunner sut() {
        return sut(AFTERNOON);
    }

    private FetchExchangeRatesJobRunner sut(Clock clock) {
        return new FetchExchangeRatesJobRunner(jobRepository, ingestionService, clock);
    }

    private void stubJobRow() {
        ScheduledJob job = new ScheduledJob();
        job.setCode(FetchExchangeRatesJobRunner.CODE);
        job.setTimezone("America/Caracas");
        when(jobRepository.findByCode(FetchExchangeRatesJobRunner.CODE)).thenReturn(Optional.of(job));
    }

    @Test
    void run_succeedsWhenEveryCurrencyWasFetched() {
        stubJobRow();
        when(ingestionService.fetchAndStoreLatest()).thenReturn(new IngestionSummary(List.of(
                new IngestionSummary.CurrencyResult("USD", IngestionSummary.Status.FETCHED, null),
                new IngestionSummary.CurrencyResult("EUR", IngestionSummary.Status.FETCHED, null)
        )));

        JobRunResult result = sut().run();

        assertThat(result.success()).isTrue();
    }

    @Test
    void run_reportsFailureWhenAnyCurrencyIsUnchangedSoTheRetryPolicyKicksIn() {
        stubJobRow();
        when(ingestionService.fetchAndStoreLatest()).thenReturn(new IngestionSummary(List.of(
                new IngestionSummary.CurrencyResult("USD", IngestionSummary.Status.ALREADY_HAD_TODAY, null),
                new IngestionSummary.CurrencyResult("EUR", IngestionSummary.Status.FETCHED, null)
        )));

        JobRunResult result = sut().run();

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).isEqualTo("rate_unchanged_since_last_stored");
        // the partial summary is preserved on the failure — an admin looking
        // at the FAILED scheduled_job_runs row still sees EUR did fetch.
        assertThat(result.summary()).containsEntry("fetched", 1).containsEntry("alreadyHadToday", 1);
    }

    @Test
    void run_reportsFailureWhenACurrencyFailsToFetchSoTheRetryPolicyKicksIn() {
        stubJobRow();
        when(ingestionService.fetchAndStoreLatest()).thenReturn(new IngestionSummary(List.of(
                new IngestionSummary.CurrencyResult("USD", IngestionSummary.Status.FETCH_FAILED, "upstream_unreachable_or_error"),
                new IngestionSummary.CurrencyResult("EUR", IngestionSummary.Status.FETCHED, null)
        )));

        JobRunResult result = sut().run();

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).isEqualTo("rate_fetch_failed");
    }

    @Test
    void run_succeedsWhenTheLatestPublicationIsAlreadyStored() {
        stubJobRow();
        when(ingestionService.fetchAndStoreLatest()).thenReturn(new IngestionSummary(List.of(
                new IngestionSummary.CurrencyResult("USD", IngestionSummary.Status.UP_TO_DATE, null),
                new IngestionSummary.CurrencyResult("EUR", IngestionSummary.Status.UP_TO_DATE, null)
        )));

        JobRunResult result = sut().run();

        assertThat(result.success()).isTrue();
    }

    @Test
    void run_morningRunWithNothingNewIsNotAFailure() {
        stubJobRow();
        when(ingestionService.fetchAndStoreLatest()).thenReturn(new IngestionSummary(List.of(
                new IngestionSummary.CurrencyResult("USD", IngestionSummary.Status.ALREADY_HAD_TODAY, null),
                new IngestionSummary.CurrencyResult("EUR", IngestionSummary.Status.ALREADY_HAD_TODAY, null)
        )));

        JobRunResult result = sut(MORNING).run();

        assertThat(result.success()).isTrue();
    }

    @Test
    void run_reportsFailureWhenTheIngestionServiceThrows() {
        stubJobRow();
        when(ingestionService.fetchAndStoreLatest()).thenThrow(new IllegalStateException("boom"));

        JobRunResult result = sut().run();

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).isEqualTo("boom");
    }
}
