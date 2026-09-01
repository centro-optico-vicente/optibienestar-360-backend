-- ────────────────────────────────────────────────────────────────────────────
-- DEV-ONLY seed — 3-month history for the sales network (promoters), the
-- commercial directory (allies) and the payment/commission pipeline.
--
-- Generates data for the current month plus the two preceding ones. With
-- "today" = 2026-08-30 that is: June, July and August 2026.
--
-- What it inserts (all rows tagged so they are easy to find / wipe):
--   · 17 persons + 17 users + 17 promoters  (15 ACTIVE + 2 INACTIVE random)
--   · 14 allies + 14 ally_agreements        (12 ACTIVE + 2 INACTIVE random)
--   · 18 persons + 18 members + 18 memberships (round-robin over plans 1-3
--     and over the 15 active promoters)
--   · ~55 payments across Jun/Jul/Aug — inscription + monthly, mostly
--     APPROVED, a few PENDING (August) and 2 REJECTED (with reason)
--   · one commission per APPROVED payment — PAID for Jun/Jul, PENDING for Aug
--   · promoter snapshot counters + membership paid-through recomputed
--
-- Seed markers (for cleanup):
--   promoters.referral_code   LIKE 'PROMOSEED%'
--   allies.email / persons.email LIKE '%@seed.optibienestar.test'
--   persons.document_number   BETWEEN '80000001' AND '80000017' (promoters)
--                             BETWEEN '90000001' AND '90000018' (members)
--
-- IDEMPOTENT GUARD: aborts (and rolls back) if 'PROMOSEED01' already exists.
-- Run the whole BEGIN..COMMIT block in a single execution.
-- ────────────────────────────────────────────────────────────────────────────

BEGIN;

SET LOCAL search_path TO app, public;

-- ─── Idempotency guard ─────────────────────────────────────────────────────
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM app.promoters WHERE referral_code = 'PROMOSEED01') THEN
        RAISE EXCEPTION
            'dev-seed-network-history already applied — nothing to do (rolling back)';
    END IF;
END $$;


-- ════════════════════════════════════════════════════════════════════════════
-- 1. PROMOTERS  (15 ACTIVE + 2 INACTIVE)
-- ════════════════════════════════════════════════════════════════════════════

-- 1.1 persons
INSERT INTO app.persons (
    first_name, last_name, document_type, document_number,
    email, phone, gender_id, city_id
)
SELECT
    'Promotor',
    'Seed ' || lpad(g::text, 2, '0'),
    'V',
    (80000000 + g)::text,
    'promotor' || g || '@seed.optibienestar.test',
    '0414-' || lpad(g::text, 7, '0'),
    CASE WHEN g % 2 = 0 THEN 2 ELSE 1 END,
    (ARRAY[460, 138, 95, 214])[1 + (g % 4)]
FROM generate_series(1, 17) AS g;

-- 1.2 users (login for the promoter portal — password 'optibienestar360')
INSERT INTO app.users (person_id, email, password_hash, default_role_id, status)
SELECT
    p.persons_id,
    p.email,
    app.bcrypt_hash('optibienestar360'),
    (SELECT roles_id FROM app.roles WHERE name = 'PROMOTOR'),
    'ACTIVE'
FROM app.persons p
WHERE p.document_number BETWEEN '80000001' AND '80000017';

INSERT INTO app.user_roles (user_id, role_id)
SELECT u.users_id, (SELECT roles_id FROM app.roles WHERE name = 'PROMOTOR')
FROM app.users u
JOIN app.persons p ON p.persons_id = u.person_id
WHERE p.document_number BETWEEN '80000001' AND '80000017'
ON CONFLICT DO NOTHING;

