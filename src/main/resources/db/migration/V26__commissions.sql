SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V26: commissions — earnings the platform owes a promoter for an
-- enrollment payment.
--
-- One row per payment-driven commission event. The commission engine
-- (CommissionService, future bullet) creates a row when a payment is
-- approved + the resolved promoter qualifies for a tier (per v2 PDF #5
-- commission_tiers). The row carries the tier snapshot inline so later
-- edits to commission_tiers do NOT retroactively change historical
-- commissions.
--
-- Lifecycle:
--
--   PENDING → PAID      (admin closes a period and disburses the payout)
--           → VOIDED    (underlying payment refunded / fraud reversed)
--           → DISPUTED  (promoter contests; under review)
--
-- Period model:
--   The commission row stores period_start + period_end (calendar dates
--   inclusive). The v2 commission_tiers can drive different cadences
--   (DAILY / WEEKLY / BIWEEKLY / MONTHLY / QUARTERLY / SEMIANNUAL /
--   ANNUAL); period_strategy is snapshotted here so admin reports stay
--   self-explanatory even if the tier is later edited.
--
-- Member.promoter_id resolution:
--   Per v2 PDF 2.a, member.promoter_id is a permanent link. This row
--   captures the resolved promoter at the moment of payment approval,
--   so the late-assignment endpoint (POST /v1/admin/members/{uuid}/
--   assign-promoter) does NOT retroactively rewrite existing
--   commissions — only future payments attribute to the new promoter.
-- ────────────────────────────────────────────────────────────────────────────


CREATE TABLE commissions
(
    commissions_id     BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid               UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    -- Earner + originator
    promoter_id        BIGINT       NOT NULL REFERENCES promoters (promoters_id),
    -- The payment that triggered this commission. Each payment normally
    -- yields at most one commission per promoter (the highest qualifying
    -- tier per v2 PDF #5). The partial UNIQUE below enforces this.
    payment_id         BIGINT       NOT NULL REFERENCES payments (payments_id),
    -- Convenience FK — denormalized from payment.membership.member but
    -- kept here so the "all commissions of member X" query doesn't need
    -- a 2-level JOIN.
    member_id          BIGINT       NOT NULL REFERENCES members (members_id),

    -- Money
    amount             NUMERIC(10, 2) NOT NULL CHECK (amount > 0),
    currency           VARCHAR(3)   NOT NULL DEFAULT 'USD',

    -- Calculation snapshot (NOT live references). The tier row may be
    -- later edited; this commission keeps the values that were in effect
    -- at the moment of calculation.
    -- The basis is the payment amount the commission was computed from;
    -- pct OR flat (one of them populated, never both — CHECK below).
    calculation_basis  NUMERIC(10, 2) NOT NULL CHECK (calculation_basis >= 0),
    commission_pct     NUMERIC(5, 2)  CHECK (commission_pct IS NULL OR (commission_pct >= 0 AND commission_pct <= 100)),
    flat_amount        NUMERIC(10, 2) CHECK (flat_amount IS NULL OR flat_amount >= 0),

    -- Snapshot of the matched tier (NULL when no tier table existed yet —
    -- v1 calculations may use a hardcoded default rate before
    -- commission_tiers ships).
    commission_tier_id BIGINT,  -- FK added when commission_tiers table lands
    tier_name_snapshot VARCHAR(80),

    -- What kind of payment generated this. Matches v2 commission_tiers
    -- applies_to enum but captured per-row so reports stay legible.
    applies_to         VARCHAR(20)  NOT NULL
                          CHECK (applies_to IN ('INSCRIPTION', 'MONTHLY')),

    -- Period the commission falls in. period_strategy is the cadence used
    -- to compute the bounds (snapshot — admin can later change the tier's
    -- strategy without rewriting history).
    period_strategy    VARCHAR(20)  NOT NULL DEFAULT 'MONTHLY'
                          CHECK (period_strategy IN (
                              'DAILY', 'WEEKLY', 'BIWEEKLY',
                              'MONTHLY', 'QUARTERLY', 'SEMIANNUAL', 'ANNUAL')),
    period_start       DATE         NOT NULL,
    period_end         DATE         NOT NULL,

    -- When the commission was actually earned (the payment's reviewed_at).
    earned_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    -- Payout tracking
    payout_reference   VARCHAR(120),  -- bank ref / batch id / Zelle confirmation
    paid_at            TIMESTAMPTZ,
    voided_at          TIMESTAMPTZ,
    void_reason        TEXT,

    -- Admin-only free-form
    admin_notes        TEXT,

    -- Audit + workflow status (BaseEntity-style)
    is_active          BOOLEAN      NOT NULL DEFAULT TRUE,
    status             VARCHAR(50)  NOT NULL DEFAULT 'PENDING'
                          CHECK (status IN ('PENDING', 'PAID', 'VOIDED', 'DISPUTED')),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by         UUID,
    updated_by         UUID,

    -- Calculation coherence: exactly one of pct or flat applied
    CONSTRAINT chk_commissions_pct_xor_flat CHECK (
        (commission_pct IS NOT NULL AND flat_amount IS NULL)
        OR
        (commission_pct IS NULL AND flat_amount IS NOT NULL)
    ),
    -- Period coherence
    CONSTRAINT chk_commissions_period_bounds CHECK (period_end >= period_start),
    -- earned_at must fall inside the period it's attributed to (anti drift
    -- between the commission engine's clock and the period bounds)
    CONSTRAINT chk_commissions_earned_in_period CHECK (
        earned_at::date BETWEEN period_start AND period_end
    ),
    -- Payout coherence: status=PAID ⇒ paid_at + payout_reference required;
    --                   status=VOIDED ⇒ voided_at + void_reason required.
    CONSTRAINT chk_commissions_paid_consistency CHECK (
        status <> 'PAID' OR (paid_at IS NOT NULL AND payout_reference IS NOT NULL)
    ),
    CONSTRAINT chk_commissions_voided_consistency CHECK (
        status <> 'VOIDED' OR (voided_at IS NOT NULL AND void_reason IS NOT NULL)
    )
);


-- A given payment yields AT MOST ONE non-voided commission per promoter
-- (the highest qualifying tier). VOIDED rows stay around as audit history;
-- the partial UNIQUE allows re-creating a fresh commission after a void.
CREATE UNIQUE INDEX uniq_commissions_payment_promoter_active
    ON commissions (payment_id, promoter_id)
    WHERE status <> 'VOIDED';

-- Liquidation per-promoter per-period (the canonical "pay this promoter
-- for last month" query). Partial on is_active to keep the scan small.
CREATE INDEX idx_commissions_promoter_status_period
    ON commissions (promoter_id, status, period_start)
    WHERE is_active;

-- Per-member commission history (admin opens member detail → "who's
-- being paid for keeping this affiliate active").
CREATE INDEX idx_commissions_member_earned
    ON commissions (member_id, earned_at DESC)
    WHERE is_active;

-- Audit lookup by payment (refunding a payment fans out to voiding the
-- commission rows tied to it).
CREATE INDEX idx_commissions_payment_id
    ON commissions (payment_id);

CREATE TRIGGER trg_commissions_updated_at
    BEFORE UPDATE ON commissions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
