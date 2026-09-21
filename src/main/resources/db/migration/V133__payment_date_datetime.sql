SET search_path TO app, public;

-- ============================================================================
-- V122: Recreates v_report_payments and v_report_commissions to reflect the
-- payments unification (hub plan 2026-09-17-payments-unification-plan.md,
-- V115-V121).
--
-- v_report_payments:
--   - Unifies IN (collections) and OUT (commission payouts).
--   - Changes memberships/plans/members to LEFT JOIN so OUT rows
--     (where membership_id IS NULL, V120) are NOT excluded.
--   - Exposes `direction` ('IN' / 'OUT').
--   - Exposes payment category reason (FK payment_categories: code, name).
--   - Exposes counterpart person (FK person_id -> persons) and promoter
--     (FK promoter_id -> promoters) directly from the payments header.
--   - Exposes bank information (FK bank_id -> banks) and payment method
--     Spanish name from payment_lines.
--
-- v_report_commissions:
--   - Enriches commissions with their settlement OUT payout payment
--     (commissions.payout_payment_id, V118) and payout lines/bank/method.
--   - Exposes FX snapshots (exchange_rate_at_earned, exchange_rate_at_paid).
--   - Preserves backward compatibility for c.payout_reference.
-- ============================================================================

DROP VIEW IF EXISTS app.v_report_commissions CASCADE;
DROP VIEW IF EXISTS app.v_report_payments CASCADE;

-- Payment settlement timestamps replace the legacy calendar-only value.
-- Existing dates are preserved as midnight in the application's business
-- timezone before being stored as instants.
ALTER TABLE app.payments
    DROP CONSTRAINT chk_payments_date_not_future;

ALTER TABLE app.payments
    ALTER COLUMN payment_date TYPE TIMESTAMPTZ
        USING payment_date::timestamp AT TIME ZONE 'America/Caracas';

ALTER TABLE app.payments
    ADD CONSTRAINT chk_payments_date_not_future CHECK (
        payment_date <= CURRENT_TIMESTAMP + INTERVAL '1 day'
    );

-- ─── 1. Unified v_report_payments ───────────────────────────────────────────
CREATE OR REPLACE VIEW app.v_report_payments AS
SELECT
    p.payments_id,
    p.uuid AS payment_uuid,
    p.direction,
    p.payment_date,
    p.received_at,
    p.status AS payment_status,
    -- Category / Reason
    pc.payment_categories_id,
    pc.code AS payment_category_code,
    pc.name AS payment_category_name,
    pc.direction AS payment_category_direction,
    COALESCE(pc.name, CASE WHEN p.inscription THEN 'Inscripción' ELSE 'Mensualidad' END) AS concept_label,
    -- Line Details (method, bank, reference)
    pline.payment_line_id,
    pline.uuid AS payment_line_uuid,
    pm.payment_methods_id,
    pm.code AS payment_method,
    pm.name AS payment_method_name,
    b.banks_id,
    b.code AS bank_code,
    b.short_name AS bank_name,
    pline.reference_number,
    pline.amount AS line_amount,
    pline.status AS line_status,
    -- Amounts and Currency
    COALESCE(pline.amount, p.amount) AS gross_amount,
    cur.code AS currency,
    cur.symbol AS currency_symbol,
    CASE 
        WHEN p.amount > 0 AND p.discount_amount IS NOT NULL 
        THEN ROUND(p.discount_amount * (COALESCE(pline.amount, p.amount) / p.amount), 2)
        ELSE 0.00 
    END AS discount_amount,
    p.discount_reason,
    (COALESCE(pline.amount, p.amount) - CASE 
        WHEN p.amount > 0 AND p.discount_amount IS NOT NULL 
        THEN ROUND(p.discount_amount * (COALESCE(pline.amount, p.amount) / p.amount), 2)
        ELSE 0.00 
    END) AS net_amount,
    p.exchange_rate_used,
    p.exchange_rate_date,
    -- Allocation & Audit
    p.inscription,
    p.applied_period,
    p.admin_notes,
    p.reviewed_at,
    p.review_reason,
    p.support_file_name,
    p.created_at AS payment_created_at,
    -- Reviewer & Discounter Operators
    COALESCE(ru_p.full_name, 'Sin Revisar') AS reviewer_name,
    du_p.full_name AS discounter_name,
    -- Counterpart Person (direct FK on payments header)
    per.persons_id,
    per.uuid AS person_uuid,
    per.full_name AS person_name,
    (per.document_type || '-' || per.document_number) AS person_document,
    COALESCE(per.phone, 'N/A') AS person_phone,
    COALESCE(per.email::text, 'N/A') AS person_email,
    -- Assigned / Receiving Promoter (direct denormalized FK on payments)
    pr.promoters_id,
    pr.uuid AS promoter_uuid,
    COALESCE(pr.display_name, 'Directo / Sin Promotor') AS promoter_name,
    pr.referral_code AS promoter_referral_code,
    -- Membership and Plan (populated for direction=IN, NULL for direction=OUT)
    ms.memberships_id,
    ms.uuid AS membership_uuid,
    ms.status AS membership_status,
    ms.monthly_fee AS plan_monthly_fee,
    pl.plans_id,
    pl.code AS plan_code,
    pl.name AS plan_name,
    pl.type AS plan_type,
    -- Affiliate Member (populated for direction=IN, NULL for direction=OUT)
    m.members_id,
    m.uuid AS member_uuid,
    m_p.full_name AS member_name,
    (m_p.document_type || '-' || m_p.document_number) AS member_document,
    COALESCE(m_p.phone, 'N/A') AS member_phone,
    COALESCE(m_p.email::text, 'N/A') AS member_email