-- 1.3 promoters — created_at spread across the 3-month window
INSERT INTO app.promoters (
    user_id, person_id, display_name, description, referral_code,
    email, phone, is_active, status, created_at
)
SELECT
    u.users_id,
    p.persons_id,
    p.full_name,
    'Promotor de prueba generado por dev-seed-network-history.',
    'PROMOSEED' || lpad(g::text, 2, '0'),
    p.email,
    p.phone,
    (g <= 15),
    CASE WHEN g <= 15 THEN 'ACTIVE' ELSE 'INACTIVE' END,
    (DATE '2026-06-01' + ((g * 5) % 85) * INTERVAL '1 day')
FROM generate_series(1, 17) AS g
JOIN app.persons p ON p.document_number = (80000000 + g)::text
JOIN app.users   u ON u.person_id = p.persons_id;


-- ════════════════════════════════════════════════════════════════════════════
-- 2. ALLIES  (12 ACTIVE + 2 INACTIVE) + one agreement each
-- ════════════════════════════════════════════════════════════════════════════

INSERT INTO app.allies (
    name, ally_type_id, tax_document_type, tax_document_number,
    email, phone, website, address, city_id, joined_at,
    is_published, published_at, is_active, status, created_at
)
SELECT
    'Aliado Seed ' || lpad(g::text, 2, '0') || ' — '
        || (ARRAY['Óptica','Clínica','Laboratorio','Centro Médico',
                  'Farmacia','Centro Auditivo'])[1 + (g % 6)],
    (ARRAY[7, 1, 6, 3, 5, 3])[1 + (g % 6)],
    'J',
    '4' || lpad(g::text, 8, '0') || '5',
    'aliado' || g || '@seed.optibienestar.test',
    '0261-' || lpad(g::text, 7, '0'),
    'https://aliado' || g || '.seed.optibienestar.test',
    'Av. Principal #' || g || ', sector comercial',
    (ARRAY[460, 138, 95, 214])[1 + (g % 4)],
    (DATE '2026-06-01' + ((g * 7) % 85) * INTERVAL '1 day')::date,
    (g <= 12),
    CASE WHEN g <= 12
         THEN (DATE '2026-06-01' + ((g * 7) % 85) * INTERVAL '1 day') END,
    (g <= 12),
    CASE WHEN g <= 12 THEN 'ACTIVE' ELSE 'INACTIVE' END,
    (DATE '2026-06-01' + ((g * 7) % 85) * INTERVAL '1 day')
FROM generate_series(1, 14) AS g;

INSERT INTO app.ally_agreements (
    ally_id, agreement_type, start_date, end_date, terms, status, is_active
)
SELECT
    a.allies_id,
    (ARRAY['COMMERCIAL','MEDICAL','SUPPLY','EXCLUSIVITY'])[1 + (a.allies_id % 4)],
    a.joined_at,
    (a.joined_at + INTERVAL '1 year')::date,
    'Convenio de descuento para afiliados OptiBienestar 360 (datos de prueba).',
    CASE WHEN a.is_active THEN 'ACTIVE' ELSE 'TERMINATED' END,
    a.is_active
FROM app.allies a
WHERE a.email LIKE '%@seed.optibienestar.test';


-- ════════════════════════════════════════════════════════════════════════════
-- 3. MEMBERS + MEMBERSHIPS  (18 affiliates, enrolled across Jun/Jul/Aug)
-- ════════════════════════════════════════════════════════════════════════════

-- 3.1 persons
INSERT INTO app.persons (
    first_name, last_name, document_type, document_number,
    email, phone, gender_id, city_id
)
SELECT
    'Afiliado',
    'Seed ' || lpad(g::text, 2, '0'),
    'V',
    (90000000 + g)::text,
    'afiliado' || g || '@seed.optibienestar.test',
    '0424-' || lpad(g::text, 7, '0'),
    CASE WHEN g % 2 = 0 THEN 2 ELSE 1 END,
    (ARRAY[460, 138, 95, 214])[1 + (g % 4)]
FROM generate_series(1, 18) AS g;

