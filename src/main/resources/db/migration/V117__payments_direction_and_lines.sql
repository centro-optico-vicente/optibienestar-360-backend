SET search_path TO app, public;

-- ============================================================================
-- V117: payments becomes a real header (direction/person_id/promoter_id +
-- reason FK to payment_categories) and gains payment_lines, the second step
-- of the payments/payouts unification (hub plan
-- ".ai/plans/2026-09-17-payments-unification-plan.md", decisions #1-#3).
--
-- `payment_method`/`reference_number` move from the header down to
-- payment_lines (one header can now split across cash + transfer + any
-- combination, even though today every payment still produces exactly one
-- line). `payments.payment_type_id` becomes the REASON (FK payment_categories,
-- V115) — not to be confused with `payment_lines.payment_type_id`, which is
-- the METHOD (FK payment_methods, V115). Same column name in both tables on
-- purpose (continuity with the original single-catalog design) but pointing
-- at two different catalogs, each with its own referential integrity.
--
-- Every payment that exists today is a membership/inscription fee collected
-- from an affiliate, so the backfill is unambiguous:
--   - direction        = 'IN' for all existing rows.
--   - payment_type_id  = INSCRIPTION_FEE when inscription, else MEMBERSHIP_FEE.
--   - person_id        = the paying member's person_id (via membership_id).
--   - promoter_id      = the promoter already assigned to the paying member
--                         (members.promoter_id), when the member has one —
--                         direct/no-promoter members stay NULL.
--
-- v_report_commissions / v_report_payments (V97) select payment_method and
-- reference_number directly off payments — both columns move down to
-- payment_lines here, so both views are dropped and recreated against the
-- new single-line-per-payment shape at the end of this migration.
-- ============================================================================

-- ─── payments: new header columns ───────────────────────────────────────────

ALTER TABLE payments ADD COLUMN direction VARCHAR(10);
UPDATE payments SET direction = 'IN';
ALTER TABLE payments ALTER COLUMN direction SET NOT NULL;
ALTER TABLE payments ADD CONSTRAINT chk_payments_direction CHECK (direction IN ('IN', 'OUT'));

ALTER TABLE payments ADD COLUMN payment_type_id BIGINT REFERENCES payment_categories (payment_categories_id);
UPDATE payments p
SET payment_type_id = (
    SELECT pc.payment_categories_id
    FROM payment_categories pc
    WHERE pc.code = CASE WHEN p.inscription THEN 'INSCRIPTION_FEE' ELSE 'MEMBERSHIP_FEE' END
);
ALTER TABLE payments ALTER COLUMN payment_type_id SET NOT NULL;
CREATE INDEX idx_payments_payment_type ON payments (payment_type_id);

ALTER TABLE payments ADD COLUMN person_id BIGINT REFERENCES persons (persons_id);
UPDATE payments p
SET person_id = mem.person_id
FROM memberships m
         JOIN members mem ON mem.members_id = m.member_id
WHERE m.memberships_id = p.membership_id;
ALTER TABLE payments ALTER COLUMN person_id SET NOT NULL;
CREATE INDEX idx_payments_person ON payments (person_id);

-- Denormalized on purpose (see plan §"Diagrama de tablas") so "cobros de mi
-- red" / "mis pagos de comisiones" filter directly on promoter_id without a
-- multi-hop join. Nullable: direct/no-promoter members legitimately have none.
ALTER TABLE payments ADD COLUMN promoter_id BIGINT REFERENCES promoters (promoters_id);
UPDATE payments p
SET promoter_id = mem.promoter_id
FROM memberships m
         JOIN members mem ON mem.members_id = m.member_id
WHERE m.memberships_id = p.membership_id
  AND mem.promoter_id IS NOT NULL;
CREATE INDEX idx_payments_promoter ON payments (promoter_id);

-- Screen filters: admin "Movimientos" (direction + status), promoter
-- self-service ("cobros de mi red" / "mis pagos de comisiones").
CREATE INDEX idx_payments_direction_status ON payments (direction, status);

-- ─── payment_lines ───────────────────────────────────────────────────────────

CREATE TABLE payment_lines
(
    payment_line_id    BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    payment_id          BIGINT       NOT NULL REFERENCES payments (payments_id) ON DELETE CASCADE,

    -- METHOD (not reason) — FK payment_methods, V115.
    payment_type_id     BIGINT       NOT NULL REFERENCES payment_methods (payment_methods_id),

    -- Only populated when the chosen method has is_mandatory_bank_account=true
    -- (bank transfer, international transfer, check, bank deposit).
    bank_id              BIGINT       REFERENCES banks (banks_id),

    amount               NUMERIC(10, 2) NOT NULL CHECK (amount > 0),
    currency_id           BIGINT       NOT NULL REFERENCES currencies (currencies_id),

    reference_number      VARCHAR(80),

    -- Same state machine as payments.status — a line can be approved/rejected
    -- independently of its sibling lines (e.g. the transfer half clears before
    -- the cash half is counted). The header status becomes a derived summary
    -- at the application layer (APPROVED only once every line is APPROVED).
    status                VARCHAR(50)  NOT NULL DEFAULT 'PENDING'
                             CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    reviewed_by            BIGINT       REFERENCES users (users_id),
    reviewed_at            TIMESTAMPTZ,
    review_reason          TEXT,

    is_active              BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by              UUID,
    updated_by              UUID,

    CONSTRAINT chk_payment_lines_review_consistency CHECK (
        (status = 'PENDING' AND reviewed_at IS NULL AND reviewed_by IS NULL)
        OR
        (status IN ('APPROVED', 'REJECTED') AND reviewed_at IS NOT NULL AND reviewed_by IS NOT NULL)
    ),
    CONSTRAINT chk_payment_lines_rejection_has_reason CHECK (
        status <> 'REJECTED' OR review_reason IS NOT NULL
    )
);

CREATE INDEX idx_payment_lines_payment ON payment_lines (payment_id);
CREATE INDEX idx_payment_lines_status ON payment_lines (status) WHERE is_active;

CREATE TRIGGER trg_payment_lines_updated_at
    BEFORE UPDATE ON payment_lines
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ─── Backfill: one line per existing payment ────────────────────────────────
-- Straight 1:1 mapping — payment_method (old CHECK values, verbatim-preserved
-- as payment_methods.code in V115) + reference_number + amount + currency_id
-- move down to a single line per header, header status copied to line status
-- so approved/rejected payments keep the same review trail on their line.
INSERT INTO payment_lines (payment_id, payment_type_id, amount, currency_id, reference_number,
                            status, reviewed_by, reviewed_at, review_reason, is_active,
                            created_at, updated_at, created_by, updated_by)
SELECT p.payments_id,
       pm.payment_methods_id,
       p.amount,
       p.currency_id,
       p.reference_number,
       p.status,
       p.reviewed_by,
       p.reviewed_at,
       p.review_reason,
       p.is_active,
       p.created_at,
       p.updated_at,
       p.created_by,
       p.updated_by
FROM payments p
         JOIN payment_methods pm ON pm.code = p.payment_method;

-- ─── Drop dependent report views (V97) before dropping their columns ───────
DROP VIEW app.v_report_commissions;
DROP VIEW app.v_report_payments;

-- payment_method/reference_number now live on payment_lines.
ALTER TABLE payments DROP COLUMN payment_method;
ALTER TABLE payments DROP COLUMN reference_number;

-- ─── Recreate report views against payment_lines (single line per payment
-- today, so a straight join keeps the same one-row-per-payment shape) ──────
CREATE OR REPLACE VIEW app.v_report_commissions AS
SELECT
    c.commissions_id,
    c.uuid AS commission_uuid,
    c.status AS commission_status,
    c.applies_to,
    c.period_strategy,
    c.period_start,
    c.period_end,
    c.earned_at,
    c.amount AS commission_amount,
    cur.code AS currency,
    c.calculation_basis,
    c.commission_pct,
    c.flat_amount,
    COALESCE(c.tier_name_snapshot, 'Sin Nivel') AS tier_name,
    c.payout_reference,
    c.paid_at,
    c.voided_at,
    c.void_reason,
    c.admin_notes,
    c.created_at AS commission_created_at,
    -- Promoter Information
    pr.promoters_id,
    pr.uuid AS promoter_uuid,
    pr.display_name AS promoter_name,
    pr.referral_code AS promoter_referral_code,
    COALESCE(pr_p.full_name, pr.display_name) AS promoter_person_name,
    COALESCE(pr_p.document_type || '-' || pr_p.document_number, 'N/A') AS promoter_document,
    COALESCE(pr.phone, pr_p.phone, 'N/A') AS promoter_phone,
    COALESCE(pr.email::text, pr_p.email::text, 'N/A') AS promoter_email,
    COALESCE(pt.name, 'Estándar') AS promoter_type_name,
    -- Member / Affiliate Information
    m.members_id,
    m.uuid AS member_uuid,
    m_p.full_name AS member_name,
    (m_p.document_type || '-' || m_p.document_number) AS member_document,
    COALESCE(m_p.phone, 'N/A') AS member_phone,
    COALESCE(m_p.email::text, 'N/A') AS member_email,
    -- Payment Origin
    p.payments_id,
    p.uuid AS payment_uuid,
    p.payment_date,
    p.received_at AS payment_received_at,
    pm.code AS payment_method,
    pline.reference_number AS payment_reference,
    p.amount AS payment_amount,
    p.status AS payment_status,
    -- Plan and Membership
    ms.memberships_id,
    ms.uuid AS membership_uuid,
    pl.plans_id,
    pl.code AS plan_code,
    pl.name AS plan_name,
    pl.type AS plan_type
FROM app.commissions c
JOIN app.currencies cur ON c.currency_id = cur.currencies_id
JOIN app.promoters pr ON c.promoter_id = pr.promoters_id
LEFT JOIN app.persons pr_p ON pr.person_id = pr_p.persons_id
LEFT JOIN app.promoter_types pt ON pr.promoter_type_id = pt.promoter_types_id
JOIN app.members m ON c.member_id = m.members_id
JOIN app.persons m_p ON m.person_id = m_p.persons_id
JOIN app.payments p ON c.payment_id = p.payments_id
LEFT JOIN app.payment_lines pline ON pline.payment_id = p.payments_id
LEFT JOIN app.payment_methods pm ON pm.payment_methods_id = pline.payment_type_id
LEFT JOIN app.memberships ms ON p.membership_id = ms.memberships_id
LEFT JOIN app.plans pl ON ms.plan_id = pl.plans_id
WHERE c.is_active = true;

CREATE OR REPLACE VIEW app.v_report_payments AS
SELECT
    p.payments_id,
    p.uuid AS payment_uuid,
    p.payment_date,
    p.received_at,
    pm.code AS payment_method,
    pline.reference_number,
    p.status AS payment_status,
    p.inscription,
    CASE WHEN p.inscription THEN 'Inscripción' ELSE 'Mensualidad' END AS concept_label,
    p.applied_period,
    p.amount AS gross_amount,
    cur.code AS currency,
    COALESCE(p.discount_amount, 0.00) AS discount_amount,
    p.discount_reason,
    (p.amount - COALESCE(p.discount_amount, 0.00)) AS net_amount,
    p.admin_notes,
    p.reviewed_at,
    p.review_reason,
    p.support_file_name,
    p.created_at AS payment_created_at,
    -- Reviewer Operator
    COALESCE(ru_p.full_name, 'Sin Revisar') AS reviewer_name,
    -- Discounter Operator
    du_p.full_name AS discounter_name,
    -- Membership and Plan
    ms.memberships_id,
    ms.uuid AS membership_uuid,
    ms.status AS membership_status,
    ms.monthly_fee AS plan_monthly_fee,
    pl.plans_id,
    pl.code AS plan_code,
    pl.name AS plan_name,
    pl.type AS plan_type,
    -- Member / Affiliate
    m.members_id,
    m.uuid AS member_uuid,
    m_p.full_name AS member_name,
    (m_p.document_type || '-' || m_p.document_number) AS member_document,
    COALESCE(m_p.phone, 'N/A') AS member_phone,
    COALESCE(m_p.email::text, 'N/A') AS member_email,
    -- Assigned Promoter
    pr.promoters_id,
    pr.uuid AS promoter_uuid,
    COALESCE(pr.display_name, 'Directo / Sin Promotor') AS promoter_name,
    pr.referral_code AS promoter_referral_code
FROM app.payments p
JOIN app.currencies cur ON p.currency_id = cur.currencies_id
JOIN app.memberships ms ON p.membership_id = ms.memberships_id
JOIN app.plans pl ON ms.plan_id = pl.plans_id
JOIN app.members m ON ms.member_id = m.members_id
JOIN app.persons m_p ON m.person_id = m_p.persons_id
LEFT JOIN app.promoters pr ON m.promoter_id = pr.promoters_id
LEFT JOIN app.payment_lines pline ON pline.payment_id = p.payments_id
LEFT JOIN app.payment_methods pm ON pm.payment_methods_id = pline.payment_type_id
LEFT JOIN app.users ru ON p.reviewed_by = ru.users_id
LEFT JOIN app.persons ru_p ON ru.person_id = ru_p.persons_id
LEFT JOIN app.users du ON p.discounted_by = du.users_id
LEFT JOIN app.persons du_p ON du.person_id = du_p.persons_id
WHERE p.is_active = true;

GRANT SELECT ON app.v_report_commissions TO optibienestar360_app;
GRANT SELECT ON app.v_report_payments TO optibienestar360_app;