FROM app.payments p
JOIN app.currencies cur ON p.currency_id = cur.currencies_id
JOIN app.payment_categories pc ON p.payment_type_id = pc.payment_categories_id
LEFT JOIN app.payment_lines pline ON pline.payment_id = p.payments_id
LEFT JOIN app.payment_methods pm ON pm.payment_methods_id = pline.payment_type_id
LEFT JOIN app.banks b ON b.banks_id = pline.bank_id
LEFT JOIN app.persons per ON p.person_id = per.persons_id
LEFT JOIN app.promoters pr ON p.promoter_id = pr.promoters_id
LEFT JOIN app.memberships ms ON p.membership_id = ms.memberships_id
LEFT JOIN app.plans pl ON ms.plan_id = pl.plans_id
LEFT JOIN app.members m ON ms.member_id = m.members_id
LEFT JOIN app.persons m_p ON m.person_id = m_p.persons_id
LEFT JOIN app.users ru ON p.reviewed_by = ru.users_id
LEFT JOIN app.persons ru_p ON ru.person_id = ru_p.persons_id
LEFT JOIN app.users du ON p.discounted_by = du.users_id
LEFT JOIN app.persons du_p ON du.person_id = du_p.persons_id
WHERE p.is_active = true;

-- ─── 2. Enriched v_report_commissions ───────────────────────────────────────
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
    cur.symbol AS currency_symbol,
    c.calculation_basis,
    c.commission_pct,
    c.flat_amount,
    COALESCE(c.tier_name_snapshot, 'Sin Nivel') AS tier_name,
    -- Payout settlement (OUT payment) details
    c.payout_payment_id,
    payout_p.uuid AS payout_payment_uuid,
    payout_p.payment_date AS payout_date,
    payout_p.status AS payout_payment_status,
    payout_line.payout_method_code,
    payout_line.payout_method_name,
    payout_line.payout_bank_name,
    COALESCE(payout_line.reference_number, c.payout_reference) AS payout_reference,
    c.paid_at,
    -- FX Snapshots
    c.exchange_rate_at_earned,
    c.earned_rate_date,
    c.exchange_rate_at_paid,
    c.paid_rate_date,
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
    -- Origin Payment (IN)
    p.payments_id,
    p.uuid AS payment_uuid,
    p.payment_date,
    p.received_at AS payment_received_at,
    origin_line.payment_method,
    origin_line.payment_method_name,
    origin_line.payment_bank_name,
    origin_line.payment_reference,
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
-- Origin IN Payment
JOIN app.payments p ON c.payment_id = p.payments_id
LEFT JOIN LATERAL (
    SELECT
        STRING_AGG(DISTINCT origin_pm.code, ', ') AS payment_method,
        STRING_AGG(DISTINCT origin_pm.name, ', ') AS payment_method_name,
        STRING_AGG(DISTINCT origin_b.short_name, ', ') AS payment_bank_name,
        STRING_AGG(DISTINCT pl.reference_number, ', ') AS payment_reference
    FROM app.payment_lines pl
    LEFT JOIN app.payment_methods origin_pm ON origin_pm.payment_methods_id = pl.payment_type_id
    LEFT JOIN app.banks origin_b ON origin_b.banks_id = pl.bank_id
    WHERE pl.payment_id = p.payments_id
) origin_line ON true
LEFT JOIN app.memberships ms ON p.membership_id = ms.memberships_id
LEFT JOIN app.plans pl ON ms.plan_id = pl.plans_id
-- Payout OUT Payment (V118)
LEFT JOIN app.payments payout_p ON payout_p.payments_id = c.payout_payment_id
LEFT JOIN LATERAL (
    SELECT
        STRING_AGG(DISTINCT payout_pm.code, ', ') AS payout_method_code,
        STRING_AGG(DISTINCT payout_pm.name, ', ') AS payout_method_name,
        STRING_AGG(DISTINCT payout_b.short_name, ', ') AS payout_bank_name,
        STRING_AGG(DISTINCT pol.reference_number, ', ') AS reference_number
    FROM app.payment_lines pol
    LEFT JOIN app.payment_methods payout_pm ON payout_pm.payment_methods_id = pol.payment_type_id
    LEFT JOIN app.banks payout_b ON payout_b.banks_id = pol.bank_id
    WHERE pol.payment_id = payout_p.payments_id
) payout_line ON true
WHERE c.is_active = true;

-- ─── 3. Grants for application runtime user ─────────────────────────────────
GRANT SELECT ON app.v_report_commissions TO optibienestar360_app;
GRANT SELECT ON app.v_report_payments TO optibienestar360_app;
