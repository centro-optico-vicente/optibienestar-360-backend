package com.fenixcore.optibienestar360.modules.scheduling.service.runners;

import com.fenixcore.optibienestar360.modules.exchangerate.service.ExchangeRateIngestionService;
import com.fenixcore.optibienestar360.modules.exchangerate.service.IngestionSummary;
import com.fenixcore.optibienestar360.modules.scheduling.repository.ScheduledJobRepository;
import com.fenixcore.optibienestar360.modules.scheduling.service.JobRunResult;
import com.fenixcore.optibienestar360.modules.scheduling.service.ScheduledJobRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
 * failure is still a partial success at the job level — this runner
 * essentially never returns {@link JobRunResult#failure(String)}; that path
 * is reserved for something genuinely unexpected (e.g. the {@code VES}/{@code
 * USD}/{@code EUR} seed rows themselves missing from {@code currencies}).</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FetchExchangeRatesJobRunner implements ScheduledJobRunner {

    public static final String CODE = "FETCH_EXCHANGE_RATES";

    private final ScheduledJobRepository jobRepository;
    private final ExchangeRateIngestionService ingestionService;

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
                        log.warn("Invalid timezone '{}' on {} job row — falling back to America/Caracas",
                                job.getTimezone(), CODE);
                        return ZoneId.of("America/Caracas");
                    }
                })
                .orElse(ZoneId.of("America/Caracas"));
    }
}
