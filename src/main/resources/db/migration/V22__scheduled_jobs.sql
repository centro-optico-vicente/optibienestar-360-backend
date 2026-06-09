SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V22: scheduled_jobs framework — runtime-configurable cron registry.
--
-- Two tables:
--
--   scheduled_jobs       — the configurable registry. One row per job. Editable
--                          by admin endpoints at runtime; the
--                          DynamicScheduledJobsRegistry in the app picks up
--                          changes after each transaction commit and
--                          re-registers the corresponding ScheduledFuture
--                          against the JVM TaskScheduler (no restart needed).
--
--   scheduled_job_runs   — audit ledger. One row per execution attempt
--                          (scheduled, manual, or startup-replay). Records
--                          outcome, duration, runner-specific summary
--                          (JSONB), and actor identity for manual triggers.
--
-- Q1 (per-replica enable): the app reads SCHEDULER_ENABLED env var via
--     `app.scheduler.enabled` property. When false, the registry bean is
--     not instantiated — the replica still serves HTTP including the
--     manual /run-now endpoint, but does not register the cron triggers.
--     Today the deployment is single-replica so this flag is unused; in a
--     future multi-replica scaling, pin scheduling to one replica via env.
--
-- Q2 (hot reload): admin PUT to /scheduled-jobs/{uuid} updates the row and
--     a TransactionSynchronizationManager afterCommit hook calls
--     registry.reschedule(updated). No polling thread. Changes effective
--     from the next nextExecution() computation.
--
-- Q3 (manual trigger): POST /scheduled-jobs/{uuid}/run-now invokes the
--     runner via a CompletableFuture against the schedulerExecutor pool.
--     The HTTP response is 200 if the runner completes within
--     max_sync_seconds; otherwise the future keeps running in background
--     and the response is 202 with a runUuid the admin can poll via
--     GET /scheduled-jobs/{uuid}/runs/{runUuid}.
--
-- Number bump note: this migration takes V22 because the framework
-- introduces cross-cutting infrastructure (cron registry + audit + perms)
-- needed before the membership status sweep can land. The previously-
-- planned V22__payments.sql cascades to V23, and downstream planned
-- migrations bump +1. This is the 3rd renumeration of the project (see
-- 02-database.md "Renumeración del 2026-06" note).
-- ────────────────────────────────────────────────────────────────────────────


-- scheduled_jobs: per-job configuration. The `code` column is the lookup
-- key the app uses to resolve a row to its ScheduledJobRunner bean.
CREATE TABLE scheduled_jobs
(
    scheduled_jobs_id     BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                  UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    -- Identity
    code                  VARCHAR(80)  NOT NULL UNIQUE,
    display_name          VARCHAR(120) NOT NULL,
    description           TEXT,

    -- Schedule
    cron_expression       VARCHAR(120) NOT NULL,
    timezone              VARCHAR(60)  NOT NULL DEFAULT 'America/Caracas',
    enabled               BOOLEAN      NOT NULL DEFAULT TRUE,

    -- Execution policy
    -- allow_concurrent=FALSE rejects (scheduled or manual) starts while a
    -- previous run is still RUNNING. Set TRUE only for idempotent jobs.
    allow_concurrent      BOOLEAN      NOT NULL DEFAULT FALSE,
    -- max_sync_seconds caps how long the manual /run-now waits for the
    -- result before bailing to a 202 + runUuid for polling. 0 = always
    -- async. >= 86400 effectively always sync.
    max_sync_seconds      INT          NOT NULL DEFAULT 30 CHECK (max_sync_seconds >= 0),

    -- Soft mutex (single-replica). Future: backed by pg_try_advisory_lock
    -- for multi-replica HA. The column is audit-visible either way so the
    -- admin can see when a run is in flight.
    lock_held             BOOLEAN      NOT NULL DEFAULT FALSE,

    -- Bookkeeping snapshot (last execution outcome — runs ledger has the
    -- full history; this is the cached "current state" for admin lists)
    last_run_at           TIMESTAMPTZ,
    last_run_status       VARCHAR(20),
    next_run_at           TIMESTAMPTZ,

    -- Audit + soft-delete (mirrors BaseEntity)
    is_active             BOOLEAN      NOT NULL DEFAULT TRUE,
    status                VARCHAR(50)  NOT NULL DEFAULT 'ENABLED'
                              CHECK (status IN ('ENABLED', 'DISABLED')),
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID
);

CREATE INDEX idx_scheduled_jobs_enabled
    ON scheduled_jobs (enabled)
    WHERE is_active AND enabled;

