SET search_path TO app, public;

-- ============================================================
-- V93: exchange_rate_ingestion — support tables for
-- FetchExchangeRatesJob (ADR 0015 §3).
--
-- holidays: the calendar FetchExchangeRatesJob's BusinessDayCalculator
-- consults to compute `valid_from` (next business day after the BCV
-- publish date, at 8:00 AM America/Caracas — a Friday publish stays
-- vigente all weekend until Monday 8 AM). Generalized (not Venezuela-only)
-- so any country/state/city calendar can be modeled the same way, adapted
-- from the legacy tglo_DIA_FERIADO pattern (proyecto-iv-mh):
--
--   * Scope is derived from which of country_id/state_id/city_id are set,
--     enforcing a strict hierarchy (GENERAL < NATIONAL < REGIONAL < LOCAL):
--       - all three NULL                      -> GENERAL   (applies everywhere)
--       - country_id set, state/city NULL     -> NATIONAL  (whole country)
--       - country_id + state_id, city NULL    -> REGIONAL  (whole state)
--       - country_id + state_id + city_id set -> LOCAL     (specific city)
--     No redundant "scope" column is stored — it is fully derivable from the
--     three FKs (see Holiday.scope() in Java).
--   * Recurrence: is_recurring + recurrence_type (NONE/WEEKLY/MONTHLY/ANNUAL)
--     + repetitions_count let a single row stand for a holiday that repeats
--     forever (repetitions_count = 0) or a fixed number of times, instead of
--     requiring a fresh INSERT every year — NONE is a one-off dated row
--     (used for the movable Semana Santa dates, which aren't a fixed
--     month/day). Same dual-identifier + BaseEntity shape as
--     currencies/organizations/exchange_rates (V84/V85/V86).
--
-- OPERATIONAL DEBT (accepted per ADR 0015 "Negativas / a mitigar"):
-- no library computes Easter / movable Semana Santa dates. The NONE-typed
-- rows (Jueves Santo / Viernes Santo) still need MANUAL ANNUAL MAINTENANCE —
-- someone must insert next year's movable dates before January 1, or the
-- business-day calculator will treat a holiday as a normal day and compute
-- the wrong `valid_from`. The fixed-date holidays seeded below as ANNUAL
-- recurring rows do NOT need this — they are seeded once and match every
-- year going forward. Low frequency (~once a year, movable dates only),
-- documented here and in the ADR.
-- ============================================================

CREATE TABLE holidays
(
    holidays_id       BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid              UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    code              VARCHAR(50),
    name              VARCHAR(150) NOT NULL,
    description       VARCHAR(255),

    -- Anchor date: for NONE, the exact one-off date; for ANNUAL only
    -- month+day matter (the year is just the first occurrence); for WEEKLY
    -- only the day-of-week matters; for MONTHLY only the day-of-month matters.
    holiday_date      DATE         NOT NULL,
    is_recurring      BOOLEAN      NOT NULL DEFAULT FALSE,
    recurrence_type   VARCHAR(12)  NOT NULL DEFAULT 'NONE'
                                   CHECK (recurrence_type IN ('NONE', 'WEEKLY', 'MONTHLY', 'ANNUAL')),
    -- 0 = repeats forever (when is_recurring); ignored when NOT recurring.
    repetitions_count INT          NOT NULL DEFAULT 0 CHECK (repetitions_count >= 0),

    -- Scope hierarchy — all nullable; absence widens the scope. Enforced
    -- below: state requires country, city requires state (can't skip a level).
    country_id        BIGINT       REFERENCES countries (countries_id),
    state_id          BIGINT       REFERENCES states (states_id),
    city_id           BIGINT       REFERENCES cities (cities_id),

    is_active         BOOLEAN      NOT NULL DEFAULT TRUE,
    status            VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,

    CONSTRAINT chk_holidays_recurrence_coherence CHECK (
        (is_recurring = FALSE AND recurrence_type = 'NONE')
        OR (is_recurring = TRUE AND recurrence_type <> 'NONE')
    ),
    CONSTRAINT chk_holidays_state_requires_country CHECK (state_id IS NULL OR country_id IS NOT NULL),
    CONSTRAINT chk_holidays_city_requires_state CHECK (city_id IS NULL OR state_id IS NOT NULL)
);

CREATE INDEX idx_holidays_scope      ON holidays (country_id, state_id, city_id);
CREATE INDEX idx_holidays_is_active  ON holidays (is_active) WHERE is_active;

CREATE TRIGGER trg_holidays_updated_at
    BEFORE UPDATE ON holidays
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Seed: Venezuela's 2026 public holidays, scoped NATIONAL (country_id =
-- Venezuela, state/city NULL). Fixed-date holidays are seeded as ANNUAL
-- recurring rows (repetitions_count = 0 = forever) — they never need
-- re-seeding again. Semana Santa is movable and computed off Easter Sunday
-- 2026 = April 5 (Jueves Santo = Apr 2, Viernes Santo = Apr 3 — both are
-- non-business days by long-standing convention even though only Good
-- Friday is a strict national non-working day in some years' official
-- gazette; BCV itself does not publish on either day) — these stay
-- recurrence_type = NONE (one-off dated rows) since Easter isn't a fixed
-- month/day; ONLY these movable dates need yearly re-insertion.
INSERT INTO holidays (name, holiday_date, is_recurring, recurrence_type, repetitions_count, country_id)
SELECT v.name, v.holiday_date, TRUE, 'ANNUAL', 0,
       (SELECT countries_id FROM countries WHERE iso_code = 'VE')
