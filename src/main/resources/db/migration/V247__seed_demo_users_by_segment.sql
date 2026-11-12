SET search_path TO app, public;

-- ============================================================
-- V247: one generic demo user per segment, with complete data, for demo
-- videos and walkthroughs.
--
-- GUARDED: does nothing unless the Flyway placeholder demo_seed_enabled is
-- 'true' (env DEMO_SEED_ENABLED). It is 'false' in application.properties,
-- so production never gets these rows. NOTE: Flyway records this version even
-- when it is a no-op, so an environment that wants the demo users must have
-- DEMO_SEED_ENABLED=true the first time it migrates past V247.
--
-- Password of each user = the local part of its e-mail (asesor@... → asesor,
-- aliado.comercial@... → aliado.comercial), hashed with app.bcrypt_hash (V20).
-- They never expire. Known on purpose (demo videos); that is why the seed is
-- opt-in and scripts/demo-cleanup.sql removes the users before the launch.
--
-- Idempotent: if the demo administrator e-mail already exists, the whole
-- seed is skipped.
--
-- Every row is tagged so scripts/demo-cleanup.sql can inactivate or delete it
-- before the production launch:
--   - e-mails  *@optibienestar360.centroopticovicente.com (users, persons,
--     allies, promoters). Cloudflare Email Routing forwards them to
--     info.optibienestar360@gmail.com (hub: deployment/cloudflare/).
--   - person documents V-96000001 .. V-96000013
--   - promoter referral code DEMO-ASESOR, product codes DEMO-*,
--     payment references DEMO-*.
--
-- Users (default role → what the portal shows):
--   sistema@           SYSTEM          roles and permissions
--   administrador@     ADMINISTRADOR   full back office; reviewer of the demo payments
--   operador@          OPERADOR        operations
--   operador.medico@   OPERADOR_MEDICO operations with medical-record access
--   asesor@            ASESOR          portfolio of 3 members, commissions, payout account
--   afiliado@          AFILIADO        FAMILIAR membership ACTIVE, 2 beneficiaries,
--                                      approved payments, medical record; sold by asesor@
--   aliado.comercial@  ALIADO          B2B optical store, published, agreement + 3 offers
--   aliado.medico@     ALIADO          B2P MEDICAL ophthalmologist, license, 2 offers
--   aliado.bienestar@  ALIADO          B2P WELLNESS psychologist, 2 offers
--
-- Dates are relative to the migration date: the member enrolled two months
-- ago and is paid four months ahead, so the membership stays ACTIVE for the
-- next few months of recordings.
-- ============================================================

-- ---------- session-local helpers (dropped with the session) ----------

CREATE FUNCTION pg_temp.demo_person(p_doc text, p_first text, p_last text, p_email text, p_phone text,
                                    p_birth date, p_city bigint)
RETURNS bigint LANGUAGE plpgsql AS $f$
DECLARE v_id bigint;
BEGIN
    INSERT INTO persons (first_name, last_name, document_type, document_number, email, phone, birth_date, city_id)
    VALUES (p_first, p_last, 'V', p_doc, p_email, p_phone, p_birth, p_city)
    ON CONFLICT (document_type, document_number) DO NOTHING;
    SELECT persons_id INTO v_id FROM persons WHERE document_type = 'V' AND document_number = p_doc;
    RETURN v_id;
END;
$f$;

-- Accepts the current unscoped version(s) of a contract type. Two versions can
-- share the latest valid_from (the seeds do), so all of them are accepted.
CREATE FUNCTION pg_temp.demo_accept(p_user bigint, p_type text)
RETURNS void LANGUAGE plpgsql AS $f$
BEGIN
    INSERT INTO terms_acceptances (user_id, terms_versions_id, channel)
    SELECT p_user, tv.terms_versions_id, 'ADMIN'
      FROM terms_versions tv
     WHERE tv.term_type = p_type
       AND tv.ally_type_id IS NULL AND tv.segment IS NULL AND tv.ally_id IS NULL
       AND tv.valid_from = (SELECT max(valid_from) FROM terms_versions
                             WHERE term_type = p_type AND ally_type_id IS NULL AND segment IS NULL
                               AND ally_id IS NULL AND valid_from <= now())
    ON CONFLICT DO NOTHING;
END;
$f$;

-- The password is the local part of the e-mail (before the '@').
CREATE FUNCTION pg_temp.demo_user(p_person bigint, p_email text, p_role text)
RETURNS bigint LANGUAGE plpgsql AS $f$
DECLARE v_role bigint; v_id bigint; v_hash text := app.bcrypt_hash(split_part(p_email, '@', 1), 12);
BEGIN
    SELECT roles_id INTO STRICT v_role FROM roles WHERE name = p_role;
    INSERT INTO users (email, password_hash, default_role_id, password_never_expires, status, person_id)
    VALUES (p_email, v_hash, v_role, TRUE, 'ACTIVE', p_person)
    RETURNING users_id INTO v_id;
    INSERT INTO user_roles (user_id, role_id) VALUES (v_id, v_role);
    INSERT INTO user_password_history (user_id, password_hash) VALUES (v_id, v_hash);
    -- Every user is asked for the member agreement on first login; accept it
    -- up front so the videos open on the real home screen.
    PERFORM pg_temp.demo_accept(v_id, 'AFILIADO');
    RETURN v_id;
