-- ────────────────────────────────────────────────────────────────────────────
-- DEV-ONLY seed — edge-case scenarios on top of dev-seed-network-history.sql
--
-- Adds three narrative situations for demos / QA:
--
--   A. Aliado con convenio VENCIDO — ally whose commercial agreement lapsed
--      (ally_agreements.status = 'EXPIRED', end_date in the past); still on
--      record but unpublished.
--
--   B. Afiliados MOROSOS — two members behind on payment:
--        · SUSPENDED — paid through July, no August payment, past due.
--        · EXPIRED   — paid only June, past due + grace, plus one REJECTED
--                      August payment attempt.
--
--   C. Promotores que alcanzaron METAS de suscriptores (commission_bonus_rules
--      seeded in V50: 41 / 61 / 76 / 101 inscripciones/mes):
--        · PROMOTOP01 — alcanzó las primeras metas (41 y 61) → 2 bonos PAID.
--        · PROMOTOP02 — alcanzó TODAS las metas (41/61/76/101) → 4 bonos,
--          los dos primeros PAID, los dos últimos PENDING.
--        NOTE: promoter_bonus_awards.metric_count is a snapshot integer; it is
--        set directly here (no 100+ real member rows are generated).
--
-- Depends on dev-seed-network-history.sql having run (uses PROMOSEED01 and
-- admin user id 3). IDEMPOTENT: aborts (rolls back) if PROMOTOP01 exists.
-- Cleanup markers: emails LIKE '%@seed.optibienestar.test',
--   promoters.referral_code IN ('PROMOTOP01','PROMOTOP02'),
--   persons.document_number BETWEEN '90000019' AND '90000020' and '80000018'/'80000019'.
-- ────────────────────────────────────────────────────────────────────────────

BEGIN;

SET LOCAL search_path TO app, public;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM app.promoters WHERE referral_code = 'PROMOTOP01') THEN
        RAISE EXCEPTION
            'dev-seed-network-scenarios already applied — nothing to do (rolling back)';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM app.promoters WHERE referral_code = 'PROMOSEED01') THEN
        RAISE EXCEPTION
            'run dev-seed-network-history.sql first (PROMOSEED01 not found)';
    END IF;
END $$;


-- ════════════════════════════════════════════════════════════════════════════
-- A. Aliado con convenio VENCIDO
-- ════════════════════════════════════════════════════════════════════════════

INSERT INTO app.allies (
    name, ally_type_id, tax_document_type, tax_document_number,
    email, phone, website, address, city_id, joined_at,
    is_published, is_active, status, created_at
)
VALUES (
    'Aliado Scenario — Convenio Vencido',
    4, 'J', '409999999',
    'aliado.vencido@seed.optibienestar.test',
    '0261-9990001',
    'https://aliado-vencido.seed.optibienestar.test',
    'Calle 72 con Av. 3H, local 4',
    460,
    DATE '2025-06-01',
    FALSE, TRUE, 'AGREEMENT_EXPIRED',
    TIMESTAMPTZ '2025-06-01 09:00+00'
);

INSERT INTO app.ally_agreements (
    ally_id, agreement_type, start_date, end_date, terms, status, is_active
)
SELECT
    a.allies_id, 'COMMERCIAL',
    DATE '2025-06-01', DATE '2026-05-31',
    'Convenio comercial 2025-2026 NO renovado — aliado sin descuento activo (datos de prueba).',
    'EXPIRED', FALSE
FROM app.allies a
WHERE a.email = 'aliado.vencido@seed.optibienestar.test';


-- ════════════════════════════════════════════════════════════════════════════
-- B. Afiliados MOROSOS  (SUSPENDED + EXPIRED)
-- ════════════════════════════════════════════════════════════════════════════

-- 4 persons+members share promoter PROMOSEED01. document_number 90000019/20.
INSERT INTO app.persons (
    first_name, last_name, document_type, document_number,
    email, phone, gender_id, city_id
)
VALUES
    ('Afiliado', 'Moroso Suspendido', 'V', '90000019',
     'afiliado.moroso.susp@seed.optibienestar.test', '0424-9990019', 1, 460),
    ('Afiliado', 'Moroso Vencido',    'V', '90000020',
     'afiliado.moroso.venc@seed.optibienestar.test', '0424-9990020', 2, 138);

