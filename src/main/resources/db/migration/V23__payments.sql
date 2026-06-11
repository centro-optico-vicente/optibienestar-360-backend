SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V23: payments — manual payment workflow.
--
-- Per ADR 0008, payments in this platform are MANUAL: the affiliate (or an
-- operator on their behalf) registers a payment with a proof of payment
-- (PDF / image), an admin reviews it, and the admin approves or rejects
-- the record. This table captures the full workflow:
--
--   PENDING → APPROVED   (admin confirms; status driven by the review step)
--           → REJECTED   (admin rejects, must include reason)
--
-- The actual money movement happens outside the platform (bank transfer,
-- Zelle, cash at counter, Pago Móvil VE, etc.). The platform only audits
-- the registration + review + approval.
--
-- Allocation:
--   - inscription = TRUE   → one-time inscription fee (member's first
--                            enrollment, or a beneficiary extra fee per
--                            V18 extra_inscription_paid).
--   - inscription = FALSE  → recurring monthly fee; `applied_period` is
--                            the first day of the month the payment
--                            unblocks (e.g. 2026-06-01 covers June 2026).
--
-- The reviewed_by FK uses BIGINT (matches V11 ally_services.reviewed_by) so
-- joins to users for the admin panel are cheap. The audit/created_by stays
-- UUID per BaseEntity pattern.
--
-- At the end: wires the deferred FK from V18
-- (beneficiaries.inscription_payment_id → payments.payments_id) that was
-- left open because beneficiaries was created before payments.
-- ────────────────────────────────────────────────────────────────────────────


CREATE TABLE payments
(
    payments_id                BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                       UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    -- Subject of the payment
    membership_id              BIGINT       NOT NULL REFERENCES memberships (memberships_id),

    -- Who paid. Typically the member's user account; can differ for
    -- corporate-contract payments (v2) or when a relative pays for the
    -- member. NULL when paid in cash by a non-user at the counter.
    payer_user_id              BIGINT       REFERENCES users (users_id),

    -- Money
    amount                     NUMERIC(10, 2) NOT NULL CHECK (amount > 0),
    -- ISO 4217 currency code. USD is the program's base currency per the
    -- flyer pricing; VES (Bolívares) accepted for VE customers — admin
    -- records the locally-paid amount + currency, the equivalence to USD
    -- stays an out-of-band conversion until v2 introduces FX rates.
    currency                   VARCHAR(3)   NOT NULL DEFAULT 'USD',

    -- How the customer paid. CHECK ENUM (VARCHAR + CHECK rather than PG
    -- ENUM, same convention as plans.type + membership.status — easier to
    -- evolve via migration).
    payment_method             VARCHAR(30)  NOT NULL CHECK (payment_method IN (
        'BANK_TRANSFER',           -- transferencia bancaria local
        'CASH',                    -- efectivo en taquilla
        'ZELLE',                   -- Zelle (USD)
        'PAGO_MOVIL',              -- pago móvil VE
        'CRYPTO',                  -- USDT / Binance / similar
        'INTERNATIONAL_TRANSFER',  -- SWIFT / Wise / etc.
        'OTHER'
    )),
    -- Bank reference number, Zelle confirmation code, etc.
    reference_number           VARCHAR(80),

    -- Calendar date the customer paid (their bank settlement) vs. when
    -- the system recorded it (received_at).
    payment_date               DATE         NOT NULL,
    received_at                TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    -- Allocation (see header comment)
    inscription                BOOLEAN      NOT NULL DEFAULT FALSE,
    -- First day of the covered month for recurring payments. NULL for
    -- inscription fees and for unallocated proposals before review.
    applied_period             DATE,

    -- Proof of payment uploaded by the member, stored in Cloudflare R2
    -- (S3-compatible, ADR 0007). The url is the object key; the controller
    -- generates presigned URLs on demand.
    support_file_url           VARCHAR(500),
    support_file_name          VARCHAR(255),
    support_file_content_type  VARCHAR(100),
    support_file_size_bytes    BIGINT,

    -- Free-form notes from the admin (visible only to admin, e.g.
    -- "matched against statement on 2026-06-12").
    admin_notes                TEXT,

    -- Review workflow — admin annotates the row
    reviewed_by                BIGINT       REFERENCES users (users_id),
    reviewed_at                TIMESTAMPTZ,
    -- Required when status = 'REJECTED' (CHECK below); also used for
    -- free-form approval notes if the admin wants to record a justification.
    review_reason              TEXT,

    -- Audit + workflow status. `status` is the BaseEntity-style column;
    -- pinned by the CHECK below to the 3 valid payment states.
    is_active                  BOOLEAN      NOT NULL DEFAULT TRUE,
    status                     VARCHAR(50)  NOT NULL DEFAULT 'PENDING'
                                  CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    created_at                 TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                 TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by                 UUID,
    updated_by                 UUID,

    -- Coherence: the review fields must travel together with the status
    CONSTRAINT chk_payments_review_consistency CHECK (
        (status = 'PENDING'
            AND reviewed_at IS NULL
            AND reviewed_by IS NULL)
        OR
        (status IN ('APPROVED', 'REJECTED')
            AND reviewed_at IS NOT NULL
            AND reviewed_by IS NOT NULL)
    ),
    -- Rejections must always come with a reason — the affiliate needs to
    -- know why so they can re-submit.
    CONSTRAINT chk_payments_rejection_has_reason CHECK (
        status <> 'REJECTED' OR review_reason IS NOT NULL
    ),
    -- payment_date can't be in the far future (more than 1 day out is
    -- almost certainly a typo).
    CONSTRAINT chk_payments_date_not_future CHECK (
        payment_date <= CURRENT_DATE + INTERVAL '1 day'
    ),
    -- Inscription rows shouldn't carry an applied_period (it's a one-time
    -- fee, not aligned to a billing month).
    CONSTRAINT chk_payments_inscription_no_period CHECK (
        NOT inscription OR applied_period IS NULL
    )
);


-- Pending-queue scan: admin opens "payments to review" → this is the
-- partial index that powers it. Most queries also order by received_at
-- desc, so include it for the index-only scan.
CREATE INDEX idx_payments_status_received
    ON payments (status, received_at DESC)
    WHERE is_active;

-- Per-membership payment history (admin views member detail → see all
-- payments newest first).
CREATE INDEX idx_payments_membership_date
    ON payments (membership_id, payment_date DESC);

-- Reports — payments approved within a calendar period
CREATE INDEX idx_payments_approved_period
    ON payments (applied_period)
    WHERE is_active AND status = 'APPROVED';

CREATE TRIGGER trg_payments_updated_at
    BEFORE UPDATE ON payments
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ─── Deferred FK from V18 ──────────────────────────────────────────────────
--
-- beneficiaries.inscription_payment_id was created in V18 as a bare BIGINT
-- (no FK) because the payments table did not exist yet. Now that V23 ships
-- payments, wire the constraint up so the relationship is enforced at the
-- database boundary.
--
-- ON DELETE: NO ACTION (default). Soft-delete is the canonical removal
-- pattern; deleting a payment row referenced by a beneficiary is a no-op
-- in normal operation, and a hard delete would orphan the beneficiary's
-- inscription history.
ALTER TABLE beneficiaries
    ADD CONSTRAINT fk_beneficiaries_inscription_payment
    FOREIGN KEY (inscription_payment_id) REFERENCES payments (payments_id);