-- 3.2 members — round-robin over the 15 active seed promoters.
--     members.referral_code is the member's OWN code (unique) for the
--     referrals program, NOT the promoter's — left NULL here.
INSERT INTO app.members (
    person_id, occupation_id, enrolled_at, promoter_id, status
)
SELECT
    p.persons_id,
    1 + (g % 10),
    (DATE '2026-06-02' + ((g * 4) % 84) * INTERVAL '1 day')::date,
    pr.promoters_id,
    'ACTIVE'
FROM generate_series(1, 18) AS g
JOIN app.persons p ON p.document_number = (90000000 + g)::text
JOIN LATERAL (
    SELECT promoters_id, referral_code
    FROM app.promoters
    WHERE referral_code LIKE 'PROMOSEED%' AND is_active = TRUE
    ORDER BY referral_code
    OFFSET (g - 1) % 15 LIMIT 1
) pr ON TRUE;

-- 3.3 memberships — pricing snapshot from plans 1-3, next_due_date fixed up later
INSERT INTO app.memberships (
    member_id, plan_id, enrolled_at, next_due_date,
    inscription_fee, monthly_fee, grace_period_days,
    billing_start_day, is_active, status
)
SELECT
    mem.members_id,
    pl.plans_id,
    mem.enrolled_at,
    (mem.enrolled_at + INTERVAL '1 month')::date,
    pl.inscription_fee,
    pl.monthly_fee,
    pl.grace_period_days,
    LEAST(EXTRACT(day FROM mem.enrolled_at)::int, 28),
    TRUE,
    'ACTIVE'
FROM app.members mem
JOIN app.persons p ON p.persons_id = mem.person_id
JOIN app.plans   pl ON pl.plans_id = 1 + (mem.members_id % 3)
WHERE p.document_number BETWEEN '90000001' AND '90000018';


-- ════════════════════════════════════════════════════════════════════════════
-- 4. PAYMENTS  (transactions history — inscription + monthly, Jun→Aug)
-- ════════════════════════════════════════════════════════════════════════════

-- 4.1 inscription fee — one per membership, always APPROVED
INSERT INTO app.payments (
    membership_id, amount, currency, payment_method, reference_number,
    payment_date, received_at, inscription, applied_period,
    status, reviewed_by, reviewed_at, is_active, created_at
)
SELECT
    ms.memberships_id,
    ms.inscription_fee,
    'USD',
    (ARRAY['CASH','ZELLE','PAGO_MOVIL','BANK_TRANSFER'])[1 + (ms.memberships_id % 4)],
    'INS-' || lpad(ms.memberships_id::text, 4, '0'),
    ms.enrolled_at,
    ms.enrolled_at::timestamptz + INTERVAL '2 hours',
    TRUE,
    NULL,
    'APPROVED',
    3,
    ms.enrolled_at::timestamptz + INTERVAL '1 day',
    TRUE,
    ms.enrolled_at::timestamptz
FROM app.memberships ms
JOIN app.members mem ON mem.members_id = ms.member_id
JOIN app.persons p   ON p.persons_id = mem.person_id
WHERE p.document_number BETWEEN '90000001' AND '90000018';

-- 4.2 recurring monthly fee — from the enrollment month through August 2026
INSERT INTO app.payments (
    membership_id, amount, currency, payment_method, reference_number,
    payment_date, received_at, inscription, applied_period,
    status, reviewed_by, reviewed_at, review_reason, is_active, created_at
)
SELECT
    ms.memberships_id,
    ms.monthly_fee,
    'USD',
    (ARRAY['CASH','ZELLE','PAGO_MOVIL','BANK_TRANSFER'])
        [1 + ((ms.memberships_id + EXTRACT(month FROM mm.mo_date)::int) % 4)],
    'MON-' || lpad(ms.memberships_id::text, 4, '0') || '-' || to_char(mm.mo_date, 'YYYYMM'),
    pay_date.d,
    pay_date.d::timestamptz,
    FALSE,
    mm.mo_date,
    st.status,
    CASE WHEN st.status = 'PENDING' THEN NULL ELSE 3 END,
    CASE WHEN st.status = 'PENDING' THEN NULL ELSE pay_date.d::timestamptz + INTERVAL '1 day' END,
    CASE WHEN st.status = 'REJECTED'
         THEN 'Comprobante ilegible — se solicitó reenvío al afiliado.' END,
    TRUE,
    pay_date.d::timestamptz