END;
$f$;

-- An approved incoming payment with one approved Pago Móvil line.
CREATE FUNCTION pg_temp.demo_payment(p_membership bigint, p_person bigint, p_payer bigint, p_promoter bigint,
                                     p_category text, p_amount numeric, p_currency bigint, p_ts timestamptz,
                                     p_inscription boolean, p_applied date, p_through date,
                                     p_reviewer bigint, p_ref text)
RETURNS bigint LANGUAGE plpgsql AS $f$
DECLARE v_id bigint; v_ts timestamptz := least(p_ts, now()); v_rev timestamptz;
BEGIN
    v_rev := least(v_ts + interval '2 hours', now());
    INSERT INTO payments (membership_id, payer_user_id, amount, payment_date, received_at, inscription,
                          applied_period, coverage_through_period, reviewed_by, reviewed_at, status,
                          currency_id, direction, payment_type_id, person_id, promoter_id, admin_notes)
    VALUES (p_membership, p_payer, p_amount, v_ts, v_ts, p_inscription,
            p_applied, p_through, p_reviewer, v_rev, 'APPROVED',
            p_currency, 'IN', (SELECT payment_categories_id FROM payment_categories WHERE code = p_category),
            p_person, p_promoter, 'Pago de demostración')
    RETURNING payments_id INTO v_id;
    INSERT INTO payment_lines (payment_id, payment_type_id, bank_id, amount, currency_id, reference_number,
                               phone, status, reviewed_by, reviewed_at)
    VALUES (v_id, (SELECT payment_methods_id FROM payment_methods WHERE code = 'PAGO_MOVIL'),
            (SELECT banks_id FROM banks WHERE code = '0102'), p_amount, p_currency, p_ref,
            '04140000006', 'APPROVED', p_reviewer, v_rev);
    RETURN v_id;
END;
$f$;

-- A commission for one payment. Its period is the calendar month of the
-- payment review (the moment it was earned).
CREATE FUNCTION pg_temp.demo_commission(p_promoter bigint, p_payment bigint, p_member bigint, p_basis numeric,
                                        p_pct numeric, p_applies text, p_status text, p_approver bigint,
                                        p_currency bigint)
RETURNS void LANGUAGE plpgsql AS $f$
DECLARE v_earned timestamptz; v_day date; v_start date;
BEGIN
    SELECT reviewed_at INTO v_earned FROM payments WHERE payments_id = p_payment;
    v_day := v_earned::date;
    v_start := date_trunc('month', v_day)::date;
    INSERT INTO commissions (promoter_id, payment_id, member_id, amount, calculation_basis, commission_pct,
                             applies_to, period_strategy, period_start, period_end, earned_at, status,
                             currency_id, approved_by_user_id, approved_at, paid_at, payout_reference)
    VALUES (p_promoter, p_payment, p_member, round(p_basis * p_pct / 100, 2), p_basis, p_pct,
            p_applies, 'MONTHLY', v_start, (v_start + interval '1 month' - interval '1 day')::date, v_earned,
            p_status, p_currency,
            CASE WHEN p_status IN ('APPROVED', 'PAID') THEN p_approver END,
            CASE WHEN p_status IN ('APPROVED', 'PAID') THEN v_earned END,
            CASE WHEN p_status = 'PAID' THEN least(v_earned + interval '1 day', now()) END,
            CASE WHEN p_status = 'PAID' THEN 'DEMO-PAGO-COMISION' END);
END;
$f$;

-- Monthly charges from p_from to p_to (first days of month), covered by p_payment.
CREATE FUNCTION pg_temp.demo_charges(p_membership bigint, p_from date, p_to date, p_payment bigint,
                                     p_amount numeric, p_currency bigint, p_day int)
RETURNS void LANGUAGE plpgsql AS $f$
DECLARE v_m date := p_from;
BEGIN
    WHILE v_m <= p_to LOOP
        INSERT INTO membership_charges (membership_id, period_start, period_end, due_date, amount, gross_amount,
                                        currency_id, covered_by_payment_id, status)
        VALUES (p_membership, v_m, (v_m + interval '1 month' - interval '1 day')::date, v_m + (p_day - 1),
                p_amount, p_amount, p_currency, p_payment, 'COVERED')
        ON CONFLICT (membership_id, period_start) DO NOTHING;
        v_m := (v_m + interval '1 month')::date;
    END LOOP;
END;
$f$;

-- An approved, published offer on an exclusive product of the ally.
CREATE FUNCTION pg_temp.demo_offer(p_ally bigint, p_code text, p_name text, p_desc text, p_type text,
                                   p_category text, p_price numeric, p_currency bigint, p_pct numeric,
                                   p_flat numeric, p_appointment boolean, p_reviewer bigint, p_exclusions text)
