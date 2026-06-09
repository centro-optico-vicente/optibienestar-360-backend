SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V21: memberships — concrete subscriptions of a Member to a Plan.
--
-- This is the row payments are billed against. Lifecycle:
--
--     ACTIVE → SUSPENDED → EXPIRED            (driven by daily job)
--          ↘ CANCELED                          (admin action)
--
-- A member can have a history of past memberships (different plans over
-- time, or reactivations after a cancellation) but at most ONE row that is
-- currently in use — enforced by the partial unique index below on
-- (member_id) WHERE is_active = TRUE.
--
-- Pricing is snapshotted at enrollment. If the parent plan changes its
-- inscription_fee / monthly_fee / grace_period_days later, the active
-- membership keeps the values that were in effect when it was created.
-- Renegotiating means cancelling the old row and enrolling a new one — the
-- audit trail stays clean.
--
-- Number bump note: this migration was originally planned as V20 in
-- vertical-5 + 02-database.md. V20 was taken by V20__bcrypt_helper.sql
-- (utility function for seed passwords); memberships moves down to V21 and
-- the rest of the planned chain bumps +1.
-- ────────────────────────────────────────────────────────────────────────────

CREATE TABLE memberships
(
    memberships_id            BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                      UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    -- Core links
    member_id                 BIGINT       NOT NULL REFERENCES members (members_id),
    plan_id                   BIGINT       NOT NULL REFERENCES plans (plans_id),

    -- Lifecycle dates
    enrolled_at               DATE         NOT NULL DEFAULT CURRENT_DATE,
    expires_at                DATE,         -- NULL = open-ended (recurring monthly)

    -- Payment tracking
    -- next_due_date drives the daily status job: ACTIVE rows whose
    -- next_due_date has passed move to SUSPENDED; SUSPENDED rows whose
    -- next_due_date + grace_period_days has passed move to EXPIRED.
    next_due_date             DATE         NOT NULL,
    -- The last calendar date covered by an applied payment. NULL until the
    -- first payment is recorded.
    last_paid_through         DATE,

    -- Pricing snapshot from plans at enrollment (immune to later plan edits)
    inscription_fee           NUMERIC(10, 2) NOT NULL CHECK (inscription_fee   >= 0),
    monthly_fee               NUMERIC(10, 2) NOT NULL CHECK (monthly_fee       >= 0),
    grace_period_days         INT            NOT NULL CHECK (grace_period_days >= 0),

    -- Status-change auditing (last transition only; full history can live in
    -- a future membership_status_log if needed for compliance / reports).
    last_status_change_at     TIMESTAMPTZ,
    last_status_change_reason TEXT,

    -- Audit + lifecycle status. status uses the BaseEntity column with a
    -- CHECK that pins it to the four valid lifecycle values; the soft-delete
    -- flag is is_active.
    is_active                 BOOLEAN     NOT NULL DEFAULT TRUE,
    status                    VARCHAR(50) NOT NULL DEFAULT 'ACTIVE'
                                  CHECK (status IN ('ACTIVE', 'SUSPENDED', 'EXPIRED', 'CANCELED')),
    created_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by                UUID,
    updated_by                UUID,

    -- Date coherence
    CONSTRAINT chk_memberships_expires_after_enrolled CHECK (
        expires_at IS NULL OR expires_at >= enrolled_at
    ),
    CONSTRAINT chk_memberships_paid_through_after_enrolled CHECK (
        last_paid_through IS NULL OR last_paid_through >= enrolled_at
    ),
    CONSTRAINT chk_memberships_next_due_after_enrolled CHECK (
        next_due_date >= enrolled_at
    )
);

-- A member has at most one currently-active membership row. The partial
-- UNIQUE allows the historical trail (canceled / soft-deleted past
-- subscriptions stay around as is_active = FALSE) and the readmission flow
-- ("cancel the old + insert the new" within one transaction; service
-- ensures the deactivation lands before the insert).
CREATE UNIQUE INDEX uniq_memberships_one_active_per_member
    ON memberships (member_id)
    WHERE is_active = TRUE;

-- Daily status job: scan ACTIVE rows past next_due_date → SUSPENDED, then
-- SUSPENDED rows past next_due_date + grace → EXPIRED. Composite on
-- (status, next_due_date) covers both queries cheaply.
CREATE INDEX idx_memberships_status_next_due
    ON memberships (status, next_due_date)
    WHERE is_active = TRUE;

-- Per-member history lookup (admin views all the member's past
-- subscriptions on the affiliate detail page).
CREATE INDEX idx_memberships_member ON memberships (member_id);

CREATE TRIGGER trg_memberships_updated_at
    BEFORE UPDATE ON memberships
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