FROM app.memberships ms
JOIN app.members mem ON mem.members_id = ms.member_id
JOIN app.persons p   ON p.persons_id = mem.person_id
CROSS JOIN LATERAL (
    SELECT gs::date AS mo_date
    FROM generate_series(date_trunc('month', ms.enrolled_at)::date,
                         DATE '2026-08-01',
                         INTERVAL '1 month') AS gs
) mm
CROSS JOIN LATERAL (
    SELECT GREATEST(mm.mo_date + 3, ms.enrolled_at + 1) AS d
) pay_date
CROSS JOIN LATERAL (
    SELECT CASE
        WHEN mm.mo_date < DATE '2026-08-01'      THEN 'APPROVED'
        WHEN ms.memberships_id % 7  = 0          THEN 'PENDING'
        WHEN ms.memberships_id % 11 = 0          THEN 'REJECTED'
        ELSE 'APPROVED'
    END AS status
) st
WHERE p.document_number BETWEEN '90000001' AND '90000018';


-- ════════════════════════════════════════════════════════════════════════════
-- 5. COMMISSIONS  (one per APPROVED payment; 10% of the paid amount)
-- ════════════════════════════════════════════════════════════════════════════

INSERT INTO app.commissions (
    promoter_id, payment_id, member_id, amount, currency,
    calculation_basis, commission_pct, tier_name_snapshot,
    applies_to, period_strategy, period_start, period_end, earned_at,
    status, paid_at, payout_reference, is_active
)
SELECT
    mem.promoter_id,
    pay.payments_id,
    mem.members_id,
    ROUND(pay.amount * 0.10, 2),
    'USD',
    pay.amount,
    10.00,
    'Base 10% (seed)',
    CASE WHEN pay.inscription THEN 'INSCRIPTION' ELSE 'MONTHLY' END,
    'MONTHLY',
    date_trunc('month', pay.reviewed_at)::date,
    (date_trunc('month', pay.reviewed_at) + INTERVAL '1 month - 1 day')::date,
    pay.reviewed_at,
    CASE WHEN date_trunc('month', pay.reviewed_at)::date < DATE '2026-08-01'
         THEN 'PAID' ELSE 'PENDING' END,
    CASE WHEN date_trunc('month', pay.reviewed_at)::date < DATE '2026-08-01'
         THEN (date_trunc('month', pay.reviewed_at) + INTERVAL '1 month 3 days')::timestamptz END,
    CASE WHEN date_trunc('month', pay.reviewed_at)::date < DATE '2026-08-01'
         THEN 'BATCH-' || to_char(pay.reviewed_at, 'YYYYMM') END,
    TRUE
FROM app.payments pay
JOIN app.memberships ms ON ms.memberships_id = pay.membership_id
JOIN app.members mem    ON mem.members_id = ms.member_id
JOIN app.persons p      ON p.persons_id = mem.person_id
WHERE p.document_number BETWEEN '90000001' AND '90000018'
  AND pay.status = 'APPROVED'
  AND mem.promoter_id IS NOT NULL;


-- ════════════════════════════════════════════════════════════════════════════
-- 6. DERIVED ROLL-UPS
-- ════════════════════════════════════════════════════════════════════════════

-- 6.1 membership paid-through / next due date from the APPROVED monthly rows
UPDATE app.memberships m
SET last_paid_through = sub.lpt,
    next_due_date     = (sub.lpt + INTERVAL '1 day')::date