RETURNS bigint LANGUAGE plpgsql AS $f$
DECLARE v_product bigint; v_offer bigint;
BEGIN
    INSERT INTO products (code, name, description, product_type, product_category_id, owner_ally_id,
                          requires_appointment, is_published, published_at)
    VALUES (p_code, p_name, p_desc, p_type,
            (SELECT product_categories_id FROM product_categories WHERE code = p_category),
            p_ally, p_appointment, TRUE, now())
    RETURNING products_id INTO v_product;
    INSERT INTO ally_products (ally_id, product_id, price_amount, price_currency_id, discount_type,
                               discount_pct, discount_amount, exclusions, review_status, reviewed_by,
                               reviewed_at, is_published, published_at)
    VALUES (p_ally, v_product, p_price, p_currency,
            CASE WHEN p_flat IS NULL THEN 'PERCENTAGE' ELSE 'FLAT' END,
            CASE WHEN p_flat IS NULL THEN p_pct END, p_flat, p_exclusions,
            'APPROVED', p_reviewer, now(), TRUE, now())
    RETURNING ally_products_id INTO v_offer;
    RETURN v_offer;
END;
$f$;

-- Monday-Friday 08:00-12:00 and 14:00-18:00; Saturday 08:00-12:00 when asked.
CREATE FUNCTION pg_temp.demo_hours(p_ally bigint, p_saturday boolean)
RETURNS void LANGUAGE plpgsql AS $f$
DECLARE d int;
BEGIN
    FOR d IN 1..5 LOOP
        INSERT INTO ally_opening_hours (ally_id, day_of_week, opens_at, closes_at)
        VALUES (p_ally, d, '08:00', '12:00'), (p_ally, d, '14:00', '18:00')
        ON CONFLICT DO NOTHING;
    END LOOP;
    IF p_saturday THEN
        INSERT INTO ally_opening_hours (ally_id, day_of_week, opens_at, closes_at)
        VALUES (p_ally, 6, '08:00', '12:00') ON CONFLICT DO NOTHING;
    END IF;
END;
$f$;

-- An ACTIVE agreement under the current ALIADO template of the ally's segment,
-- accepted by the ally owner.
CREATE FUNCTION pg_temp.demo_agreement(p_ally bigint, p_type text, p_segment text, p_start date, p_owner bigint)
RETURNS void LANGUAGE plpgsql AS $f$
DECLARE v_version bigint; v_agreement bigint;
BEGIN
    SELECT terms_versions_id INTO v_version
      FROM terms_versions
     WHERE term_type = 'ALIADO' AND ally_type_id IS NULL AND ally_id IS NULL
       AND (segment = p_segment OR segment IS NULL) AND valid_from <= now()
     ORDER BY (segment IS NULL), valid_from DESC, terms_versions_id DESC
     LIMIT 1;
    INSERT INTO ally_agreements (ally_id, agreement_type, start_date, status, terms_version_id)
    VALUES (p_ally, p_type, p_start, 'ACTIVE', v_version)
    RETURNING ally_agreements_id INTO v_agreement;
    IF v_version IS NOT NULL THEN
        INSERT INTO terms_acceptances (user_id, terms_versions_id, ally_agreement_id, channel)
        VALUES (p_owner, v_version, v_agreement, 'ADMIN')
        ON CONFLICT DO NOTHING;
    END IF;
END;
$f$;

-- ---------- the seed ----------

DO $$
DECLARE
    c_domain   CONSTANT text := '@optibienestar360.centroopticovicente.com';
    v_city     bigint;
    v_usd      bigint;
    v_plan_fam plans%ROWTYPE;
    v_plan_ind plans%ROWTYPE;
    v_rank     bigint;
    -- dates
    v_m0       date := date_trunc('month', current_date)::date;   -- first day of this month
    v_enrolled date;                                             -- day 5, two months ago
    v_ts       timestamptz;
    -- people / users
    v_p bigint; v_u_admin bigint; v_member_x bigint;
    v_p_asesor bigint; v_u_asesor bigint; v_promoter bigint;
    v_p_afil bigint; v_u_afil bigint; v_member bigint; v_membership bigint;
    v_p_ben bigint;
    v_p_ally bigint; v_u_ally bigint; v_ally bigint; v_ally_user bigint; v_offer bigint;
    v_pay_ins bigint; v_pay_m2 bigint; v_pay_m1 bigint; v_pay_adv bigint;
    v_account bigint;
    v_admin_uuid uuid;
    i int;
