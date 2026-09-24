SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V153: membership_charges — one billable month of a Membership.
--
-- The payment-driven half of the billing cycle MembershipStatusService's own
-- Javadoc deferred to "V22 payments": a row per covered month, generated
-- ahead of time by the daily MEMBERSHIP_CHARGE_GENERATION job (V155) and
-- settled (PENDING → COVERED) by MembershipChargeService.applyPayment when
-- an APPROVED payment covers it. A single payment can cover several
-- consecutive charges (multi-month advance) — the commission engine still
-- calculates against the payment's full amount, never fractioned per month.
-- ────────────────────────────────────────────────────────────────────────────

CREATE TABLE membership_charges
(
    membership_charges_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                   UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    membership_id          BIGINT       NOT NULL REFERENCES memberships (memberships_id),

    -- Covered month
    period_start           DATE         NOT NULL,   -- first day of the covered month
    period_end             DATE         NOT NULL,   -- last day of the covered month

    -- Scheduled collection date — same billing-cutover-day projection
    -- CommissionService.collectionDays uses (billing_start_day, clamped).
    due_date               DATE         NOT NULL,

    amount                 NUMERIC(10, 2) NOT NULL CHECK (amount >= 0),
    currency_id            BIGINT       NOT NULL REFERENCES currencies (currencies_id),

    covered_by_payment_id  BIGINT       REFERENCES payments (payments_id),

    -- Audit + lifecycle status (mirrors BaseEntity)
    is_active              BOOLEAN      NOT NULL DEFAULT TRUE,
    status                 VARCHAR(50)  NOT NULL DEFAULT 'PENDING'
                               CHECK (status IN ('PENDING', 'COVERED', 'OVERDUE', 'WAIVED')),
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by             UUID,
    updated_by             UUID,

    CONSTRAINT chk_membership_charges_period_end_after_start CHECK (period_end >= period_start)
);

-- One charge per (membership, covered month) — the idempotency guard behind
-- MembershipChargeService.ensureChargeForPeriod.
CREATE UNIQUE INDEX uniq_membership_charges_membership_period
    ON membership_charges (membership_id, period_start);

-- MEMBERSHIP_CHARGE_GENERATION / overdue-sweep query shape: filter by status,
-- order by due date.
CREATE INDEX idx_membership_charges_status_due_date
    ON membership_charges (status, due_date);

CREATE TRIGGER trg_membership_charges_updated_at
    BEFORE UPDATE ON membership_charges
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
