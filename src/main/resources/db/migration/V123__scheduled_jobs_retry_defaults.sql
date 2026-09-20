SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V123: raise scheduled_jobs retry policy defaults from 0/0 (V110) to 5/10.
--
-- A job created without an explicit retry policy silently retried zero
-- times, which is rarely the intent. Only the column DEFAULT changes here —
-- existing rows keep whatever value they already have.
-- ────────────────────────────────────────────────────────────────────────────

ALTER TABLE scheduled_jobs
    ALTER COLUMN max_retry_attempts SET DEFAULT 5,
    ALTER COLUMN retry_delay_seconds SET DEFAULT 10;
