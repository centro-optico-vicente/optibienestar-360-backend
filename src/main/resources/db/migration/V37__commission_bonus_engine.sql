SET search_path TO app, public;

-- V37: the automated bonus/awards engine (v2 PDF #5 "Motor Automatizado de
-- Comisiones y Premiaciones" — the Premiaciones side).
--
-- This is distinct from the per-payment commission ledger (V26): commissions
-- pay a promoter a cut of each approved payment; BONUSES reward a promoter for
-- crossing a configurable SUBSCRIBER-COUNT goal in a window. The three shapes
-- the business asked for all fall out of the same two knobs:
--
--   metric × accrual × window × reward
--
--   * "every 500 new subscribers over time → $100"      NEW / PER_BLOCK / LIFETIME / FLAT 100
--   * "300 active subscribers in a month → $50"          ACTIVE / THRESHOLD / MONTHLY / FLAT 50
--   * "end-of-month campaign, per 50 new → $200"          NEW / PER_BLOCK / CAMPAIGN / FLAT 200
--
-- Amounts/percentages and counts are configurable; rules are combinable (many
-- active at once) and several promoters may each win several awards per period.
--
-- Two tables + three permissions + a monthly scheduled-job seed ship here,
-- backing the engine + admin CRUD + promoter self-service in the same PR.


-- ─── 1. Bonus rules (configurable goals) ─────────────────────────────────────
-- BaseEntity-shaped (id/uuid/is_active/status/audit) so the JPA entity validates
-- clean. is_active doubles as the enable/disable switch the evaluator filters on.
CREATE TABLE commission_bonus_rules
(
    commission_bonus_rules_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                      UUID         NOT NULL UNIQUE,

    name                      VARCHAR(150) NOT NULL,
    description               TEXT,

    -- What we count per promoter in the window.
    metric                    VARCHAR(30)  NOT NULL
        CONSTRAINT chk_bonus_rule_metric CHECK (metric IN ('NEW_SUBSCRIBERS', 'ACTIVE_SUBSCRIBERS')),

    -- How the count turns into award units:
    --   PER_BLOCK  → one award unit per full block of threshold_count (repeats:
    --                523 new @ 500 → 1 block; 1000 → 2 blocks).
    --   THRESHOLD  → a single award once the count reaches threshold_count.
    accrual                   VARCHAR(20)  NOT NULL
        CONSTRAINT chk_bonus_rule_accrual CHECK (accrual IN ('PER_BLOCK', 'THRESHOLD')),

    -- The configurable quantity (500 / 300 / 50). Strictly positive.
    threshold_count           INT          NOT NULL
        CONSTRAINT chk_bonus_rule_threshold_positive CHECK (threshold_count > 0),

    -- Evaluation window. LIFETIME = cumulative all-time (per-block milestones);
    -- the calendar strategies reset each period; CAMPAIGN = a fixed date range.
    window_strategy           VARCHAR(20)  NOT NULL
        CONSTRAINT chk_bonus_rule_window CHECK (window_strategy IN
            ('LIFETIME', 'DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY',
             'QUARTERLY', 'SEMIANNUAL', 'ANNUAL', 'CAMPAIGN')),

    -- Only set for CAMPAIGN windows (fixed limited-time promos).
    campaign_start            DATE,
    campaign_end              DATE,

    -- Reward: exactly one of flat_amount / reward_pct (money OR percentage).
    reward_type               VARCHAR(20)  NOT NULL
        CONSTRAINT chk_bonus_rule_reward_type CHECK (reward_type IN ('FLAT', 'PERCENTAGE')),
    flat_amount               NUMERIC(10,2),
    reward_pct                NUMERIC(5,2),
    reward_currency           VARCHAR(3)   NOT NULL DEFAULT 'USD',

    -- Whether the system promoter (INSTITUCION) participates. Bonuses are for
    -- humans by default; internal financial reporting may flip this per rule.
    include_system_promoters  BOOLEAN      NOT NULL DEFAULT FALSE,

    is_active                 BOOLEAN      NOT NULL DEFAULT TRUE,
    status                    VARCHAR(50),
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by                UUID,
    updated_by                UUID,

    -- Reward coherence: FLAT ⇔ flat_amount set & pct null; PERCENTAGE ⇔ pct set
    -- & flat null. reward_pct capped at 100 (a percentage of window earnings).
    CONSTRAINT chk_bonus_rule_reward_coherence CHECK (
        (reward_type = 'FLAT'       AND flat_amount IS NOT NULL AND flat_amount > 0 AND reward_pct IS NULL) OR
        (reward_type = 'PERCENTAGE' AND reward_pct  IS NOT NULL AND reward_pct  > 0 AND reward_pct <= 100 AND flat_amount IS NULL)
    ),

    -- CAMPAIGN ⇔ both campaign dates set (and ordered); any other window ⇒ both null.
    CONSTRAINT chk_bonus_rule_campaign_dates CHECK (
        (window_strategy = 'CAMPAIGN' AND campaign_start IS NOT NULL AND campaign_end IS NOT NULL AND campaign_end >= campaign_start) OR
        (window_strategy <> 'CAMPAIGN' AND campaign_start IS NULL AND campaign_end IS NULL)
    )
);

CREATE TRIGGER trg_commission_bonus_rules_updated_at
    BEFORE UPDATE ON commission_bonus_rules
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- The evaluator scans every enabled rule each run.
CREATE INDEX idx_commission_bonus_rules_active
    ON commission_bonus_rules (created_at DESC) WHERE is_active;


-- ─── 2. Bonus awards (the ledger) ────────────────────────────────────────────
-- One row per (rule, promoter, window) grant. For PER_BLOCK the row records how
-- many blocks it granted; a later evaluation of the same window/lifetime only
-- grants the positive delta, so re-runs are idempotent by construction.
CREATE TABLE promoter_bonus_awards
(
    promoter_bonus_awards_id  BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                      UUID         NOT NULL UNIQUE,

    bonus_rule_id             BIGINT       NOT NULL REFERENCES commission_bonus_rules (commission_bonus_rules_id),
    promoter_id               BIGINT       NOT NULL REFERENCES promoters (promoters_id),

    -- The window this grant belongs to. For LIFETIME the start is a sentinel
    -- epoch and the end is the evaluation date; dedup for LIFETIME is by the
    -- cumulative sum of blocks_awarded, not by window equality.
    window_start              DATE         NOT NULL,
    window_end                DATE         NOT NULL,

    -- PER_BLOCK: number of newly-crossed blocks this row grants (≥1).
    -- THRESHOLD: always 1.
    blocks_awarded            INT          NOT NULL
        CONSTRAINT chk_bonus_award_blocks_positive CHECK (blocks_awarded > 0),

    -- The subscriber count observed at evaluation (audit: "you had 523 new").
    metric_count              INT          NOT NULL,

    -- Reward snapshot — legible + stable even if the rule is later edited.
    reward_type               VARCHAR(20)  NOT NULL
        CONSTRAINT chk_bonus_award_reward_type CHECK (reward_type IN ('FLAT', 'PERCENTAGE')),
    flat_amount               NUMERIC(10,2),
    reward_pct                NUMERIC(5,2),
    -- Basis the percentage was applied to (null for FLAT rewards).
    basis_amount              NUMERIC(12,2),
    amount                    NUMERIC(12,2) NOT NULL
        CONSTRAINT chk_bonus_award_amount_positive CHECK (amount > 0),
    reward_currency           VARCHAR(3)   NOT NULL DEFAULT 'USD',
    rule_name_snapshot        VARCHAR(150) NOT NULL,

    evaluated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),

    -- Payout lifecycle mirrors commissions (PENDING → PAID / VOIDED).
    payout_reference          VARCHAR(120),
    paid_at                   TIMESTAMPTZ,
    voided_at                 TIMESTAMPTZ,
    void_reason               TEXT,
    admin_notes               TEXT,

    is_active                 BOOLEAN      NOT NULL DEFAULT TRUE,
    status                    VARCHAR(50)  NOT NULL DEFAULT 'PENDING'
        CONSTRAINT chk_bonus_award_status CHECK (status IN ('PENDING', 'PAID', 'VOIDED')),
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by                UUID,
    updated_by                UUID,

    CONSTRAINT chk_bonus_award_paid     CHECK (status <> 'PAID'   OR (paid_at   IS NOT NULL AND payout_reference IS NOT NULL)),
    CONSTRAINT chk_bonus_award_voided   CHECK (status <> 'VOIDED' OR (voided_at IS NOT NULL AND void_reason      IS NOT NULL)),
    CONSTRAINT chk_bonus_award_reward   CHECK (
        (reward_type = 'FLAT'       AND flat_amount IS NOT NULL AND reward_pct IS NULL) OR
        (reward_type = 'PERCENTAGE' AND reward_pct  IS NOT NULL AND flat_amount IS NULL)
    )
);