FROM (VALUES
    ('Año Nuevo',                       DATE '2026-01-01'),
    ('Declaración de Independencia',    DATE '2026-04-19'),
    ('Día del Trabajador',              DATE '2026-05-01'),
    ('Batalla de Carabobo',             DATE '2026-06-24'),
    ('Día de la Independencia',         DATE '2026-07-05'),
    ('Natalicio de Simón Bolívar',      DATE '2026-07-24'),
    ('Día de la Resistencia Indígena',  DATE '2026-10-12'),
    ('Nochebuena',                      DATE '2026-12-24'),
    ('Navidad',                         DATE '2026-12-25'),
    ('Fin de Año',                      DATE '2026-12-31')
) AS v(name, holiday_date);

INSERT INTO holidays (name, holiday_date, is_recurring, recurrence_type, repetitions_count, country_id)
SELECT v.name, v.holiday_date, FALSE, 'NONE', 0,
       (SELECT countries_id FROM countries WHERE iso_code = 'VE')
FROM (VALUES
    ('Jueves Santo',  DATE '2026-04-02'),
    ('Viernes Santo', DATE '2026-04-03')
) AS v(name, holiday_date);

-- ─── Fail loudly rather than silently seed NULL country_id (= GENERAL scope
-- by accident) if Venezuela isn't seeded in `countries` — this migration is
-- the wrong place to own that seed (it belongs to V8__locations.sql), so a
-- missing dependency must abort the migrate, not degrade the seeded rows'
-- scope silently.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM countries WHERE iso_code = 'VE') THEN
        RAISE EXCEPTION 'V93: Venezuela (iso_code = VE) not found in countries — seed it in V8__locations.sql first';
    END IF;

    IF EXISTS (SELECT 1 FROM holidays WHERE country_id IS NULL) THEN
        RAISE EXCEPTION 'V93: seeded holiday rows ended up with NULL country_id (GENERAL scope) unexpectedly';
    END IF;
END $$;


-- ─── Seed: FetchExchangeRatesJob registration ──────────────────────────────
-- 5:30 PM America/Caracas, Monday-Friday only — BCV publishes its reference
-- rate on business days, typically between 4 PM and 5 PM; no point running
-- on weekends when nothing new is published. The runner bean
-- (FetchExchangeRatesJobRunner, modules/scheduling/service/runners/)
-- delegates to ExchangeRateIngestionService.fetchAndStoreLatest(), which
-- pulls USD/VES and EUR/VES from exchange-rates-api and inserts idempotently
-- by (base_currency_id, quote_currency_id, operation_date). No new JOB_*
-- permission is needed — scheduled_jobs rows are not individually
-- permissioned (see V22's global JOB_VIEW_ALL/JOB_CREATE/JOB_UPDATE/
-- JOB_DELETE/JOB_RUN_NOW grants), this job is runnable out of the box
-- through the existing generic POST /v1/admin/scheduled-jobs/{uuid}/run-now.
INSERT INTO scheduled_jobs (code, display_name, description, cron_expression, timezone)
VALUES (
    'FETCH_EXCHANGE_RATES',
    'Obtención diaria de tasas de cambio BCV',
    'Consulta exchange-rates-api (USD/VES, EUR/VES) y registra la tasa de referencia BCV del día, calculando su fecha de vigencia (siguiente día hábil venezolano a las 8:00 AM).',
    '0 30 17 * * MON-FRI',
    'America/Caracas'
);
