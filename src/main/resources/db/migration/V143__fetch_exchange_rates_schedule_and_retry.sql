SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V143: FETCH_EXCHANGE_RATES — move the cron to 4:00 PM and turn on the
-- generic retry policy (max_retry_attempts/retry_delay_seconds, V110/V123).
--
-- The job used to fire at 5:30 PM with retries disabled (0/0, seeded before
-- V110 added those columns). FetchExchangeRatesJobRunner now reports
-- JobRunResult.failure(...) whenever a currency comes back ALREADY_HAD_TODAY
-- (same operation_date as the latest stored row — i.e. BCV hasn't published
-- today's rate yet), so JobExecutionService's existing fixed-backoff retry
-- engine (Thread.sleep between attempts, safe on schedulerExecutor) is what
-- actually retries the run — no new mechanism, this just turns dials that
-- already exist for every scheduled job (visible/editable from the admin
-- "Editar trabajo" modal).
--
-- 4:00 PM is BCV's earliest typical publish time (vs the 5:30 PM the job used
-- to wait for) — moving the cron earlier plus 5 retries * 30s apart covers
-- publishes anywhere from 4:00 PM to ~4:02:30 PM immediately and up to
-- ~4:32:30 PM via retries, without waiting until 5:30 PM regardless.
-- ────────────────────────────────────────────────────────────────────────────

UPDATE scheduled_jobs
SET cron_expression      = '0 0 16 * * MON-FRI',
    max_retry_attempts   = 5,
    retry_delay_seconds  = 30
WHERE code = 'FETCH_EXCHANGE_RATES';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM scheduled_jobs
        WHERE code = 'FETCH_EXCHANGE_RATES'
          AND cron_expression = '0 0 16 * * MON-FRI'
          AND max_retry_attempts = 5
          AND retry_delay_seconds = 30
    ) THEN
        RAISE EXCEPTION 'V143: FETCH_EXCHANGE_RATES schedule/retry update did not apply — check the row still exists with that code (seeded by V93)';
    END IF;
END $$;