CREATE TRIGGER trg_promoter_bonus_awards_updated_at
    BEFORE UPDATE ON promoter_bonus_awards
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Dedup / delta computation per rule+promoter, scoped by window for the
-- calendar/campaign strategies and cumulatively for LIFETIME.
CREATE INDEX idx_bonus_awards_dedup
    ON promoter_bonus_awards (bonus_rule_id, promoter_id, window_start, window_end)
    WHERE is_active;

-- Promoter self-service ("my bonuses", newest first) + admin per-promoter view.
CREATE INDEX idx_bonus_awards_promoter_created
    ON promoter_bonus_awards (promoter_id, created_at DESC) WHERE is_active;

-- Admin payout queue: unpaid awards.
CREATE INDEX idx_bonus_awards_status_created
    ON promoter_bonus_awards (status, created_at DESC) WHERE is_active;


-- ─── 3. Permissions ──────────────────────────────────────────────────────────
-- COMMISSIONS domain (same as COMMISSION_*). The V30 trigger auto-grants each to
-- SYSTEM on insert; the grants below add them to the business roles.
INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('BONUS_RULE_MANAGE',    'COMMISSIONS', 'Configurar reglas de bonos y disparar la evaluación'),
    ('BONUS_AWARD_VIEW_ALL', 'COMMISSIONS', 'Ver todos los premios/bonos otorgados a promotores'),
    ('BONUS_VIEW_OWN',       'COMMISSIONS', 'Ver los bonos propios del promotor')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code;


