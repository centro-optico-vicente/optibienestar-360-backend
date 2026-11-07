package com.fenixcore.optibienestar360.modules.scheduling.service.runners;

import com.fenixcore.optibienestar360.core.util.AppTimeZone;
import com.fenixcore.optibienestar360.modules.exchangerate.service.ExchangeRateIngestionService;
import com.fenixcore.optibienestar360.modules.exchangerate.service.IngestionSummary;
import com.fenixcore.optibienestar360.modules.scheduling.repository.ScheduledJobRepository;
import com.fenixcore.optibienestar360.modules.scheduling.service.JobRunResult;
import com.fenixcore.optibienestar360.modules.scheduling.service.ScheduledJobRunner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Daily "fetch BCV exchange rates" job. Resolved by code
 * {@code FETCH_EXCHANGE_RATES} (seeded as a row in {@code scheduled_jobs} by
 * V93). Delegates entirely to
 * {@link ExchangeRateIngestionService#fetchAndStoreLatest()} — no ingestion
 * logic lives here, matching the "single orchestration point" shape shared
 * with the admin quick-action endpoint (ADR 0015 §3).
 *
 * <p>Per the ADR's "degrade, never block" philosophy, a per-currency fetch
 * failure alone is still reported as a partial success — this runner never
 * returns {@link JobRunResult#failure} just because a currency was
 * unreachable or unparseable.</p>
 *
 * <p><b>Exception — the unchanged-rate retry:</b> if any currency comes back
 * {@link IngestionSummary.Status#ALREADY_HAD_TODAY} (BCV hasn't published
 * today's rate yet as of this call — same {@code operation_date} as the
 * latest stored row), this run deliberately returns
 * {@link JobRunResult#failure(String, java.util.Map)} so
 * {@code JobExecutionService}'s existing fixed-backoff retry engine
 * (the job's own {@code max_retry_attempts}/{@code retry_delay_seconds}
 * columns — same fields every other scheduled job uses, editable from the
 * admin "Editar trabajo" modal) retries the whole run. A retry that still
 * finds nothing new after the last attempt surfaces as a genuinely FAILED
 * {@code scheduled_job_runs} row — a deliberate visibility signal (BCV is
 * later than usual), not silently swallowed.</p>
 */
@Component
@Slf4j
public class FetchExchangeRatesJobRunner implements ScheduledJobRunner {

    public static final String CODE = "FETCH_EXCHANGE_RATES";

    /**
     * BCV publishes in the afternoon. Before this time "nothing new upstream"
     * is the expected state (e.g. the 07:00 catch-up run when nothing was
     * missed), so it is not reported as a failure.
     */
    static final LocalTime PUBLISH_WINDOW_START = LocalTime.of(16, 0);

    private final ScheduledJobRepository jobRepository;
    private final ExchangeRateIngestionService ingestionService;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public FetchExchangeRatesJobRunner(ScheduledJobRepository jobRepository, ExchangeRateIngestionService ingestionService) {
        this(jobRepository, ingestionService, Clock.system(AppTimeZone.ZONE));
    }

    FetchExchangeRatesJobRunner(ScheduledJobRepository jobRepository, ExchangeRateIngestionService ingestionService, Clock clock) {
        this.jobRepository = jobRepository;
        this.ingestionService = ingestionService;
        this.clock = clock;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public JobRunResult run() {
        // The job's configured timezone (read from its own scheduled_jobs
        // row, per the MembershipStatusJobRunner pattern) isn't needed for
        // any date arithmetic here — ExchangeRateIngestionService always
        // passes Venezuela + America/Caracas to BusinessDayCalculator
        // regardless of when the cron fires —
        // but resolving it still surfaces a misconfigured row early and
        // keeps this runner consistent with the rest of the framework.
        resolveZone();

        try {
            IngestionSummary summary = ingestionService.fetchAndStoreLatest();
            log.info("FETCH_EXCHANGE_RATES completed: {}", summary.toSummaryMap());

            // A failed fetch is retried too (V229): the upstream only ever exposes
            // the latest publication, so a run lost to an outage loses that day for good.
            boolean anyFailed = summary.currencies().stream()
                    .anyMatch(r -> r.status() == IngestionSummary.Status.FETCH_FAILED
                            || r.status() == IngestionSummary.Status.PARSE_FAILED);
            if (anyFailed) {
                log.warn("FETCH_EXCHANGE_RATES: at least one currency failed to fetch/parse — reporting failure "
                        + "so the job's retry policy kicks in");
                return JobRunResult.failure("rate_fetch_failed", summary.toSummaryMap());
            }

            boolean anyUnchanged = summary.currencies().stream()
                    .anyMatch(r -> r.status() == IngestionSummary.Status.ALREADY_HAD_TODAY);
            boolean inPublishWindow = !LocalTime.now(clock.withZone(AppTimeZone.ZONE)).isBefore(PUBLISH_WINDOW_START);
            if (anyUnchanged && inPublishWindow) {
                log.warn("FETCH_EXCHANGE_RATES: at least one currency unchanged from the latest stored "
                        + "rate (BCV hasn't published today's rate yet) — reporting failure so the job's "
                        + "retry policy (max_retry_attempts/retry_delay_seconds) kicks in");
                return JobRunResult.failure("rate_unchanged_since_last_stored", summary.toSummaryMap());
            }

            return JobRunResult.success(summary.toSummaryMap());
        } catch (RuntimeException ex) {
            log.error("FETCH_EXCHANGE_RATES failed unexpectedly", ex);
            return JobRunResult.failure(ex.getMessage());
        }
    }

    private ZoneId resolveZone() {
        return jobRepository.findByCode(CODE)
                .map(job -> {
                    try {
                        return ZoneId.of(job.getTimezone());
                    } catch (RuntimeException ex) {
                        log.warn("Invalid timezone '{}' on {} job row — falling back to {}",
                                job.getTimezone(), CODE, AppTimeZone.ZONE);
                        return AppTimeZone.ZONE;
                    }
                })
                .orElse(AppTimeZone.ZONE);
    }
}
