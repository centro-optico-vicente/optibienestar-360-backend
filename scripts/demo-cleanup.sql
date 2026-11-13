-- ============================================================
-- demo-cleanup.sql — inactivate (default) or delete the demo users and data
-- seeded by V247__seed_demo_users_by_segment.sql. Run it before the
-- production launch on any database that ever had DEMO_SEED_ENABLED=true.
--
-- Usage (on the server, as the migration role):
--   docker exec -i <postgres-container> psql -U <migration-user> -d <db> -f - < scripts/demo-cleanup.sql
--       → dry run: prints what it finds and what it would change, then ROLLBACK.
--   ... psql -v commit=1 ...                    → inactivate and COMMIT.
--   ... psql -v commit=1 -v hard_delete=1 ...   → delete and COMMIT.
--
-- What counts as demo (the tags V247 puts on every row):
--   - users / persons / allies / promoters with an e-mail ending in
--     @optibienestar360.centroopticovicente.com
--   - persons with document V-96000001 .. V-96000013 (holders, beneficiaries
--     and the two portfolio members without login)
--   - promoter referral code DEMO-ASESOR, product codes DEMO-*
--
-- Inactivate (default): users SUSPENDED, advisor INACTIVE, allies and their
-- offers unpublished and inactive, memberships CANCELED, members inactive.
-- Reversible by hand.
--
-- Delete (hard_delete): removes the demo-owned rows. Parent rows (users,
-- persons, members, promoters, allies, payments) are deleted one by one; if a
-- real record still points to one (e.g. a real payment the demo administrator
-- approved, a real member assigned to the demo advisor), that row is kept,
-- left inactivated, and reported with a NOTICE.
--
-- Never touches optibienestar360@gmail.com (the old demo administrator) nor the
-- mailbox info.optibienestar360@gmail.com (not a user).
-- ============================================================

\set ON_ERROR_STOP on
SET search_path TO app, public;
BEGIN;

-- ---------- what is demo ----------
CREATE TEMP TABLE demo_users ON COMMIT DROP AS
    SELECT users_id, person_id, uuid FROM users
     WHERE email LIKE '%@optibienestar360.centroopticovicente.com';

CREATE TEMP TABLE demo_persons ON COMMIT DROP AS
    SELECT persons_id FROM persons
     WHERE (document_type = 'V' AND document_number BETWEEN '96000001' AND '96000013'
            AND length(document_number) = 8)
        OR email LIKE '%@optibienestar360.centroopticovicente.com'
        OR persons_id IN (SELECT person_id FROM demo_users);

CREATE TEMP TABLE demo_promoters ON COMMIT DROP AS
    SELECT promoters_id FROM promoters
     WHERE referral_code = 'DEMO-ASESOR'
        OR user_id IN (SELECT users_id FROM demo_users);

CREATE TEMP TABLE demo_members ON COMMIT DROP AS
    SELECT members_id FROM members WHERE person_id IN (SELECT persons_id FROM demo_persons);

CREATE TEMP TABLE demo_memberships ON COMMIT DROP AS
    SELECT memberships_id FROM memberships WHERE member_id IN (SELECT members_id FROM demo_members);

CREATE TEMP TABLE demo_allies ON COMMIT DROP AS
    SELECT allies_id FROM allies
     WHERE email LIKE '%@optibienestar360.centroopticovicente.com'
        OR allies_id IN (SELECT ally_id FROM ally_users WHERE user_id IN (SELECT users_id FROM demo_users)
                          AND ally_role = 'OWNER');

CREATE TEMP TABLE demo_payments ON COMMIT DROP AS
    SELECT payments_id FROM payments
     WHERE person_id IN (SELECT persons_id FROM demo_persons)
        OR membership_id IN (SELECT memberships_id FROM demo_memberships);

\echo '== Demo rows found =='
SELECT 'users' AS what, count(*) FROM demo_users
UNION ALL SELECT 'persons', count(*) FROM demo_persons
UNION ALL SELECT 'promoters', count(*) FROM demo_promoters
UNION ALL SELECT 'members', count(*) FROM demo_members
UNION ALL SELECT 'memberships', count(*) FROM demo_memberships
UNION ALL SELECT 'allies', count(*) FROM demo_allies
UNION ALL SELECT 'payments', count(*) FROM demo_payments
UNION ALL SELECT 'commissions', count(*) FROM commissions
           WHERE payment_id IN (SELECT payments_id FROM demo_payments)
UNION ALL SELECT 'ally offers', count(*) FROM ally_products WHERE ally_id IN (SELECT allies_id FROM demo_allies);

\echo '== Real records that point to demo rows (these demo rows will be kept, inactivated) =='
SELECT 'real members of the demo advisor' AS what, count(*) FROM members
 WHERE promoter_id IN (SELECT promoters_id FROM demo_promoters)
   AND members_id NOT IN (SELECT members_id FROM demo_members)
UNION ALL SELECT 'real allies captured by the demo advisor', count(*) FROM allies
 WHERE captured_by_promoter_id IN (SELECT promoters_id FROM demo_promoters)
   AND allies_id NOT IN (SELECT allies_id FROM demo_allies)
UNION ALL SELECT 'real payments reviewed by a demo user', count(*) FROM payments
 WHERE reviewed_by IN (SELECT users_id FROM demo_users)
   AND payments_id NOT IN (SELECT payments_id FROM demo_payments);

-- ---------- inactivate (always; it is the fallback for kept rows) ----------
UPDATE users SET status = 'SUSPENDED', is_active = FALSE, login_token_hash = NULL, login_token_expires_at = NULL,
                 password_reset_token = NULL, password_reset_expires_at = NULL
 WHERE users_id IN (SELECT users_id FROM demo_users);
