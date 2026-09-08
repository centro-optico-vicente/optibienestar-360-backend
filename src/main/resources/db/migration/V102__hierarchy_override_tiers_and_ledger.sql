SET search_path TO app, public;

-- ============================================================================
-- V102: hierarchy override bands + cascade ledger (hub plan
-- ".ai/plans/2026-09-07-hierarchical-commissions-plan.md", PR2 "Bandas de
-- override + cascada síncrona"). Builds on V101's rank/supervisor schema.
--
-- Two things ship here:
--   1. hierarchy_override_tiers — scoped by (rank_id, category), exactly like
--      commission_tiers is scoped by (plan_type, promoter_type_id). Each rank
--      can have a completely independent band table per category
--      (INSCRIPTION / COLLECTION) — a Coordinador's bands are unrelated to a
--      Supervisor's, and apertura bands are unrelated to cobranza bands.
--   2. promoter_hierarchy_overrides — a granular ledger, one row per source
--      event, mirroring commissions' own shape. Exactly one of
--      source_commission_id / source_override_id is set (CHECK): level-2
--      overrides (Supervisor) are born from a Commission; level-3+ overrides
--      (Coordinador and beyond) are born from the override the level right
--      below just earned — never from the original commission directly, so
--      the cascade can never skip a level.
--
-- currency_id on both tables follows the exact pattern commissions.currency_id
-- already established (ADR 0015) — see hub plan "Integración con ADR 0015".
-- ============================================================================


-- ─── 1. hierarchy_override_tiers ────────────────────────────────────────────
CREATE TABLE hierarchy_override_tiers
(
    hierarchy_override_tiers_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                        UUID         NOT NULL UNIQUE,

    name                        VARCHAR(80)  NOT NULL,
    rank_id                     BIGINT       NOT NULL REFERENCES promoter_ranks (promoter_ranks_id),
    category                    VARCHAR(20)  NOT NULL,

    -- Team volume threshold (NOT the beneficiary's own sales) — 0 = base band, always qualifies.
    threshold_count             INTEGER      NOT NULL DEFAULT 0,

    override_pct                NUMERIC(5, 2),
    flat_amount                 NUMERIC(10, 2),
    flat_amount_currency_id     BIGINT       REFERENCES currencies (currencies_id),

    period_strategy             VARCHAR(20)  NOT NULL DEFAULT 'MONTHLY',

    is_active                   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by                  UUID,
    updated_by                  UUID,

    CONSTRAINT chk_hierarchy_override_tiers_category
        CHECK (category IN ('INSCRIPTION', 'COLLECTION')),
    CONSTRAINT chk_hierarchy_override_tiers_period_strategy
        CHECK (period_strategy IN ('DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY', 'QUARTERLY', 'SEMIANNUAL', 'ANNUAL')),
    -- Exactly one of pct / flat — same shape as commissions/commission_tiers.
    CONSTRAINT chk_hierarchy_override_tiers_pct_xor_flat
        CHECK ((override_pct IS NOT NULL) <> (flat_amount IS NOT NULL)),
    CONSTRAINT chk_hierarchy_override_tiers_flat_needs_currency
        CHECK (flat_amount IS NULL OR flat_amount_currency_id IS NOT NULL)
);

CREATE TRIGGER trg_hierarchy_override_tiers_updated_at
    BEFORE UPDATE ON hierarchy_override_tiers
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Band lookup per (rank, category), highest threshold first.
CREATE INDEX idx_hierarchy_override_tiers_rank_category
    ON hierarchy_override_tiers (rank_id, category, threshold_count DESC);

-- Seed: pizarra numbers (hub plan §2). SUPERVISOR is flat (single base band);
-- COORDINADOR is banded on team volume for INSCRIPTION (1500/3000), flat for
-- COLLECTION. Team-volume metric for COLLECTION bands is a documented TBD
-- (hub plan §2 "Punto de extensión") — not exercised by this seed, since
-- COORDINADOR/COLLECTION has no threshold > 0 band yet.
INSERT INTO hierarchy_override_tiers (uuid, name, rank_id, category, threshold_count, override_pct, period_strategy)
SELECT gen_random_uuid(), v.name, pr.promoter_ranks_id, v.category, v.threshold_count, v.override_pct, 'MONTHLY'
FROM (VALUES
    ('SUPERVISOR',  'Override apertura — Supervisor',       'INSCRIPTION', 0,    10.00),
    ('SUPERVISOR',  'Override cobranza — Supervisor',       'COLLECTION',  0,    30.00),
    ('COORDINADOR', 'Override apertura — Coordinador base',  'INSCRIPTION', 0,    20.00),
    ('COORDINADOR', 'Override apertura — Coordinador 1500',  'INSCRIPTION', 1500, 25.00),
    ('COORDINADOR', 'Override apertura — Coordinador 3000',  'INSCRIPTION', 3000, 30.00),
    ('COORDINADOR', 'Override cobranza — Coordinador',       'COLLECTION',  0,    20.00)
) AS v(rank_code, name, category, threshold_count, override_pct)
JOIN promoter_ranks pr ON pr.code = v.rank_code;


-- ─── 2. promoter_hierarchy_overrides (cascade ledger) ───────────────────────
CREATE TABLE promoter_hierarchy_overrides
(
    promoter_hierarchy_overrides_id BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                             UUID        NOT NULL UNIQUE,

    -- The beneficiary — the Supervisor/Coordinador earning this override row.
    promoter_id                      BIGINT      NOT NULL REFERENCES promoters (promoters_id),

    -- Exactly one of these two is set (CHECK below): level-2 overrides point
    -- at the originating Commission; level-3+ overrides point at the
    -- immediate-inferior override that funded them.
    source_commission_id             BIGINT      REFERENCES commissions (commissions_id),
    source_override_id               BIGINT      REFERENCES promoter_hierarchy_overrides (promoter_hierarchy_overrides_id),

    category                         VARCHAR(20) NOT NULL,
    basis_amount                     NUMERIC(10, 2) NOT NULL,
    tier_id                          BIGINT      NOT NULL REFERENCES hierarchy_override_tiers (hierarchy_override_tiers_id),
    amount                           NUMERIC(10, 2) NOT NULL,
    currency_id                      BIGINT      NOT NULL REFERENCES currencies (currencies_id),

    period_strategy                  VARCHAR(20) NOT NULL,
    period_start                     DATE        NOT NULL,
    period_end                       DATE        NOT NULL,
    earned_at                        TIMESTAMPTZ NOT NULL DEFAULT now(),

    voided_at                        TIMESTAMPTZ,
    void_reason                      TEXT,

    is_active                        BOOLEAN     NOT NULL DEFAULT TRUE,
    status                           VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_at                       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                       TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by                       UUID,
    updated_by                       UUID,

    CONSTRAINT chk_promoter_hierarchy_overrides_category
        CHECK (category IN ('INSCRIPTION', 'COLLECTION')),
    CONSTRAINT chk_promoter_hierarchy_overrides_status
        CHECK (status IN ('PENDING', 'PAID', 'VOIDED')),
    CONSTRAINT chk_promoter_hierarchy_overrides_source_xor
        CHECK ((source_commission_id IS NOT NULL) <> (source_override_id IS NOT NULL))
);

CREATE TRIGGER trg_promoter_hierarchy_overrides_updated_at
    BEFORE UPDATE ON promoter_hierarchy_overrides
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE INDEX idx_promoter_hierarchy_overrides_promoter_period
    ON promoter_hierarchy_overrides (promoter_id, status, period_start, period_end);
CREATE INDEX idx_promoter_hierarchy_overrides_source_commission
    ON promoter_hierarchy_overrides (source_commission_id);
CREATE INDEX idx_promoter_hierarchy_overrides_source_override
    ON promoter_hierarchy_overrides (source_override_id);


-- ─── 3. Audit config ────────────────────────────────────────────────────────
-- Table renamed audit_entity_config → entity_config by V80.
INSERT INTO entity_config (entity_key, display_name, table_name) VALUES
    ('hierarchy_override_tier',   'Bandas de override jerárquico',   'hierarchy_override_tiers'),
    ('promoter_hierarchy_override', 'Overrides jerárquicos de promotor', 'promoter_hierarchy_overrides')
ON CONFLICT (entity_key) DO NOTHING;


-- ─── 4. Fail loudly rather than migrate into a half-applied state ──────────
DO $$
BEGIN
    IF (SELECT COUNT(*) FROM hierarchy_override_tiers) < 6 THEN
        RAISE EXCEPTION 'V102: hierarchy_override_tiers seed did not insert the expected 6 rows';
    END IF;
END $$;
