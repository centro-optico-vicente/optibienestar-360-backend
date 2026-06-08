SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V13: plans — subscription product catalog.
--
-- The flyer (OPTIBIENESTAR 360, jun 2026) defines three commercial SKUs:
--     Individual    $10 inscription / $5 monthly
--     Familiar      $20 inscription / $5 monthly + included beneficiaries
--     Corporativo   $5/persona — pricing model TBD with client
-- Plus a per-affiliate extra inscription fee of $5 for beneficiaries beyond
-- the plan's included cap (handled here via extra_beneficiary_inscription_fee).
--
-- All v2 fields baked in from day 1 to avoid ALTER chains later — see
-- vertical-5 / scope-additions-v2.md for the decision trail:
--   - type (INDIVIDUAL / FAMILIAR / CORPORATIVO)
--   - included_beneficiaries / max_beneficiaries / extra_fee
--   - is_published + published_at (publishing model identical to allies
--     and ally_services in V11 — admin controls site visibility orthogonally
--     from is_active)
--
-- The actual SKU seeds (Individual / Familiar / Corporativo with their
-- prices) land in V14__seed_plans.sql; this migration only defines the
-- shape. Memberships (V20, planned) reference plans_id; the corporate
-- contracts table (planned v2 addition) carries the plan_id of corporate
-- subscribers.
-- ────────────────────────────────────────────────────────────────────────────

CREATE TABLE plans
(
    plans_id                          BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                              UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    -- Natural identity. `code` is stable across renames (services key off it
    -- for hardcoded lookups like "the Individual plan"); `name` is the
    -- display label which the admin can rename without breaking integrations.
    code                              VARCHAR(40)  NOT NULL UNIQUE,
    name                              VARCHAR(100) NOT NULL,
    description                       TEXT,

    -- Plan type — three families per the flyer. CHECK matches the v2 ENUM
    -- documented in scope-additions-v2.md.
    type                              VARCHAR(20)  NOT NULL
                                          CHECK (type IN ('INDIVIDUAL', 'FAMILIAR', 'CORPORATIVO')),

    -- Pricing in USD. NUMERIC(10,2) covers values up to 99 999 999.99 — way
    -- more than needed but matches the convention from ally_services.
    inscription_fee                   NUMERIC(10, 2) NOT NULL CHECK (inscription_fee   >= 0),
    monthly_fee                       NUMERIC(10, 2) NOT NULL CHECK (monthly_fee       >= 0),

    -- Beneficiaries (v2 fields).
    --   included_beneficiaries: how many extra family members are covered
    --     without extra cost. 0 for INDIVIDUAL, ~3 for FAMILIAR, 0 for
    --     CORPORATIVO (each person on a corporate contract is a member, not
    --     a beneficiary).
    --   max_beneficiaries: hard upper bound. NULL = unlimited (e.g.
    --     a future "FAMILIAR_PLUS" with no cap). CORPORATIVO does not use
    --     this field — it scales via the corporate_contracts table.
    --   extra_beneficiary_inscription_fee: one-time per beneficiary added
    --     beyond included_beneficiaries. NULL when the plan does not allow
    --     extra beneficiaries at all (e.g. INDIVIDUAL with max=0).
    included_beneficiaries            INT          NOT NULL DEFAULT 0 CHECK (included_beneficiaries >= 0),
    max_beneficiaries                 INT          CHECK (max_beneficiaries IS NULL OR max_beneficiaries >= included_beneficiaries),
    extra_beneficiary_inscription_fee NUMERIC(10, 2) CHECK (extra_beneficiary_inscription_fee IS NULL OR extra_beneficiary_inscription_fee >= 0),

    -- Lifecycle. After grace_period_days past due_date with no payment, the
    -- membership transitions ACTIVE → SUSPENDED. Configurable per plan so a
    -- premium plan can be more lenient.
    grace_period_days                 INT          NOT NULL DEFAULT 7 CHECK (grace_period_days >= 0),

    -- Publishing — same shape as allies / ally_services in V11. The admin
    -- can ready a plan in the DB without exposing it to the public landing
    -- yet; future scheduler can flip is_published when published_at arrives.
    -- Public filter on the directory: WHERE is_active AND is_published.
    is_published                      BOOLEAN      NOT NULL DEFAULT FALSE,
    published_at                      TIMESTAMPTZ,

    -- Audit + soft-delete (BaseEntity-style)
    is_active                         BOOLEAN      NOT NULL DEFAULT TRUE,
    status                            VARCHAR(50),
    created_at                        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by                        UUID,
    updated_by                        UUID
);

CREATE INDEX idx_plans_type      ON plans (type);
-- Public directory filter — partial keeps it small (only published rows).
CREATE INDEX idx_plans_published ON plans (is_active) WHERE is_active AND is_published;

CREATE TRIGGER trg_plans_updated_at
    BEFORE UPDATE ON plans
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
