SET search_path TO app, public;

-- ============================================================
-- V94: scheduled_jobs.parameters — per-job JSONB configuration.
--
-- Until now, a job needing external config (a URL, an API key reference,
-- a batch size, ...) had no home for it except a new Spring "app.*"
-- property (sourced from a matching env var) per integration — doesn't
-- scale (every future job that calls a different external API adds
-- another env var) and isn't editable from the admin UI without a
-- redeploy. `parameters` gives every job row its own free-form JSONB bag,
-- same JSONB-via-@JdbcTypeCode(SqlTypes.JSON) precedent
-- `scheduled_job_runs.summary` already established (V22) — no new
-- dialect dependency, editable through the existing
-- POST/PUT /v1/admin/scheduled-jobs endpoints.
--
-- FETCH_EXCHANGE_RATES is the first consumer: its `baseUrl` parameter
-- replaces the `app.exchange-rates-api.base-url` /
-- `EXCHANGE_RATES_API_BASE_URL` env var from V93 — ExchangeRateIngestionService
-- now resolves it from this job's row instead.
-- ============================================================

ALTER TABLE scheduled_jobs
    ADD COLUMN parameters JSONB NOT NULL DEFAULT '{}'::jsonb;

UPDATE scheduled_jobs
SET parameters = jsonb_build_object('baseUrl', 'https://rates-api.jeaninformatico.com')
WHERE code = 'FETCH_EXCHANGE_RATES';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM scheduled_jobs
        WHERE code = 'FETCH_EXCHANGE_RATES' AND parameters ? 'baseUrl'
    ) THEN
        RAISE EXCEPTION 'V94: FETCH_EXCHANGE_RATES.parameters.baseUrl was not seeded — check V93 seeded the row with this exact code';
    END IF;
END $$;