UPDATE promoters SET status = 'INACTIVE', is_active = FALSE WHERE promoters_id IN (SELECT promoters_id FROM demo_promoters);
UPDATE ally_products SET is_published = FALSE, is_active = FALSE WHERE ally_id IN (SELECT allies_id FROM demo_allies);
UPDATE products SET is_published = FALSE, is_active = FALSE WHERE owner_ally_id IN (SELECT allies_id FROM demo_allies);
UPDATE allies SET is_published = FALSE, is_active = FALSE WHERE allies_id IN (SELECT allies_id FROM demo_allies);
UPDATE memberships SET status = 'CANCELED', last_status_change_at = now(),
                       last_status_change_reason = 'Datos de demostración retirados antes del lanzamiento'
 WHERE memberships_id IN (SELECT memberships_id FROM demo_memberships) AND status <> 'CANCELED';
UPDATE members SET is_active = FALSE WHERE members_id IN (SELECT members_id FROM demo_members);

-- ---------- delete (only with -v hard_delete=1) ----------
\if :{?hard_delete}
\echo '== Deleting demo rows =='

-- Deletes one row at a time; a row still referenced by real data is kept.
CREATE FUNCTION pg_temp.try_delete(p_table text, p_pk text, p_ids bigint[])
RETURNS void LANGUAGE plpgsql AS $f$
DECLARE v_id bigint; v_kept int := 0; v_deleted int := 0;
BEGIN
    FOREACH v_id IN ARRAY coalesce(p_ids, '{}') LOOP
        BEGIN
            EXECUTE format('DELETE FROM %I WHERE %I = $1', p_table, p_pk) USING v_id;
            v_deleted := v_deleted + 1;
        EXCEPTION WHEN foreign_key_violation THEN
            v_kept := v_kept + 1;
            RAISE NOTICE '% % kept (still referenced by real data; left inactivated)', p_table, v_id;
        END;
    END LOOP;
    RAISE NOTICE '%: % deleted, % kept', p_table, v_deleted, v_kept;
END;
$f$;

-- Rows that only exist because of the demo data.
DELETE FROM commissions WHERE payment_id IN (SELECT payments_id FROM demo_payments);
DELETE FROM benefit_usages WHERE membership_id IN (SELECT memberships_id FROM demo_memberships)
                              OR ally_id IN (SELECT allies_id FROM demo_allies);
DELETE FROM membership_charges WHERE membership_id IN (SELECT memberships_id FROM demo_memberships);
DELETE FROM person_credit_entries WHERE person_id IN (SELECT persons_id FROM demo_persons)
                                     OR membership_id IN (SELECT memberships_id FROM demo_memberships);
DELETE FROM terms_acceptances WHERE user_id IN (SELECT users_id FROM demo_users)
                                 OR membership_id IN (SELECT memberships_id FROM demo_memberships)
                                 OR ally_agreement_id IN (SELECT ally_agreements_id FROM ally_agreements
                                                           WHERE ally_id IN (SELECT allies_id FROM demo_allies));
DELETE FROM ally_patient_links WHERE member_id IN (SELECT members_id FROM demo_members)
                                  OR created_at_ally_id IN (SELECT allies_id FROM demo_allies);
DELETE FROM medical_record_access_log WHERE member_id IN (SELECT members_id FROM demo_members)
                                         OR ally_id IN (SELECT allies_id FROM demo_allies);
DELETE FROM ally_agreements WHERE ally_id IN (SELECT allies_id FROM demo_allies);
DELETE FROM person_professional_licenses WHERE person_id IN (SELECT persons_id FROM demo_persons);
DELETE FROM medical_records WHERE person_id IN (SELECT persons_id FROM demo_persons);
DELETE FROM beneficiaries WHERE member_id IN (SELECT members_id FROM demo_members);
DELETE FROM user_password_history WHERE user_id IN (SELECT users_id FROM demo_users);
DELETE FROM user_roles WHERE user_id IN (SELECT users_id FROM demo_users);
DELETE FROM user_sessions_log WHERE user_id IN (SELECT users_id FROM demo_users);
DELETE FROM login_audit_log WHERE user_id IN (SELECT users_id FROM demo_users);
DELETE FROM notifications WHERE recipient_user_id IN (SELECT users_id FROM demo_users);
DELETE FROM ally_users WHERE user_id IN (SELECT users_id FROM demo_users);

-- Parents, one by one (payment lines go with their payment; offers, products,
-- types, professions and hours go with their ally; payout accounts with the advisor).
SELECT pg_temp.try_delete('payments', 'payments_id', array_agg(payments_id)) FROM demo_payments;
SELECT pg_temp.try_delete('memberships', 'memberships_id', array_agg(memberships_id)) FROM demo_memberships;
SELECT pg_temp.try_delete('members', 'members_id', array_agg(members_id)) FROM demo_members;
SELECT pg_temp.try_delete('allies', 'allies_id', array_agg(allies_id)) FROM demo_allies;
SELECT pg_temp.try_delete('promoters', 'promoters_id', array_agg(promoters_id)) FROM demo_promoters;
SELECT pg_temp.try_delete('users', 'users_id', array_agg(users_id)) FROM demo_users;
SELECT pg_temp.try_delete('persons', 'persons_id', array_agg(persons_id)) FROM demo_persons;
\endif

\echo '== Demo users left (should be 0 after delete, or all SUSPENDED after inactivate) =='
SELECT email, status, is_active FROM users
 WHERE email LIKE '%@optibienestar360.centroopticovicente.com' ORDER BY email;

\if :{?commit}
COMMIT;
\echo '== COMMITTED =='
\else
ROLLBACK;
\echo '== DRY RUN: rolled back. Run again with -v commit=1 to apply. =='
\endif
