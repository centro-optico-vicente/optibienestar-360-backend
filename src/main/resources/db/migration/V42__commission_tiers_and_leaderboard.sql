SET search_path TO app, public;

-- V42: Automated commission engine — configurable tiers + leaderboard + prizes
-- (v2 PDF item #5 "Motor Automatizado de Comisiones y Premiaciones", the tier
-- sub-section; the bonus/awards sub-section already shipped in V37).
--
-- Four schema changes ship here, backing the code in the same PR:
--   1. commission_tiers        — DB-driven replacement of the hardcoded plan-type
--      switch in CommissionService. Volume-threshold tiers with an OPTIONAL
--      plan_type scope (NULL = all plans), so the v1 per-plan rates survive as
--      seeded tiers while volume tiers can be layered on top.
--   2. leaderboard_prizes       — prize config per (rank, period strategy).
--   3. leaderboard_prize_awards — idempotent ledger of prizes granted at close.
--   4. commission_period_summary VIEW — per (promoter, period) aggregate that
--      powers the leaderboard.
-- Plus the deferred FK on commissions.commission_tier_id (reserved in V26),
-- three COMMISSIONS permissions, and the LEADERBOARD_PRIZE_AWARD scheduled job.


-- ─── 1. commission_tiers ─────────────────────────────────────────────────────
CREATE TABLE commission_tiers
(
    commission_tiers_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                UUID         NOT NULL UNIQUE,

    name                VARCHAR(80)  NOT NULL,

    -- Optional plan scope. NULL = applies to every plan type; a value scopes the
    -- tier to that plan (the CHECK can't reach plans.type, matched service-side).
    plan_type           VARCHAR(20)
        CONSTRAINT chk_commission_tiers_plan_type
            CHECK (plan_type IS NULL OR plan_type IN ('INDIVIDUAL', 'FAMILIAR', 'CORPORATIVO')),

    -- The promoter qualifies for the tier when their new-subscriber count within
    -- the tier's period reaches this. 0 = base tier (always qualifies).
    threshold_count     INT          NOT NULL DEFAULT 0
        CONSTRAINT chk_commission_tiers_threshold CHECK (threshold_count >= 0),

    -- Exactly one of pct / flat (mirrors the commissions row snapshot).
    commission_pct      NUMERIC(5, 2)
        CONSTRAINT chk_commission_tiers_pct CHECK (commission_pct IS NULL OR (commission_pct >= 0 AND commission_pct <= 100)),
    flat_amount         NUMERIC(10, 2)
        CONSTRAINT chk_commission_tiers_flat CHECK (flat_amount IS NULL OR flat_amount >= 0),

    period_strategy     VARCHAR(20)  NOT NULL
        CONSTRAINT chk_commission_tiers_period
            CHECK (period_strategy IN ('DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY', 'QUARTERLY', 'SEMIANNUAL', 'ANNUAL')),

    applies_to          VARCHAR(20)  NOT NULL
        CONSTRAINT chk_commission_tiers_applies_to
            CHECK (applies_to IN ('INSCRIPTION', 'MONTHLY', 'BOTH')),

    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,
    status              VARCHAR(50),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by          UUID,
    updated_by          UUID,

    CONSTRAINT chk_commission_tiers_pct_xor_flat
        CHECK ((commission_pct IS NOT NULL) <> (flat_amount IS NOT NULL))
);

CREATE TRIGGER trg_commission_tiers_updated_at
    BEFORE UPDATE ON commission_tiers
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Engine lookup: candidate tiers for a (plan_type, applies_to), highest tier first.
CREATE INDEX idx_commission_tiers_lookup
    ON commission_tiers (plan_type, applies_to, threshold_count DESC) WHERE is_active;

-- Seed the v1 hardcoded rates as base tiers (threshold 0, whole-plan scope) so the
-- cutover to DB-driven config is behavior-preserving; admins layer volume tiers on top.
INSERT INTO commission_tiers (uuid, name, plan_type, threshold_count, commission_pct, flat_amount, period_strategy, applies_to)
VALUES
    (gen_random_uuid(), 'Individual base 20%',   'INDIVIDUAL',  0, 20.00, NULL, 'MONTHLY', 'BOTH'),
    (gen_random_uuid(), 'Familiar base 25%',     'FAMILIAR',    0, 25.00, NULL, 'MONTHLY', 'BOTH'),
    (gen_random_uuid(), 'Corporativo base $5',   'CORPORATIVO', 0, NULL,  5.00, 'MONTHLY', 'BOTH');

-- Close the deferred FK reserved in V26 (commission_tier_id was a bare column).
ALTER TABLE commissions
    ADD CONSTRAINT fk_commissions_tier
        FOREIGN KEY (commission_tier_id) REFERENCES commission_tiers (commission_tiers_id);


-- ─── 2. leaderboard_prizes (config: prize per rank per period strategy) ───────
CREATE TABLE leaderboard_prizes
(
    leaderboard_prizes_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                  UUID         NOT NULL UNIQUE,

    rank                  INT          NOT NULL
        CONSTRAINT chk_leaderboard_prizes_rank CHECK (rank >= 1),
    period_strategy       VARCHAR(20)  NOT NULL
        CONSTRAINT chk_leaderboard_prizes_period
            CHECK (period_strategy IN ('DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY', 'QUARTERLY', 'SEMIANNUAL', 'ANNUAL')),
    prize_amount          NUMERIC(10, 2) NOT NULL
        CONSTRAINT chk_leaderboard_prizes_amount CHECK (prize_amount >= 0),
    prize_currency        VARCHAR(3)   NOT NULL DEFAULT 'USD',

    is_active             BOOLEAN      NOT NULL DEFAULT TRUE,
    status                VARCHAR(50),
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by            UUID,
    updated_by            UUID
);

CREATE TRIGGER trg_leaderboard_prizes_updated_at
    BEFORE UPDATE ON leaderboard_prizes
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- One active prize per (rank, strategy).
CREATE UNIQUE INDEX uq_leaderboard_prizes_rank_strategy
    ON leaderboard_prizes (rank, period_strategy) WHERE is_active;


-- ─── 3. leaderboard_prize_awards (ledger — idempotent per period close) ──────
CREATE TABLE leaderboard_prize_awards
(
    leaderboard_prize_awards_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                        UUID         NOT NULL UNIQUE,

    promoter_id                 BIGINT       NOT NULL REFERENCES promoters (promoters_id),
    period_strategy             VARCHAR(20)  NOT NULL,
    period_start                DATE         NOT NULL,
    period_end                  DATE         NOT NULL,
    rank                        INT          NOT NULL
        CONSTRAINT chk_leaderboard_prize_awards_rank CHECK (rank >= 1),

    -- The commission total that ranked the promoter (snapshot for audit).
    metric_amount               NUMERIC(12, 2) NOT NULL,
    prize_amount                NUMERIC(10, 2) NOT NULL
        CONSTRAINT chk_leaderboard_prize_awards_amount CHECK (prize_amount >= 0),
    prize_currency              VARCHAR(3)   NOT NULL DEFAULT 'USD',
    awarded_at                  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    is_active                   BOOLEAN      NOT NULL DEFAULT TRUE,
    status                      VARCHAR(50),
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by                  UUID,
    updated_by                  UUID,

    CONSTRAINT chk_leaderboard_prize_awards_period CHECK (period_end >= period_start)
);

CREATE TRIGGER trg_leaderboard_prize_awards_updated_at
    BEFORE UPDATE ON leaderboard_prize_awards
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Idempotency: one award per promoter+period+rank; re-runs at close are no-ops.
CREATE UNIQUE INDEX uq_leaderboard_prize_awards
    ON leaderboard_prize_awards (promoter_id, period_strategy, period_start, period_end, rank) WHERE is_active;
CREATE INDEX idx_leaderboard_prize_awards_promoter
    ON leaderboard_prize_awards (promoter_id, created_at DESC);


-- ─── 4. commission_period_summary VIEW (base for the leaderboard) ────────────
-- Per (promoter, period) aggregate over non-voided commissions. Each commission
-- row already carries its own period (from the matched tier's strategy), so the
-- grouping is exactly the tier grain the leaderboard ranks by.
CREATE VIEW commission_period_summary AS
SELECT c.promoter_id,
       c.period_strategy,
       c.period_start,
       c.period_end,
       COUNT(*)        AS commission_count,
       SUM(c.amount)   AS total_amount,
       MIN(c.currency) AS currency
FROM commissions c
WHERE c.is_active = TRUE
  AND c.status <> 'VOIDED'
GROUP BY c.promoter_id, c.period_strategy, c.period_start, c.period_end;


-- ─── 5. Permissions (COMMISSIONS domain) ─────────────────────────────────────
-- The V30 trigger auto-grants each to SYSTEM on insert.
INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('COMMISSION_TIER_MANAGE',   'COMMISSIONS', 'Configurar los tramos (tiers) de comisión'),
    ('LEADERBOARD_VIEW',         'COMMISSIONS', 'Ver el ranking de promotores'),
    ('LEADERBOARD_PRIZE_MANAGE', 'COMMISSIONS', 'Configurar y otorgar premios del ranking')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name IN ('SYSTEM', 'ADMINISTRADOR')
  AND p.name IN ('COMMISSION_TIER_MANAGE', 'LEADERBOARD_VIEW', 'LEADERBOARD_PRIZE_MANAGE')
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- ─── 6. Scheduled job: award leaderboard prizes at period close ──────────────
INSERT INTO scheduled_jobs (code, display_name, description, cron_expression, timezone)
VALUES ('LEADERBOARD_PRIZE_AWARD', 'Premios de ranking',
        'Otorga premios al top del ranking de promotores al cierre del período',
        '0 0 5 1 * *', 'America/Caracas');


-- ─── 7. Fail loudly rather than migrate into a half-applied state ────────────
DO $$
BEGIN
    IF (SELECT COUNT(*) FROM commission_tiers) < 3 THEN
        RAISE EXCEPTION 'V42: commission_tiers base seed did not land';
    END IF;

    IF EXISTS (
        SELECT 1 FROM (VALUES ('COMMISSION_TIER_MANAGE'), ('LEADERBOARD_VIEW'), ('LEADERBOARD_PRIZE_MANAGE')) AS want(name)
        WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.name = want.name)
    ) THEN
        RAISE EXCEPTION 'V42: one or more COMMISSIONS permissions were not created';
    END IF;

    IF EXISTS (
        SELECT 1 FROM permissions p
        WHERE p.name IN ('COMMISSION_TIER_MANAGE', 'LEADERBOARD_VIEW', 'LEADERBOARD_PRIZE_MANAGE')
          AND NOT EXISTS (
            SELECT 1 FROM role_permissions rp
                     JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'SYSTEM'
            WHERE rp.permission_id = p.permissions_id)
    ) THEN
        RAISE EXCEPTION 'V42: SYSTEM did not receive the new permissions (V30 trigger?)';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM scheduled_jobs WHERE code = 'LEADERBOARD_PRIZE_AWARD') THEN
        RAISE EXCEPTION 'V42: LEADERBOARD_PRIZE_AWARD job seed did not land';
    END IF;
END $$;
