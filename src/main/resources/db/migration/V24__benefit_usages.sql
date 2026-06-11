SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V24: benefit_usages — audit ledger of every time an affiliate uses a
-- benefit at an ally (consultation, optical discount, pharmacy purchase, …).
--
-- This table is what the validator vertical writes to. Flow:
--   1. Ally operator calls GET /v1/ally/validate/{document} (Redis-cached,
--      not persisted) to confirm the member is current.
--   2. If solvent, the ally applies the benefit and calls
--      POST /v1/ally/benefit-usage with the structured detail of what was
--      consumed → that POST creates a row here.
--
-- Not a counter / cap table: per-plan caps ("max 2 dental visits / month")
-- are a service-layer rule that aggregates over this ledger at usage time;
-- they do NOT live in a separate quota table.
--
-- Snapshotting policy: we DO NOT snapshot membership status or pricing at
-- the moment of usage. The membership row is the source of truth; if a
-- membership is later canceled, the historical usages stay linked to that
-- defunct row and remain queryable via JOIN. Service code is responsible
-- for refusing to register usage when membership is not ACTIVE.
--
-- Indices target the queries vertical-7 / 02-database.md call out:
--   - per-member history (admin / affiliate detail page)
--   - per-ally history (ally portal "usages this month")
--   - per-service category (cross-ally analytics)
-- ────────────────────────────────────────────────────────────────────────────


CREATE TABLE benefit_usages
(
    benefit_usages_id      BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                   UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    -- Subject
    membership_id          BIGINT       NOT NULL REFERENCES memberships (memberships_id),

    -- Where + what
    ally_id                BIGINT       NOT NULL REFERENCES allies (allies_id),
    -- Which specific service from V11 ally_services. NULL when the usage
    -- doesn't map to a catalogued service (e.g. generic "pharmacy
    -- discount" without a corresponding ally_service row).
    ally_service_id        BIGINT       REFERENCES ally_services (ally_services_id),

    -- Who registered (ally-side operator). NULL when admin-side
    -- registration or back-fill — the audit fields below still capture the
    -- creating user via BaseEntity created_by.
    ally_user_id           BIGINT       REFERENCES ally_users (ally_users_id),

    -- When the usage actually happened. usage_date is the calendar date
    -- the ally renders to the affiliate (Caracas time per ADR 0010);
    -- usage_datetime is the precise instant the row was committed
    -- (useful for sequencing and fraud detection).
    usage_date             DATE         NOT NULL DEFAULT CURRENT_DATE,
    usage_datetime         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    -- Co-pay: amount the affiliate paid out of pocket at the counter even
    -- though the benefit was active (e.g. 10% of the consultation price).
    -- Both fields move together — CHECK below.
    copay_amount           NUMERIC(10, 2),
    copay_currency         VARCHAR(3),

    -- Service-specific structured payload. Different ally types record
    -- different facts:
    --   {"doctorName": "Dr. Pérez", "diagnosis": "..."}                 — clinic
    --   {"prescriptionRx": "-2.5 / -2.0", "frameModel": "Ray-Ban X"}   — optical
    --   {"medication": "Amoxicillin", "boxes": 1}                      — pharmacy
    -- JSONB so each ally captures only what's meaningful without a table
    -- explosion. Hibernate 6 maps it natively via @JdbcTypeCode(SqlTypes.JSON)
    -- — same precedent V22 scheduled_job_runs.summary established.
    metadata               JSONB,

    -- Free-form operator notes
    notes                  TEXT,

    -- Workflow status. REGISTERED is the default at INSERT.
    -- REVERSED  — admin reversed a duplicate or fraudulent registration.
    -- DISPUTED  — affiliate flagged the row; under review.
    is_active              BOOLEAN      NOT NULL DEFAULT TRUE,
    status                 VARCHAR(50)  NOT NULL DEFAULT 'REGISTERED'
                              CHECK (status IN ('REGISTERED', 'REVERSED', 'DISPUTED')),
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by             UUID,
    updated_by             UUID,

    -- Copay coherence — both move together
    CONSTRAINT chk_benefit_usages_copay_paired CHECK (
        (copay_amount IS NULL AND copay_currency IS NULL)
        OR
        (copay_amount IS NOT NULL AND copay_amount >= 0 AND copay_currency IS NOT NULL)
    ),
    -- usage_datetime should not lead usage_date by more than 1 day (anti
    -- typo + handles timezone edge between system clock and Caracas).
    CONSTRAINT chk_benefit_usages_datetime_aligned CHECK (
        usage_datetime <= (usage_date + INTERVAL '2 days')
        AND usage_datetime >= (usage_date - INTERVAL '1 day')
    ),
    -- Same UX guard as payments: anti future-dated usage
    CONSTRAINT chk_benefit_usages_not_future CHECK (
        usage_date <= CURRENT_DATE + INTERVAL '1 day'
    )
);


-- Per-member history view (admin opens member detail → see usages newest
-- first). Composite index also covers a simple membership_id lookup.
CREATE INDEX idx_benefit_usages_membership_date
    ON benefit_usages (membership_id, usage_date DESC);

-- Per-ally history + dashboards ("usages this month at clinic X").
CREATE INDEX idx_benefit_usages_ally_date
    ON benefit_usages (ally_id, usage_date DESC);

-- Cross-ally service-category analytics ("how many ophthalmology consultations
-- this quarter across every clinic"). Partial — rows without a catalogued
-- service stay out of the index since they can't be aggregated by category.
CREATE INDEX idx_benefit_usages_service_date
    ON benefit_usages (ally_service_id, usage_date DESC)
    WHERE ally_service_id IS NOT NULL;

-- Admin "active/non-reversed only" filter — partial index keeps the queue
-- view scans small.
CREATE INDEX idx_benefit_usages_active_status
    ON benefit_usages (status, usage_date DESC)
    WHERE is_active;

CREATE TRIGGER trg_benefit_usages_updated_at
    BEFORE UPDATE ON benefit_usages
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
