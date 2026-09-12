SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V110: retry policy for scheduled_jobs.
--
-- Today a failed ScheduledJobRunner.run() just leaves the run FAILED — no
-- automatic retry. This adds a fixed-backoff retry policy configurable per
-- job (max_retry_attempts additional tries after the first failure,
-- retry_delay_seconds fixed wait between attempts), plus attempt_count on
-- scheduled_job_runs so the audit trail shows how many tries the finalized
-- run actually took.
-- ────────────────────────────────────────────────────────────────────────────

ALTER TABLE scheduled_jobs
    ADD COLUMN max_retry_attempts SMALLINT NOT NULL DEFAULT 0
        CHECK (max_retry_attempts >= 0 AND max_retry_attempts <= 10),
    ADD COLUMN retry_delay_seconds INT NOT NULL DEFAULT 0
        CHECK (retry_delay_seconds >= 0 AND retry_delay_seconds <= 3600);

ALTER TABLE scheduled_job_runs
    ADD COLUMN attempt_count SMALLINT NOT NULL DEFAULT 1;