INSERT INTO app.members (person_id, occupation_id, enrolled_at, promoter_id, status)
SELECT p.persons_id, 5, DATE '2026-06-05',
       (SELECT promoters_id FROM app.promoters WHERE referral_code = 'PROMOSEED01'),
       'ACTIVE'
FROM app.persons p
WHERE p.document_number IN ('90000019', '90000020');

-- Memberships: plan 1 (ins 10 / mensual 5 / gracia 7).
--   90000019 → last_paid_through 2026-07-31, next_due 2026-08-01  → SUSPENDED
--   90000020 → last_paid_through 2026-06-30, next_due 2026-07-01  → EXPIRED
INSERT INTO app.memberships (
    member_id, plan_id, enrolled_at, next_due_date, last_paid_through,
    inscription_fee, monthly_fee, grace_period_days, billing_start_day,
    is_active, status, last_status_change_at, last_status_change_reason
)
SELECT
    m.members_id, 1, DATE '2026-06-05',
    CASE p.document_number WHEN '90000019' THEN DATE '2026-08-01'
                           ELSE DATE '2026-07-01' END,
    CASE p.document_number WHEN '90000019' THEN DATE '2026-07-31'
                           ELSE DATE '2026-06-30' END,
    10.00, 5.00, 7, 5,
    TRUE,
    CASE p.document_number WHEN '90000019' THEN 'SUSPENDED' ELSE 'EXPIRED' END,
    CASE p.document_number WHEN '90000019' THEN TIMESTAMPTZ '2026-08-08 03:00+00'
                           ELSE TIMESTAMPTZ '2026-07-15 03:00+00' END,
    CASE p.document_number WHEN '90000019' THEN 'Falta de pago — período agosto 2026 no cubierto.'
                           ELSE 'Vencida — sin pago desde julio 2026, superado el período de gracia.' END
FROM app.members m
JOIN app.persons p ON p.persons_id = m.person_id
WHERE p.document_number IN ('90000019', '90000020');

-- Inscription payments (both APPROVED).
INSERT INTO app.payments (
    membership_id, amount, currency, payment_method, reference_number,
    payment_date, received_at, inscription, applied_period,
    status, reviewed_by, reviewed_at, is_active, created_at
)
SELECT
    ms.memberships_id, 10.00, 'USD', 'CASH',
    'INS-' || lpad(ms.memberships_id::text, 4, '0'),
    DATE '2026-06-05', TIMESTAMPTZ '2026-06-05 14:00+00',
    TRUE, NULL, 'APPROVED', 3, TIMESTAMPTZ '2026-06-06 10:00+00',
    TRUE, TIMESTAMPTZ '2026-06-05 14:00+00'
FROM app.memberships ms
JOIN app.members m ON m.members_id = ms.member_id
JOIN app.persons p ON p.persons_id = m.person_id
WHERE p.document_number IN ('90000019', '90000020');

-- Monthly APPROVED payments: 90000019 pays Jun+Jul, 90000020 pays Jun only.
INSERT INTO app.payments (
    membership_id, amount, currency, payment_method, reference_number,
    payment_date, received_at, inscription, applied_period,
    status, reviewed_by, reviewed_at, is_active, created_at
)
SELECT
    ms.memberships_id, 5.00, 'USD', 'PAGO_MOVIL',
    'MON-' || lpad(ms.memberships_id::text, 4, '0') || '-' || to_char(mo.d, 'YYYYMM'),
    mo.d + 3, (mo.d + 3)::timestamptz, FALSE, mo.d,
    'APPROVED', 3, (mo.d + 4)::timestamptz, TRUE, (mo.d + 3)::timestamptz
FROM app.memberships ms
JOIN app.members m ON m.members_id = ms.member_id
JOIN app.persons p ON p.persons_id = m.person_id
JOIN LATERAL (
    SELECT d FROM (VALUES (DATE '2026-06-01'), (DATE '2026-07-01')) AS v(d)
    WHERE p.document_number = '90000019' OR v.d = DATE '2026-06-01'
) mo ON TRUE
WHERE p.document_number IN ('90000019', '90000020');

