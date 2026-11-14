-- ============================================================
-- demo-inactivate.sql — inactivate the demo users and data seeded by
-- V247__seed_demo_users_by_segment.sql. Reversible by hand; nothing is deleted.
-- To remove them for good use demo-delete.sql instead.
--
-- Usage (on the server, as the migration role):
--   docker exec -i <postgres-container> psql -U <migration-user> -d <db> -f - < scripts/demo-inactivate.sql
--       → dry run: prints what it finds and what it would change, then ROLLBACK.
--   ... psql -v commit=1 ...   → inactivate and COMMIT.
--
-- What counts as demo (the tags V247 puts on every row):
--   - users / persons / allies / promoters with an e-mail ending in
--     @optibienestar360.centroopticovicente.com
--   - persons with document V-96000001 .. V-96000013 (holders, beneficiaries
--     and the two portfolio members without login)
--   - promoter referral code DEMO-ASESOR, product codes DEMO-*
--
-- Result: users SUSPENDED (and their pending login / reset tokens dropped),
-- advisor INACTIVE, allies, their offers and exclusive products unpublished
-- and inactive, memberships CANCELED, members inactive.
--
-- Never touches optibienestar360@gmail.com (the old demo administrator) nor the
-- mailbox info.optibienestar360@gmail.com (not a user).
-- ============================================================

\set ON_ERROR_STOP on
SET search_path TO app, public;
BEGIN;

-- ---------- what is demo ----------
CREATE TEMP TABLE demo_users ON COMMIT DROP AS
    SELECT users_id, person_id FROM users
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

\echo '== Demo rows found =='
SELECT 'users' AS what, count(*) FROM demo_users
UNION ALL SELECT 'persons', count(*) FROM demo_persons
UNION ALL SELECT 'promoters', count(*) FROM demo_promoters
UNION ALL SELECT 'members', count(*) FROM demo_members
UNION ALL SELECT 'memberships', count(*) FROM demo_memberships
UNION ALL SELECT 'allies', count(*) FROM demo_allies
UNION ALL SELECT 'ally offers', count(*) FROM ally_products WHERE ally_id IN (SELECT allies_id FROM demo_allies)
UNION ALL SELECT 'products', count(*) FROM products
           WHERE owner_ally_id IN (SELECT allies_id FROM demo_allies) OR code LIKE 'DEMO-%';

\echo '== Real records that point to demo rows (left as they are) =='
SELECT 'real members of the demo advisor' AS what, count(*) FROM members
 WHERE promoter_id IN (SELECT promoters_id FROM demo_promoters)
   AND members_id NOT IN (SELECT members_id FROM demo_members)
UNION ALL SELECT 'real allies captured by the demo advisor', count(*) FROM allies
 WHERE captured_by_promoter_id IN (SELECT promoters_id FROM demo_promoters)
   AND allies_id NOT IN (SELECT allies_id FROM demo_allies)
UNION ALL SELECT 'real payments reviewed by a demo user', count(*) FROM payments
 WHERE reviewed_by IN (SELECT users_id FROM demo_users)
   AND person_id NOT IN (SELECT persons_id FROM demo_persons);

-- ---------- inactivate ----------
UPDATE users SET status = 'SUSPENDED', is_active = FALSE, login_token_hash = NULL, login_token_expires_at = NULL,
                 password_reset_token = NULL, password_reset_expires_at = NULL
 WHERE users_id IN (SELECT users_id FROM demo_users);
UPDATE promoters SET status = 'INACTIVE', is_active = FALSE WHERE promoters_id IN (SELECT promoters_id FROM demo_promoters);
UPDATE ally_products SET is_published = FALSE, is_active = FALSE WHERE ally_id IN (SELECT allies_id FROM demo_allies);
UPDATE products SET is_published = FALSE, is_active = FALSE
 WHERE owner_ally_id IN (SELECT allies_id FROM demo_allies) OR code LIKE 'DEMO-%';
UPDATE allies SET is_published = FALSE, is_active = FALSE WHERE allies_id IN (SELECT allies_id FROM demo_allies);
UPDATE memberships SET status = 'CANCELED', last_status_change_at = now(),
                       last_status_change_reason = 'Datos de demostración retirados antes del lanzamiento'
 WHERE memberships_id IN (SELECT memberships_id FROM demo_memberships) AND status <> 'CANCELED';
UPDATE members SET is_active = FALSE WHERE members_id IN (SELECT members_id FROM demo_members);

\echo '== Demo users after the change (all should be SUSPENDED) =='
SELECT email, status, is_active FROM users
 WHERE email LIKE '%@optibienestar360.centroopticovicente.com' ORDER BY email;

\if :{?commit}
COMMIT;
\echo '== COMMITTED =='
\else
ROLLBACK;
\echo '== DRY RUN: rolled back. Run again with -v commit=1 to apply. =='
\endif