CREATE TRIGGER trg_scheduled_jobs_updated_at
    BEFORE UPDATE ON scheduled_jobs
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- scheduled_job_runs: audit ledger. One row per execution attempt.
-- Outcome RUNNING is the in-flight state; everything else is terminal.
-- `summary` is a per-runner JSONB payload (e.g. for MEMBERSHIP_STATUS_SWEEP:
-- {scanned, suspended, expired}). JSONB lets each runner pick its own
-- shape without a column-per-job.
CREATE TABLE scheduled_job_runs
(
    scheduled_job_runs_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                  UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    scheduled_job_id      BIGINT       NOT NULL REFERENCES scheduled_jobs (scheduled_jobs_id),

    started_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    finished_at           TIMESTAMPTZ,
    duration_ms           BIGINT,

    outcome               VARCHAR(20)  NOT NULL DEFAULT 'RUNNING'
                              CHECK (outcome IN (
                                  'RUNNING', 'SUCCESS', 'FAILED', 'TIMEOUT',
                                  'SKIPPED_CONCURRENT', 'CANCELED')),
    -- triggered_by traces how the run started:
    --   SCHEDULED — the registry's cron fired
    --   MANUAL    — admin clicked /run-now (triggered_by_user_uuid is set)
    --   STARTUP   — special reserved for future startup-replay; not used today
    triggered_by          VARCHAR(20)  NOT NULL
                              CHECK (triggered_by IN ('SCHEDULED', 'MANUAL', 'STARTUP')),
    triggered_by_user_uuid UUID,

    summary               JSONB,
    error_message         TEXT,

    -- Audit (BaseAuditEntity shape). Runs are conceptually immutable
    -- history; is_active is kept for codebase consistency (every table
    -- backed by the audit base has it) but is not used by application
    -- code today.
    is_active             BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID,

    -- Coherence
    CONSTRAINT chk_runs_finished_after_started CHECK (
        finished_at IS NULL OR finished_at >= started_at
    ),
    CONSTRAINT chk_runs_terminal_has_finished_at CHECK (
        outcome = 'RUNNING' OR finished_at IS NOT NULL
    ),
    CONSTRAINT chk_runs_manual_has_user CHECK (
        triggered_by <> 'MANUAL' OR triggered_by_user_uuid IS NOT NULL
    )
);

-- Admin history: most recent runs of a job first
CREATE INDEX idx_scheduled_job_runs_job_started
    ON scheduled_job_runs (scheduled_job_id, started_at DESC);

-- Concurrency check ("is there a RUNNING row for this job?")
CREATE INDEX idx_scheduled_job_runs_running
    ON scheduled_job_runs (scheduled_job_id)
    WHERE outcome = 'RUNNING';

CREATE TRIGGER trg_scheduled_job_runs_updated_at
    BEFORE UPDATE ON scheduled_job_runs
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ─── Permission catalog extension ──────────────────────────────────────────

-- New domain for the admin panel — groups the JOB_* permissions
INSERT INTO permission_domains (code, name, icon, description, display_order)
VALUES ('SCHEDULED_JOBS', 'Tareas programadas', 'i-lucide-clock',
        'Configuración y ejecución de procesos programados del sistema', 110);

-- 5 new permissions — JOB_* prefix; same naming pattern as PLAN_* / MEMBER_*
INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('JOB_VIEW_ALL', 'SCHEDULED_JOBS', 'Ver todas las tareas programadas y su historial de ejecuciones'),
    ('JOB_CREATE',   'SCHEDULED_JOBS', 'Crear nuevas tareas programadas'),
    ('JOB_UPDATE',   'SCHEDULED_JOBS', 'Modificar configuración (cron, zona horaria, estado) de una tarea programada'),
    ('JOB_DELETE',   'SCHEDULED_JOBS', 'Desactivar una tarea programada'),
    ('JOB_RUN_NOW',  'SCHEDULED_JOBS', 'Ejecutar manualmente una tarea fuera del horario programado')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code;

-- Grant the new perms to SYSTEM + ADMINISTRADOR (the two roles V6 wired up
-- with broad scope). SYSTEM gets everything; ADMINISTRADOR gets every JOB_*
-- perm — these are operational, not governance.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name IN ('SYSTEM', 'ADMINISTRADOR')
  AND p.name IN ('JOB_VIEW_ALL', 'JOB_CREATE', 'JOB_UPDATE', 'JOB_DELETE', 'JOB_RUN_NOW');


-- ─── Seed: first runtime-registered job ────────────────────────────────────

-- MEMBERSHIP_STATUS_SWEEP — daily 03:00 America/Caracas. The runner bean
-- (MembershipStatusJobRunner, modules/scheduling/service/runners/) calls
-- MembershipStatusService.applyDueTransitions(today) and fires email
-- notifications per ACTIVE→SUSPENDED→EXPIRED transition.
INSERT INTO scheduled_jobs (code, display_name, description, cron_expression, timezone)
VALUES (
    'MEMBERSHIP_STATUS_SWEEP',
    'Barrido diario de estatus de membresías',
    'Aplica transiciones ACTIVE→SUSPENDED→EXPIRED según fecha de vencimiento y período de gracia, y notifica al afiliado por email cuando su membresía cambia de estado.',
    '0 0 3 * * *',
    'America/Caracas'
);