-- 90000020: one REJECTED August payment attempt (illegible proof).
INSERT INTO app.payments (
    membership_id, amount, currency, payment_method, reference_number,
    payment_date, received_at, inscription, applied_period,
    status, reviewed_by, reviewed_at, review_reason, is_active, created_at
)
SELECT
    ms.memberships_id, 5.00, 'USD', 'BANK_TRANSFER',
    'MON-' || lpad(ms.memberships_id::text, 4, '0') || '-202608-R',
    DATE '2026-08-10', TIMESTAMPTZ '2026-08-10 16:00+00', FALSE, DATE '2026-08-01',
    'REJECTED', 3, TIMESTAMPTZ '2026-08-11 09:00+00',
    'Comprobante ilegible y monto no coincide con la mensualidad — se solicitó reenvío.',
    TRUE, TIMESTAMPTZ '2026-08-10 16:00+00'
FROM app.memberships ms
JOIN app.members m ON m.members_id = ms.member_id
JOIN app.persons p ON p.persons_id = m.person_id
WHERE p.document_number = '90000020';

-- Commissions for their APPROVED payments (10%, PAID for Jun/Jul).
INSERT INTO app.commissions (
    promoter_id, payment_id, member_id, amount, currency,
    calculation_basis, commission_pct, tier_name_snapshot,
    applies_to, period_strategy, period_start, period_end, earned_at,
    status, paid_at, payout_reference, is_active
)
SELECT
    m.promoter_id, pay.payments_id, m.members_id,
    ROUND(pay.amount * 0.10, 2), 'USD', pay.amount, 10.00, 'Base 10% (seed)',
    CASE WHEN pay.inscription THEN 'INSCRIPTION' ELSE 'MONTHLY' END,
    'MONTHLY',
    date_trunc('month', pay.reviewed_at)::date,
    (date_trunc('month', pay.reviewed_at) + INTERVAL '1 month - 1 day')::date,
    pay.reviewed_at,
    'PAID',
    (date_trunc('month', pay.reviewed_at) + INTERVAL '1 month 3 days')::timestamptz,
    'BATCH-' || to_char(pay.reviewed_at, 'YYYYMM'),
    TRUE
FROM app.payments pay
JOIN app.memberships ms ON ms.memberships_id = pay.membership_id
JOIN app.members m ON m.members_id = ms.member_id
JOIN app.persons p ON p.persons_id = m.person_id
WHERE p.document_number IN ('90000019', '90000020')
  AND pay.status = 'APPROVED';


-- ════════════════════════════════════════════════════════════════════════════
-- C. Promotores que alcanzaron METAS de suscriptores
-- ════════════════════════════════════════════════════════════════════════════

INSERT INTO app.persons (
    first_name, last_name, document_type, document_number,
    email, phone, gender_id, city_id
)
VALUES
    ('Promotor', 'Metas Parciales', 'V', '80000018',
     'promotor.metas1@seed.optibienestar.test', '0414-9990018', 1, 460),
    ('Promotor', 'Metas Totales',   'V', '80000019',
     'promotor.metas2@seed.optibienestar.test', '0414-9990019', 2, 138);

INSERT INTO app.users (person_id, email, password_hash, default_role_id, status)
SELECT p.persons_id, p.email, app.bcrypt_hash('optibienestar360'),
       (SELECT roles_id FROM app.roles WHERE name = 'PROMOTOR'), 'ACTIVE'
FROM app.persons p
WHERE p.document_number IN ('80000018', '80000019');

INSERT INTO app.user_roles (user_id, role_id)
SELECT u.users_id, (SELECT roles_id FROM app.roles WHERE name = 'PROMOTOR')
FROM app.users u JOIN app.persons p ON p.persons_id = u.person_id
WHERE p.document_number IN ('80000018', '80000019')
ON CONFLICT DO NOTHING;

INSERT INTO app.promoters (
    user_id, person_id, display_name, description, referral_code,
    email, phone, is_active, status, created_at,
    total_referrals, total_commission_paid
)
SELECT
    u.users_id, p.persons_id, p.full_name,
    CASE p.document_number
        WHEN '80000018' THEN 'Promotor que alcanzó las metas 41 y 61 inscripciones/mes.'
        ELSE 'Promotor que alcanzó TODAS las metas (41/61/76/101 inscripciones/mes).' END,
    CASE p.document_number WHEN '80000018' THEN 'PROMOTOP01' ELSE 'PROMOTOP02' END,
    p.email, p.phone, TRUE, 'ACTIVE', TIMESTAMPTZ '2026-06-01 09:00+00',
    CASE p.document_number WHEN '80000018' THEN 68 ELSE 112 END,
    CASE p.document_number WHEN '80000018' THEN 425.00 ELSE 900.00 END
