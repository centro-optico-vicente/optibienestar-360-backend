SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V96: Database Views for Commissions and Payments Reporting
-- Provides centralized, high-performance read models for JasperReports
-- and administrative queries.
-- ────────────────────────────────────────────────────────────────────────────

-- 1. View: v_report_commissions
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
    p.payment_method,
    p.reference_number AS payment_reference,
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
LEFT JOIN app.memberships ms ON p.membership_id = ms.memberships_id
LEFT JOIN app.plans pl ON ms.plan_id = pl.plans_id
WHERE c.is_active = true;

-- 2. View: v_report_payments
CREATE OR REPLACE VIEW app.v_report_payments AS
SELECT
    p.payments_id,
    p.uuid AS payment_uuid,
    p.payment_date,
    p.received_at,
    p.payment_method,
    p.reference_number,
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
LEFT JOIN app.users ru ON p.reviewed_by = ru.users_id
LEFT JOIN app.persons ru_p ON ru.person_id = ru_p.persons_id
LEFT JOIN app.users du ON p.discounted_by = du.users_id
LEFT JOIN app.persons du_p ON du.person_id = du_p.persons_id
WHERE p.is_active = true;

-- 3. Grants for application runtime user
GRANT SELECT ON app.v_report_commissions TO optibienestar360_app;
GRANT SELECT ON app.v_report_payments TO optibienestar360_app;