FROM (
    SELECT pay.membership_id,
           (max(pay.applied_period) + INTERVAL '1 month - 1 day')::date AS lpt
    FROM app.payments pay
    WHERE pay.status = 'APPROVED' AND pay.inscription = FALSE
    GROUP BY pay.membership_id
) sub
WHERE sub.membership_id = m.memberships_id;

-- 6.2 promoter dashboard snapshot counters
UPDATE app.promoters pr
SET total_referrals       = COALESCE(r.cnt, 0),
    total_commission_paid = COALESCE(c.paid, 0)
FROM (SELECT p2.promoters_id FROM app.promoters p2 WHERE p2.referral_code LIKE 'PROMOSEED%') seed
LEFT JOIN (
    SELECT promoter_id, count(*) AS cnt
    FROM app.members GROUP BY promoter_id
) r ON r.promoter_id = seed.promoters_id
LEFT JOIN (
    SELECT promoter_id, sum(amount) AS paid
    FROM app.commissions WHERE status = 'PAID' GROUP BY promoter_id
) c ON c.promoter_id = seed.promoters_id
WHERE pr.promoters_id = seed.promoters_id;


-- ─── Summary ───────────────────────────────────────────────────────────────
DO $$
DECLARE
    v_prom int; v_prom_inact int; v_ally int; v_ally_inact int;
    v_mem int; v_ms int; v_pay int; v_pay_appr int; v_pay_pend int;
    v_pay_rej int; v_com int; v_com_paid int;
BEGIN
    SELECT count(*) FILTER (WHERE is_active),
           count(*) FILTER (WHERE NOT is_active)
      INTO v_prom, v_prom_inact
      FROM app.promoters WHERE referral_code LIKE 'PROMOSEED%';
    SELECT count(*) FILTER (WHERE is_active),
           count(*) FILTER (WHERE NOT is_active)
      INTO v_ally, v_ally_inact
      FROM app.allies WHERE email LIKE '%@seed.optibienestar.test';
    SELECT count(*) INTO v_mem FROM app.members mem
      JOIN app.persons p ON p.persons_id = mem.person_id
      WHERE p.document_number BETWEEN '90000001' AND '90000018';
    SELECT count(*) INTO v_ms FROM app.memberships ms
      JOIN app.members mem ON mem.members_id = ms.member_id
      JOIN app.persons p ON p.persons_id = mem.person_id
      WHERE p.document_number BETWEEN '90000001' AND '90000018';
    SELECT count(*),
           count(*) FILTER (WHERE pay.status = 'APPROVED'),
           count(*) FILTER (WHERE pay.status = 'PENDING'),
           count(*) FILTER (WHERE pay.status = 'REJECTED')
      INTO v_pay, v_pay_appr, v_pay_pend, v_pay_rej
      FROM app.payments pay
      JOIN app.memberships ms ON ms.memberships_id = pay.membership_id
      JOIN app.members mem ON mem.members_id = ms.member_id
      JOIN app.persons p ON p.persons_id = mem.person_id
      WHERE p.document_number BETWEEN '90000001' AND '90000018';
    SELECT count(*), count(*) FILTER (WHERE c.status = 'PAID')
      INTO v_com, v_com_paid
      FROM app.commissions c
      JOIN app.members mem ON mem.members_id = c.member_id
      JOIN app.persons p ON p.persons_id = mem.person_id
      WHERE p.document_number BETWEEN '90000001' AND '90000018';

    RAISE NOTICE '── dev-seed-network-history summary ──────────────────────';
    RAISE NOTICE 'promoters : % active + % inactive', v_prom, v_prom_inact;
    RAISE NOTICE 'allies    : % active + % inactive', v_ally, v_ally_inact;
    RAISE NOTICE 'members   : %   memberships: %', v_mem, v_ms;
    RAISE NOTICE 'payments  : % total (APPROVED % / PENDING % / REJECTED %)',
        v_pay, v_pay_appr, v_pay_pend, v_pay_rej;
    RAISE NOTICE 'commissions: % total (PAID %)', v_com, v_com_paid;
END $$;

COMMIT;