FROM app.persons p
JOIN app.users u ON u.person_id = p.persons_id
WHERE p.document_number IN ('80000018', '80000019');

-- Bonus awards — window = August 2026, one per reached rule.
INSERT INTO app.promoter_bonus_awards (
    uuid, bonus_rule_id, promoter_id, window_start, window_end,
    blocks_awarded, metric_count, reward_type, flat_amount, amount,
    reward_currency, rule_name_snapshot, evaluated_at,
    status, paid_at, payout_reference
)
SELECT
    gen_random_uuid(), r.commission_bonus_rules_id, pr.promoters_id,
    DATE '2026-08-01', DATE '2026-08-31',
    1, s.metric_count, 'FLAT', r.flat_amount, r.flat_amount,
    'USD', r.name, TIMESTAMPTZ '2026-09-01 08:00+00',
    s.status, s.paid_at, s.payout_ref
FROM (VALUES
    ('PROMOTOP01',  41,  68, 'PAID',    TIMESTAMPTZ '2026-09-03 10:00+00', 'BONUS-202608-P01'),
    ('PROMOTOP01',  61,  68, 'PAID',    TIMESTAMPTZ '2026-09-03 10:00+00', 'BONUS-202608-P01'),
    ('PROMOTOP02',  41, 112, 'PAID',    TIMESTAMPTZ '2026-09-03 10:00+00', 'BONUS-202608-P02'),
    ('PROMOTOP02',  61, 112, 'PAID',    TIMESTAMPTZ '2026-09-03 10:00+00', 'BONUS-202608-P02'),
    ('PROMOTOP02',  76, 112, 'PENDING', NULL,                              NULL),
    ('PROMOTOP02', 101, 112, 'PENDING', NULL,                              NULL)
) AS s(ref, threshold, metric_count, status, paid_at, payout_ref)
JOIN app.commission_bonus_rules r
      ON r.threshold_count = s.threshold AND r.window_strategy = 'MONTHLY'
JOIN app.promoters pr ON pr.referral_code = s.ref;


-- ─── Summary ───────────────────────────────────────────────────────────────
DO $$
DECLARE
    v_ally_exp int; v_susp int; v_exp int; v_rej int;
    v_awards int; v_awards_paid int;
BEGIN
    SELECT count(*) INTO v_ally_exp FROM app.ally_agreements
      WHERE status = 'EXPIRED' AND ally_id IN
        (SELECT allies_id FROM app.allies WHERE email = 'aliado.vencido@seed.optibienestar.test');
    SELECT count(*) FILTER (WHERE ms.status = 'SUSPENDED'),
           count(*) FILTER (WHERE ms.status = 'EXPIRED')
      INTO v_susp, v_exp
      FROM app.memberships ms
      JOIN app.members m ON m.members_id = ms.member_id
      JOIN app.persons p ON p.persons_id = m.person_id
      WHERE p.document_number IN ('90000019', '90000020');
    SELECT count(*) INTO v_rej FROM app.payments pay
      JOIN app.memberships ms ON ms.memberships_id = pay.membership_id
      JOIN app.members m ON m.members_id = ms.member_id
      JOIN app.persons p ON p.persons_id = m.person_id
      WHERE p.document_number IN ('90000019', '90000020') AND pay.status = 'REJECTED';
    SELECT count(*), count(*) FILTER (WHERE pba.status = 'PAID')
      INTO v_awards, v_awards_paid
      FROM app.promoter_bonus_awards pba
      JOIN app.promoters pr ON pr.promoters_id = pba.promoter_id
      WHERE pr.referral_code IN ('PROMOTOP01', 'PROMOTOP02');

    RAISE NOTICE '── dev-seed-network-scenarios summary ────────────────────';
    RAISE NOTICE 'aliado convenio vencido : % agreement EXPIRED', v_ally_exp;
    RAISE NOTICE 'afiliados morosos       : % SUSPENDED + % EXPIRED (% pago RECHAZADO)',
        v_susp, v_exp, v_rej;
    RAISE NOTICE 'bonos por metas         : % awards (% PAID) para PROMOTOP01/02',
        v_awards, v_awards_paid;
END $$;

COMMIT;