-- ADMINISTRADOR: configures rules + sees every award.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name = 'ADMINISTRADOR'
  AND p.name IN ('BONUS_RULE_MANAGE', 'BONUS_AWARD_VIEW_ALL', 'BONUS_VIEW_OWN')
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- PROMOTOR: self-service view of their own awards only.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name = 'PROMOTOR'
  AND p.name = 'BONUS_VIEW_OWN'
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- ─── 4. Scheduled job — monthly automatic evaluation ─────────────────────────
-- Runs on the 1st at 04:00 America/Caracas, after the daily status sweep (03:00),
-- so month-close active-subscriber counts are settled. Resolved to the
-- BonusEvaluationJobRunner bean by code.
INSERT INTO scheduled_jobs (code, display_name, description, cron_expression, timezone)
VALUES (
    'BONUS_EVALUATION',
    'Evaluación mensual de bonos de promotores',
    'Recorre las reglas de bono activas y otorga premios a los promotores que alcanzan las metas de suscriptores (nuevos o activos) del período. Idempotente: solo otorga los bloques nuevos no premiados aún.',
    '0 0 4 1 * *',
    'America/Caracas'
);


-- ─── 5. Fail loudly rather than migrate into a half-applied state ────────────
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM (VALUES ('BONUS_RULE_MANAGE'), ('BONUS_AWARD_VIEW_ALL'), ('BONUS_VIEW_OWN')) AS want(name)
        WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.name = want.name)
    ) THEN
        RAISE EXCEPTION 'V37: one or more BONUS_* permissions were not created';
    END IF;

    -- SYSTEM must hold all three (V30 trigger).
    IF EXISTS (
        SELECT 1 FROM permissions p
        WHERE p.name IN ('BONUS_RULE_MANAGE', 'BONUS_AWARD_VIEW_ALL', 'BONUS_VIEW_OWN')
          AND NOT EXISTS (
            SELECT 1 FROM role_permissions rp
                     JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'SYSTEM'
            WHERE rp.permission_id = p.permissions_id)
    ) THEN
        RAISE EXCEPTION 'V37: SYSTEM did not receive the new BONUS_* permissions (V30 trigger?)';
    END IF;

    -- ADMINISTRADOR must hold all three.
    IF EXISTS (
        SELECT 1 FROM permissions p
        WHERE p.name IN ('BONUS_RULE_MANAGE', 'BONUS_AWARD_VIEW_ALL', 'BONUS_VIEW_OWN')
          AND NOT EXISTS (
            SELECT 1 FROM role_permissions rp
                     JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'ADMINISTRADOR'
            WHERE rp.permission_id = p.permissions_id)
    ) THEN
        RAISE EXCEPTION 'V37: ADMINISTRADOR is missing one of the new BONUS_* permissions';
    END IF;

    -- PROMOTOR must hold BONUS_VIEW_OWN.
    IF NOT EXISTS (
        SELECT 1 FROM role_permissions rp
                 JOIN roles r      ON r.roles_id = rp.role_id AND r.name = 'PROMOTOR'
                 JOIN permissions p ON p.permissions_id = rp.permission_id AND p.name = 'BONUS_VIEW_OWN'
    ) THEN
        RAISE EXCEPTION 'V37: PROMOTOR did not receive BONUS_VIEW_OWN';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM scheduled_jobs WHERE code = 'BONUS_EVALUATION') THEN
        RAISE EXCEPTION 'V37: BONUS_EVALUATION scheduled job row was not seeded';
    END IF;
END $$;