BEGIN
    IF '${demo_seed_enabled}' <> 'true' THEN
        RAISE NOTICE 'V247: demo seed disabled (demo_seed_enabled <> true), nothing inserted';
        RETURN;
    END IF;
    IF EXISTS (SELECT 1 FROM users WHERE email = 'administrador' || c_domain) THEN
        RAISE NOTICE 'V247: demo users already present, skipped';
        RETURN;
    END IF;
    v_city     := (SELECT cities_id FROM cities WHERE name = 'Acarigua' ORDER BY cities_id LIMIT 1);
    v_usd      := (SELECT currencies_id FROM currencies WHERE code = 'USD');
    v_rank     := (SELECT promoter_ranks_id FROM promoter_ranks WHERE code = 'ASESOR');
    -- Seeded plans by code; if an environment renamed them, fall back to an
    -- active plan of the same type, then to any active plan.
    SELECT * INTO v_plan_fam FROM plans
     ORDER BY (code = 'FAMILIAR') DESC, (type = 'FAMILIAR' AND is_active) DESC, is_active DESC, plans_id
     LIMIT 1;
    SELECT * INTO v_plan_ind FROM plans
     ORDER BY (code = 'INDIVIDUAL') DESC, (type = 'INDIVIDUAL' AND is_active) DESC, is_active DESC, plans_id
     LIMIT 1;
    IF v_plan_fam.plans_id IS NULL OR v_usd IS NULL OR v_rank IS NULL THEN
        RAISE EXCEPTION 'V247: missing catalogs (plans, USD currency or ASESOR rank)';
    END IF;
    v_enrolled := (v_m0 - interval '2 months')::date + 4;

    -- ---- internal staff ----
    v_p := pg_temp.demo_person('96000001', 'Sistema', 'Demo', 'sistema' || c_domain, '04140000001', NULL, v_city);
    PERFORM pg_temp.demo_user(v_p, 'sistema' || c_domain, 'SYSTEM');

    v_p := pg_temp.demo_person('96000002', 'Administrador', 'Demo', 'administrador' || c_domain, '04140000002', NULL, v_city);
    v_u_admin := pg_temp.demo_user(v_p, 'administrador' || c_domain, 'ADMINISTRADOR');
    SELECT uuid INTO v_admin_uuid FROM users WHERE users_id = v_u_admin;

    v_p := pg_temp.demo_person('96000003', 'Operador', 'Demo', 'operador' || c_domain, '04140000003', NULL, v_city);
    PERFORM pg_temp.demo_user(v_p, 'operador' || c_domain, 'OPERADOR');

    v_p := pg_temp.demo_person('96000004', 'Operador Médico', 'Demo', 'operador.medico' || c_domain, '04140000004', NULL, v_city);
    PERFORM pg_temp.demo_user(v_p, 'operador.medico' || c_domain, 'OPERADOR_MEDICO');

    -- ---- advisor ----
    v_p_asesor := pg_temp.demo_person('96000005', 'Asesor', 'Demo', 'asesor' || c_domain, '04140000005', '1990-03-15', v_city);
    v_u_asesor := pg_temp.demo_user(v_p_asesor, 'asesor' || c_domain, 'ASESOR');
    INSERT INTO promoters (user_id, person_id, display_name, description, referral_code, email, phone,
                           rank_id, status, joined_at, assigned_zone, education_level, profession,
                           years_of_experience, total_referrals)
    VALUES (v_u_asesor, v_p_asesor, 'Asesor Demo', 'Asesor comercial de demostración', 'DEMO-ASESOR',
            'asesor' || c_domain, '04140000005', v_rank, 'ACTIVE', v_enrolled - 30,
            'Portuguesa (Acarigua - Araure)', 'UNIVERSITY', 'Administración', 3, 3)
    RETURNING promoters_id INTO v_promoter;
    PERFORM pg_temp.demo_accept(v_u_asesor, 'ASESOR');

    INSERT INTO promoter_payout_accounts (promoter_id, label, bank_id, account_type, account_number,
                                          holder_name, holder_document_type, holder_document_number,
                                          phone, is_default)
    VALUES (v_promoter, 'Banco de Venezuela (demo)', (SELECT banks_id FROM banks WHERE code = '0102'),
            'CORRIENTE', '01020000000000960005', 'Asesor Demo', 'V', '96000005', '04140000005', TRUE)
    RETURNING promoter_payout_accounts_id INTO v_account;
    INSERT INTO promoter_payout_account_payment_methods (promoter_payout_account_id, payment_method_id)
    SELECT v_account, payment_methods_id FROM payment_methods WHERE code IN ('PAGO_MOVIL', 'BANK_TRANSFER');

    -- ---- member (holder), sold by the advisor ----
    v_p_afil := pg_temp.demo_person('96000006', 'Afiliado', 'Demo', 'afiliado' || c_domain, '04140000006', '1985-07-20', v_city);
    v_u_afil := pg_temp.demo_user(v_p_afil, 'afiliado' || c_domain, 'AFILIADO');
    v_ts := (v_enrolled + time '10:00') AT TIME ZONE 'America/Caracas';

    INSERT INTO members (person_id, enrolled_at, promoter_id, confirmed_at, notes)
    VALUES (v_p_afil, v_enrolled, v_promoter, least(v_ts, now()), 'Afiliado de demostración')
    RETURNING members_id INTO v_member;

    INSERT INTO memberships (member_id, plan_id, enrolled_at, next_due_date, last_paid_through,
                             inscription_fee, monthly_fee, grace_period_days, status, billing_start_day,
                             currency_id, reinscription_required, term_mode, term_fixed_month,
                             term_fixed_day, term_period_months, term_started_at, expires_at)
    VALUES (v_member, v_plan_fam.plans_id, v_enrolled,
            (v_m0 + interval '4 months')::date + 4,                       -- next due: day 5, four months ahead
            (v_m0 + interval '4 months' - interval '1 day')::date,        -- paid through the end of month +3
            v_plan_fam.inscription_fee, v_plan_fam.monthly_fee, v_plan_fam.grace_period_days, 'ACTIVE', 5,
            v_plan_fam.currency_id, v_plan_fam.reinscription_required, v_plan_fam.term_mode,
            v_plan_fam.term_fixed_month, v_plan_fam.term_fixed_day, v_plan_fam.term_period_months,
            CASE WHEN v_plan_fam.reinscription_required THEN v_enrolled END,
            CASE WHEN v_plan_fam.reinscription_required THEN (v_enrolled + interval '1 year')::date END)
    RETURNING memberships_id INTO v_membership;

    v_p_ben := pg_temp.demo_person('96000010', 'Cónyuge', 'Demo', NULL, '04140000010', '1987-11-02', v_city);
    INSERT INTO beneficiaries (member_id, person_id, relationship, slot_type) VALUES (v_member, v_p_ben, 'SPOUSE', 'BASE');
    v_p_ben := pg_temp.demo_person('96000011', 'Hijo', 'Demo', NULL, NULL, '2015-05-10', v_city);
    INSERT INTO beneficiaries (member_id, person_id, relationship, slot_type) VALUES (v_member, v_p_ben, 'CHILD', 'BASE');

    INSERT INTO medical_records (person_id, blood_type, allergies, chronic_conditions, current_medications,
                                 emergency_contact_name, emergency_contact_phone, emergency_contact_relationship, notes)
    VALUES (v_p_afil, 'O+', 'Penicilina', 'Miopía leve', 'Ninguno', 'Cónyuge Demo', '04140000010', 'SPOUSE',
            'Historia de demostración');

    -- Payments: inscription + month -2, month -1, and one advance payment for months 0..+3.
    v_pay_ins := pg_temp.demo_payment(v_membership, v_p_afil, v_u_afil, v_promoter, 'INSCRIPTION_FEE',
                                      v_plan_fam.inscription_fee, v_plan_fam.currency_id, v_ts, TRUE, NULL, NULL,
                                      v_u_admin, 'DEMO-000101');
    v_pay_m2 := pg_temp.demo_payment(v_membership, v_p_afil, v_u_afil, v_promoter, 'MEMBERSHIP_FEE',
                                     v_plan_fam.monthly_fee, v_plan_fam.currency_id, v_ts + interval '5 minutes',
                                     FALSE, (v_m0 - interval '2 months')::date, NULL, v_u_admin, 'DEMO-000102');
    v_pay_m1 := pg_temp.demo_payment(v_membership, v_p_afil, v_u_afil, v_promoter, 'MEMBERSHIP_FEE',
                                     v_plan_fam.monthly_fee, v_plan_fam.currency_id,
                                     (((v_m0 - interval '1 month')::date + 4) + time '10:00') AT TIME ZONE 'America/Caracas',
                                     FALSE, (v_m0 - interval '1 month')::date, NULL, v_u_admin, 'DEMO-000103');
    v_pay_adv := pg_temp.demo_payment(v_membership, v_p_afil, v_u_afil, v_promoter, 'MEMBERSHIP_FEE',
                                      v_plan_fam.monthly_fee * 4, v_plan_fam.currency_id,
                                      ((least(v_m0 + 4, current_date)) + time '09:00') AT TIME ZONE 'America/Caracas',
                                      FALSE, v_m0, (v_m0 + interval '3 months')::date, v_u_admin, 'DEMO-000104');

    PERFORM pg_temp.demo_charges(v_membership, (v_m0 - interval '2 months')::date, (v_m0 - interval '2 months')::date,
                                 v_pay_m2, v_plan_fam.monthly_fee, v_plan_fam.currency_id, 5);
    PERFORM pg_temp.demo_charges(v_membership, (v_m0 - interval '1 month')::date, (v_m0 - interval '1 month')::date,
                                 v_pay_m1, v_plan_fam.monthly_fee, v_plan_fam.currency_id, 5);
    PERFORM pg_temp.demo_charges(v_membership, v_m0, (v_m0 + interval '3 months')::date,
                                 v_pay_adv, v_plan_fam.monthly_fee, v_plan_fam.currency_id, 5);

    -- Commissions of the advisor on this member: inscription already paid out,
    -- first collection approved, the advance collection pending.
    PERFORM pg_temp.demo_commission(v_promoter, v_pay_ins, v_member, v_plan_fam.inscription_fee, 25, 'INSCRIPTION',
                                    'PAID', v_u_admin, v_plan_fam.currency_id);
    PERFORM pg_temp.demo_commission(v_promoter, v_pay_m1, v_member, v_plan_fam.monthly_fee, 35, 'MONTHLY',
                                    'APPROVED', v_u_admin, v_plan_fam.currency_id);
    PERFORM pg_temp.demo_commission(v_promoter, v_pay_adv, v_member, v_plan_fam.monthly_fee * 4, 35, 'MONTHLY',
                                    'PENDING', v_u_admin, v_plan_fam.currency_id);

    -- Two more members in the advisor's portfolio (no login), Plan Único,
    -- enrolled last month and paid through next month.
    FOR i IN 1..2 LOOP
        v_p := pg_temp.demo_person('9600001' || (i + 1), CASE i WHEN 1 THEN 'Cliente Uno' ELSE 'Cliente Dos' END,
                                   'Demo', NULL, '0414000001' || (i + 1), ('1992-01-0' || i)::date, v_city);
        v_ts := (((v_m0 - interval '1 month')::date + 9 + i) + time '11:00') AT TIME ZONE 'America/Caracas';
        INSERT INTO members (person_id, enrolled_at, promoter_id, confirmed_at, notes)
        VALUES (v_p, (v_m0 - interval '1 month')::date + 9 + i, v_promoter, v_ts, 'Afiliado de demostración')
        RETURNING members_id INTO v_member_x;
        INSERT INTO memberships (member_id, plan_id, enrolled_at, next_due_date, last_paid_through,
                                 inscription_fee, monthly_fee, grace_period_days, status, billing_start_day,
                                 currency_id, reinscription_required, term_mode, term_fixed_month,
                                 term_fixed_day, term_period_months, term_started_at, expires_at)
        VALUES (v_member_x, v_plan_ind.plans_id, (v_m0 - interval '1 month')::date + 9 + i,
                (v_m0 + interval '2 months')::date + 9 + i,
                (v_m0 + interval '2 months' - interval '1 day')::date,
                v_plan_ind.inscription_fee, v_plan_ind.monthly_fee, v_plan_ind.grace_period_days, 'ACTIVE', 10 + i,
                v_plan_ind.currency_id, v_plan_ind.reinscription_required, v_plan_ind.term_mode,
                v_plan_ind.term_fixed_month, v_plan_ind.term_fixed_day, v_plan_ind.term_period_months,
                CASE WHEN v_plan_ind.reinscription_required THEN (v_m0 - interval '1 month')::date + 9 + i END,
                CASE WHEN v_plan_ind.reinscription_required THEN ((v_m0 - interval '1 month')::date + 9 + i + interval '1 year')::date END)
        RETURNING memberships_id INTO v_membership;
        v_pay_ins := pg_temp.demo_payment(v_membership, v_p, NULL, v_promoter, 'INSCRIPTION_FEE',
                                          v_plan_ind.inscription_fee, v_plan_ind.currency_id, v_ts, TRUE, NULL, NULL,
                                          v_u_admin, 'DEMO-00020' || i);
        v_pay_adv := pg_temp.demo_payment(v_membership, v_p, NULL, v_promoter, 'MEMBERSHIP_FEE',
                                          v_plan_ind.monthly_fee * 3, v_plan_ind.currency_id, v_ts + interval '5 minutes',
                                          FALSE, (v_m0 - interval '1 month')::date, (v_m0 + interval '1 month')::date,
                                          v_u_admin, 'DEMO-00030' || i);
        PERFORM pg_temp.demo_charges(v_membership, (v_m0 - interval '1 month')::date, (v_m0 + interval '1 month')::date,
                                     v_pay_adv, v_plan_ind.monthly_fee, v_plan_ind.currency_id, 10 + i);
        PERFORM pg_temp.demo_commission(v_promoter, v_pay_ins, v_member_x, v_plan_ind.inscription_fee, 25, 'INSCRIPTION',
                                        'APPROVED', v_u_admin, v_plan_ind.currency_id);
    END LOOP;

    -- ---- commercial ally (B2B): optical store captured by the advisor ----
    v_p_ally := pg_temp.demo_person('96000007', 'Aliado Comercial', 'Demo', 'aliado.comercial' || c_domain, '04140000007', NULL, v_city);
    v_u_ally := pg_temp.demo_user(v_p_ally, 'aliado.comercial' || c_domain, 'ALIADO');
    INSERT INTO allies (name, trade_name, tax_document_type, tax_document_number, email, phone, whatsapp,
                        address, city_id, description, joined_at, segment, person_id, representative_position,
                        commercial_zone, founded_year, cash_registers_count, branches_count, has_pos,
                        captured_by_promoter_id, registration_status, registration_channel,
                        approved_at, approved_by, registration_submitted_at, is_active, is_published, published_at)
    VALUES ('Óptica Demo, C.A.', 'Óptica Demo', 'J', '960000071', 'aliado.comercial' || c_domain,
            '02550000007', '04140000007', 'Av. Libertador, Centro Comercial Demo, local 7, Acarigua', v_city,
            'Óptica de demostración: exámenes visuales, lentes y monturas con tarifa preferencial para afiliados.',
            v_enrolled - 15, 'B2B', v_p_ally, 'Gerente general', 'Portuguesa', 2015, 2, 1, TRUE,
            v_promoter, 'APPROVED', 'ADVISOR', now(), v_admin_uuid, now(), TRUE, TRUE, now())
    RETURNING allies_id INTO v_ally;
    INSERT INTO ally_ally_types (ally_id, ally_type_id) SELECT v_ally, ally_types_id FROM ally_types WHERE code = 'CENTRO_OPTICO';
    INSERT INTO ally_users (ally_id, user_id, ally_role, is_primary, joined_at)
    VALUES (v_ally, v_u_ally, 'OWNER', TRUE, v_enrolled - 15)
    RETURNING ally_users_id INTO v_ally_user;
    PERFORM pg_temp.demo_agreement(v_ally, 'COMMERCIAL', 'B2B', v_enrolled - 15, v_u_ally);
    PERFORM pg_temp.demo_hours(v_ally, TRUE);
    v_offer := pg_temp.demo_offer(v_ally, 'DEMO-OPT-EXAMEN', 'Examen visual computarizado',
                                  'Evaluación de agudeza visual y refracción computarizada.', 'SERVICE', 'EXAMEN',
                                  20, v_usd, 30, NULL, FALSE, v_u_admin, NULL);
    PERFORM pg_temp.demo_offer(v_ally, 'DEMO-OPT-LENTES', 'Lentes monofocales con antirreflejo',
                               'Par de lentes monofocales con tratamiento antirreflejo.', 'PRODUCT', 'LENTES_Y_MONTURAS',
                               60, v_usd, 20, NULL, FALSE, v_u_admin, 'No acumulable con otras promociones');
    PERFORM pg_temp.demo_offer(v_ally, 'DEMO-OPT-MONTURA', 'Montura de marca',
                               'Montura de marca a elección dentro del catálogo de la tienda.', 'PRODUCT', 'LENTES_Y_MONTURAS',
                               45, v_usd, NULL, 10, FALSE, v_u_admin, 'Sujeto a disponibilidad');
    -- One consumption of the demo member at the store, already rated.
    INSERT INTO benefit_usages (membership_id, ally_id, ally_product_id, ally_user_id, usage_date, usage_datetime,
                                copay_amount, copay_currency_id, consumption_amount, consumption_currency_id,
                                product_category_id, notes, ally_rating, product_rating, rating_comment, rated_at)
    SELECT ms.memberships_id, v_ally, v_offer, v_ally_user, current_date - 10,
           ((current_date - 10) + time '15:30') AT TIME ZONE 'America/Caracas',
           14, v_usd, 20, v_usd, (SELECT product_categories_id FROM product_categories WHERE code = 'EXAMEN'),
           'Consumo de demostración', 5, 5, 'Excelente atención', now()
      FROM memberships ms WHERE ms.member_id = v_member AND ms.is_active;

    -- ---- professional ally (B2P, medical): ophthalmologist ----
    v_p_ally := pg_temp.demo_person('96000008', 'Médica', 'Demo', 'aliado.medico' || c_domain, '04140000008', '1980-09-12', v_city);
    v_u_ally := pg_temp.demo_user(v_p_ally, 'aliado.medico' || c_domain, 'ALIADO');
    INSERT INTO allies (name, email, phone, whatsapp, address, city_id, description, joined_at, segment, person_id,
                        host_name, office_number, appointment_phone, assistant_name,
                        captured_by_promoter_id, registration_status, registration_channel,
                        approved_at, approved_by, registration_submitted_at, is_active, is_published, published_at)
    VALUES ('Dra. Médica Demo - Oftalmología', 'aliado.medico' || c_domain, '02550000008', '04140000008',
            'Centro Médico Demo, piso 2, Acarigua', v_city,
            'Oftalmóloga de demostración: consultas y estudios con tarifa preferencial para afiliados.',
            v_enrolled - 10, 'B2P', v_p_ally, 'Centro Médico Demo', '12', '04140000018', 'Asistente Demo',
            v_promoter, 'APPROVED', 'ADVISOR', now(), v_admin_uuid, now(), TRUE, TRUE, now())
    RETURNING allies_id INTO v_ally;
    INSERT INTO ally_ally_types (ally_id, ally_type_id) SELECT v_ally, ally_types_id FROM ally_types WHERE code = 'PROFESIONAL_MEDICO';
    INSERT INTO ally_professions (ally_id, profession_id) SELECT v_ally, professions_id FROM professions WHERE code = 'OFTALMOLOGIA';
    INSERT INTO person_professional_licenses (person_id, licensing_body_id, license_number, verification_status,
                                              verified_by, verified_at)
    SELECT v_p_ally, licensing_bodies_id, 'DEMO-96000008', 'VERIFIED', v_admin_uuid, now()
      FROM licensing_bodies WHERE code = 'MPPS';
    INSERT INTO ally_users (ally_id, user_id, ally_role, is_primary, joined_at) VALUES (v_ally, v_u_ally, 'OWNER', TRUE, v_enrolled - 10);
    PERFORM pg_temp.demo_agreement(v_ally, 'MEDICAL', 'B2P', v_enrolled - 10, v_u_ally);
    PERFORM pg_temp.demo_hours(v_ally, FALSE);
    PERFORM pg_temp.demo_offer(v_ally, 'DEMO-MED-CONSULTA', 'Consulta oftalmológica',
                               'Consulta con evaluación del fondo de ojo y presión intraocular.', 'SERVICE', 'CONSULTA',
                               40, v_usd, 30, NULL, TRUE, v_u_admin, NULL);
    PERFORM pg_temp.demo_offer(v_ally, 'DEMO-MED-CAMPIMETRIA', 'Campimetría computarizada',
                               'Estudio del campo visual.', 'SERVICE', 'EXAMEN',
                               35, v_usd, 25, NULL, TRUE, v_u_admin, NULL);

    -- ---- professional ally (B2P, wellness): psychologist ----
    v_p_ally := pg_temp.demo_person('96000009', 'Bienestar', 'Demo', 'aliado.bienestar' || c_domain, '04140000009', '1988-04-25', v_city);
    v_u_ally := pg_temp.demo_user(v_p_ally, 'aliado.bienestar' || c_domain, 'ALIADO');
    INSERT INTO allies (name, email, phone, whatsapp, address, city_id, description, joined_at, segment, person_id,
                        host_name, office_number, appointment_phone,
                        captured_by_promoter_id, registration_status, registration_channel,
                        approved_at, approved_by, registration_submitted_at, is_active, is_published, published_at)
    VALUES ('Lic. Bienestar Demo - Psicología', 'aliado.bienestar' || c_domain, '02550000009', '04140000009',
            'Consultorio Demo, Araure', v_city,
            'Psicóloga clínica de demostración: consultas y terapia con tarifa preferencial para afiliados.',
            v_enrolled - 5, 'B2P', v_p_ally, 'Consultorio Demo', '3', '04140000019',
            v_promoter, 'APPROVED', 'ADVISOR', now(), v_admin_uuid, now(), TRUE, TRUE, now())
    RETURNING allies_id INTO v_ally;
    INSERT INTO ally_ally_types (ally_id, ally_type_id) SELECT v_ally, ally_types_id FROM ally_types WHERE code = 'PROFESIONAL_BIENESTAR';
    INSERT INTO ally_professions (ally_id, profession_id) SELECT v_ally, professions_id FROM professions WHERE code = 'PSICOLOGIA_CLINICA';
    INSERT INTO ally_users (ally_id, user_id, ally_role, is_primary, joined_at) VALUES (v_ally, v_u_ally, 'OWNER', TRUE, v_enrolled - 5);
    PERFORM pg_temp.demo_agreement(v_ally, 'COMMERCIAL', 'B2P', v_enrolled - 5, v_u_ally);
    PERFORM pg_temp.demo_hours(v_ally, TRUE);
    PERFORM pg_temp.demo_offer(v_ally, 'DEMO-BIE-CONSULTA', 'Consulta psicológica',
                               'Primera consulta de evaluación psicológica.', 'SERVICE', 'CONSULTA',
                               30, v_usd, 25, NULL, TRUE, v_u_admin, NULL);
    PERFORM pg_temp.demo_offer(v_ally, 'DEMO-BIE-TERAPIA', 'Sesión de terapia individual',
                               'Sesión de psicoterapia de 50 minutos.', 'SERVICE', 'TERAPIA',
                               25, v_usd, 20, NULL, TRUE, v_u_admin, NULL);

    RAISE NOTICE 'V247: demo users seeded (9 users, 3 members, 3 allies)';
END
$$;

DROP FUNCTION pg_temp.demo_agreement(bigint, text, text, date, bigint);
DROP FUNCTION pg_temp.demo_hours(bigint, boolean);
DROP FUNCTION pg_temp.demo_offer(bigint, text, text, text, text, text, numeric, bigint, numeric, numeric, boolean, bigint, text);
DROP FUNCTION pg_temp.demo_charges(bigint, date, date, bigint, numeric, bigint, int);
DROP FUNCTION pg_temp.demo_commission(bigint, bigint, bigint, numeric, numeric, text, text, bigint, bigint);
DROP FUNCTION pg_temp.demo_payment(bigint, bigint, bigint, bigint, text, numeric, bigint, timestamptz, boolean, date, date, bigint, text);
DROP FUNCTION pg_temp.demo_user(bigint, text, text);
DROP FUNCTION pg_temp.demo_accept(bigint, text);
DROP FUNCTION pg_temp.demo_person(text, text, text, text, text, date, bigint);
