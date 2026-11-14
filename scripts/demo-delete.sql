-- ============================================================
-- demo-delete.sql — delete the demo users and data seeded by
-- V247__seed_demo_users_by_segment.sql, in cascade with every related row.
-- Not reversible. To only switch them off use demo-inactivate.sql instead.
--
-- Usage (on the server, as the migration role):
--   docker exec -i <postgres-container> psql -U <migration-user> -d <db> -f - < scripts/demo-delete.sql
--       → dry run: prints what it would delete or detach, then ROLLBACK.
--   ... psql -v commit=1 ...                       → delete and COMMIT.
--   ... psql -v commit=1 -v allow_collateral=1 ... → also COMMIT when the
--       cascade reaches core rows that carry no demo tag (see below).
--
-- What counts as demo (the tags V247 puts on every row):
--   - users / persons / allies / promoters with an e-mail ending in
--     @optibienestar360.centroopticovicente.com
--   - persons with document V-96000001 .. V-96000013
--   - promoter referral code DEMO-ASESOR, product codes DEMO-*
--
-- How the cascade works. Starting from the tagged rows, it follows every
-- foreign key in schema `app` (read from pg_constraint, so new tables are
-- covered without editing this file) and, for each row that points to a row
-- being deleted:
--   - DELETES it when the column is NOT NULL or it is an ownership link
--     (payments of a demo membership, products of a demo ally, logins and
--     notifications of a demo user, ...). Repeats until nothing new is found.
--   - DETACHES it (column set to NULL, row kept) when the column only records
--     who did something or who is attributed: *_by, actor_*, *_login_audit_log_id,
--     *payout_payment_id, and the attribution columns listed in keep_cols
--     below (e.g. a real member assigned to the demo advisor keeps existing,
--     unassigned).
-- Rows tied by UUID instead of a foreign key also go: attached_files and
-- media_items owned by a deleted row, and the change / report audit entries
-- about it.
--
-- Collateral: core rows (users, persons, members, memberships, payments,
-- allies, promoters) reached by the cascade that carry no demo tag are listed.
-- If there is any, COMMIT is refused unless -v allow_collateral=1.
--
-- Files in R2: the script prints the object keys of the deleted attachments;
-- remove them from the bucket by hand (SQL cannot reach R2).
--
-- Never touches optibienestar360@gmail.com (the old demo administrator) nor the
-- mailbox info.optibienestar360@gmail.com (not a user).
-- ============================================================

\set ON_ERROR_STOP on
SET search_path TO app, public;
BEGIN;

-- Rows to delete (seed = carries a demo tag) and columns to set to NULL.
CREATE TEMP TABLE doomed (tbl regclass, rid tid, seed boolean NOT NULL, done boolean NOT NULL DEFAULT FALSE,
                          PRIMARY KEY (tbl, rid)) ON COMMIT DROP;
CREATE TEMP TABLE to_null (tbl regclass, col name, rid tid, PRIMARY KEY (tbl, col, rid)) ON COMMIT DROP;
CREATE TEMP TABLE doomed_uuid (u uuid PRIMARY KEY) ON COMMIT DROP;

-- ---------- what is demo ----------
INSERT INTO doomed (tbl, rid, seed)
SELECT 'users'::regclass, ctid, TRUE FROM users
 WHERE email LIKE '%@optibienestar360.centroopticovicente.com'
UNION
SELECT 'persons'::regclass, ctid, TRUE FROM persons
 WHERE (document_type = 'V' AND document_number BETWEEN '96000001' AND '96000013' AND length(document_number) = 8)
    OR email LIKE '%@optibienestar360.centroopticovicente.com'
    OR persons_id IN (SELECT person_id FROM users WHERE email LIKE '%@optibienestar360.centroopticovicente.com')
UNION
SELECT 'promoters'::regclass, ctid, TRUE FROM promoters
 WHERE referral_code = 'DEMO-ASESOR'
    OR user_id IN (SELECT users_id FROM users WHERE email LIKE '%@optibienestar360.centroopticovicente.com')
UNION
SELECT 'allies'::regclass, ctid, TRUE FROM allies
 WHERE email LIKE '%@optibienestar360.centroopticovicente.com'
    OR allies_id IN (SELECT ally_id FROM ally_users
                      WHERE ally_role = 'OWNER'
                        AND user_id IN (SELECT users_id FROM users
                                         WHERE email LIKE '%@optibienestar360.centroopticovicente.com'))
UNION
SELECT 'products'::regclass, ctid, TRUE FROM products WHERE code LIKE 'DEMO-%';

-- ---------- cascade: collect every related row ----------
DO $cascade$
DECLARE
    -- Nullable attribution columns: the row may be real, it only points to a demo row.
    keep_cols text[] := ARRAY[
        'members.promoter_id', 'payments.promoter_id', 'allies.captured_by_promoter_id',
        'promoters.supervisor_id', 'promoter_supervisor_assignments.from_supervisor_id',
        'promoter_supervisor_assignments.to_supervisor_id', 'member_promoter_assignments.from_promoter_id',
        'competitive_commission_manual_decisions.replaced_promoter_id', 'affiliation_requests.promoter_id',
        'affiliation_requests.payment_id', 'corporate_contracts.contact_user_id',
        'membership_promotions.code_owner_ally_id', 'membership_promotions.code_owner_member_id',
        'membership_promotions.code_owner_promoter_id', 'membership_charges.covered_by_payment_id',
        'beneficiaries.inscription_payment_id', 'referrals.referred_member_id', 'referrals.reward_payment_id',
        'data_change_audit_log.restored_from_id'];
    keep_pattern constant text := '(_by|_by_user_id|actor_user_id|actor_id|payout_payment_id|login_audit_log_id)$';
    fk record;
    t record;
    n bigint;
    added bigint;
    pass int := 0;
    parent_rows text;
BEGIN
    LOOP
        pass := pass + 1;
        added := 0;

        -- UUIDs of everything doomed so far (for the rows tied by UUID).
        FOR t IN SELECT DISTINCT d.tbl FROM doomed d
                  WHERE EXISTS (SELECT 1 FROM pg_attribute a
                                 WHERE a.attrelid = d.tbl AND a.attname = 'uuid' AND NOT a.attisdropped) LOOP
            EXECUTE format('INSERT INTO doomed_uuid SELECT uuid FROM %s WHERE uuid IS NOT NULL
                              AND ctid = ANY(ARRAY(SELECT rid FROM doomed WHERE tbl = %L::regclass))
                            ON CONFLICT DO NOTHING', t.tbl, t.tbl);
        END LOOP;

        INSERT INTO doomed (tbl, rid, seed)
        SELECT 'attached_files'::regclass, ctid, FALSE FROM attached_files WHERE owner_uuid IN (SELECT u FROM doomed_uuid)
        UNION ALL
        SELECT 'media_items'::regclass, ctid, FALSE FROM media_items WHERE owner_uuid IN (SELECT u FROM doomed_uuid)
        UNION ALL
        SELECT 'data_change_audit_log'::regclass, ctid, FALSE FROM data_change_audit_log
         WHERE entity_uuid IN (SELECT u FROM doomed_uuid)
        UNION ALL
        SELECT 'report_audit_log'::regclass, ctid, FALSE FROM report_audit_log
         WHERE entity_uuid IN (SELECT u FROM doomed_uuid)
        ON CONFLICT DO NOTHING;
        GET DIAGNOSTICS n = ROW_COUNT;
        added := added + n;

        -- Every single-column foreign key in `app` whose parent has doomed rows.
        FOR fk IN
            SELECT c.conrelid::regclass AS child, cc.relname AS child_name, ac.attname AS col,
                   ac.attnotnull AS not_null, c.confrelid::regclass AS parent, ap.attname AS pcol
              FROM pg_constraint c
              JOIN pg_class cc ON cc.oid = c.conrelid
              JOIN pg_attribute ac ON ac.attrelid = c.conrelid AND ac.attnum = c.conkey[1]
              JOIN pg_attribute ap ON ap.attrelid = c.confrelid AND ap.attnum = c.confkey[1]
             WHERE c.contype = 'f'
               AND c.connamespace = 'app'::regnamespace
               AND array_length(c.conkey, 1) = 1
               AND c.confrelid IN (SELECT DISTINCT tbl FROM doomed)
        LOOP
            parent_rows := format('SELECT p.%I FROM %s p WHERE p.ctid = ANY(ARRAY(SELECT rid FROM doomed WHERE tbl = %L::regclass))',
                                  fk.pcol, fk.parent, fk.parent);
            IF NOT fk.not_null AND (fk.col ~ keep_pattern OR (fk.child_name || '.' || fk.col) = ANY(keep_cols)) THEN
                EXECUTE format('INSERT INTO to_null (tbl, col, rid) SELECT %L::regclass, %L::name, t.ctid FROM %s t
                                 WHERE t.%I IN (%s) ON CONFLICT DO NOTHING',
                               fk.child, fk.col, fk.child, fk.col, parent_rows);
            ELSE
                EXECUTE format('INSERT INTO doomed (tbl, rid, seed) SELECT %L::regclass, t.ctid, FALSE FROM %s t
                                 WHERE t.%I IN (%s) ON CONFLICT DO NOTHING',
                               fk.child, fk.child, fk.col, parent_rows);
                GET DIAGNOSTICS n = ROW_COUNT;
                added := added + n;
            END IF;
        END LOOP;

        EXIT WHEN added = 0;
        IF pass >= 50 THEN
            RAISE EXCEPTION 'cascade did not converge after % passes', pass;
        END IF;
    END LOOP;

    -- A row that is deleted does not need detaching.
    DELETE FROM to_null n USING doomed d WHERE d.tbl = n.tbl AND d.rid = n.rid;
END;
$cascade$;

\echo '== Rows to delete (tagged = carries a demo tag; cascade = reached through it) and to detach =='
SELECT tbl::text AS "table",
       count(*) FILTER (WHERE seed)     AS tagged,
       count(*) FILTER (WHERE NOT seed) AS cascade,
       0::bigint                         AS detached
  FROM doomed GROUP BY tbl
UNION ALL
SELECT tbl::text || '.' || col, 0, 0, count(*) FROM to_null GROUP BY tbl, col
 ORDER BY 1;

\echo '== Collateral: core rows reached by the cascade without a demo tag =='
CREATE TEMP TABLE collateral ON COMMIT DROP AS
SELECT d.tbl::text AS "table", count(*) AS rows
  FROM doomed d
 WHERE NOT d.seed
   AND d.tbl IN ('users'::regclass, 'persons'::regclass, 'members'::regclass, 'memberships'::regclass,
                 'payments'::regclass, 'allies'::regclass, 'promoters'::regclass)
 GROUP BY d.tbl;
-- Members, memberships and payments of demo persons are expected: only the
-- ones whose person is not demo are real collateral.
DELETE FROM collateral WHERE "table" IN ('members', 'memberships', 'payments');
INSERT INTO collateral
SELECT 'members', count(*) FROM members m
 WHERE m.ctid = ANY(ARRAY(SELECT rid FROM doomed WHERE tbl = 'members'::regclass))
   AND NOT EXISTS (SELECT 1 FROM persons p, doomed d
                    WHERE p.persons_id = m.person_id AND d.tbl = 'persons'::regclass AND d.rid = p.ctid)
HAVING count(*) > 0
UNION ALL
SELECT 'payments', count(*) FROM payments y
 WHERE y.ctid = ANY(ARRAY(SELECT rid FROM doomed WHERE tbl = 'payments'::regclass))
   AND NOT EXISTS (SELECT 1 FROM persons p, doomed d
                    WHERE p.persons_id = y.person_id AND d.tbl = 'persons'::regclass AND d.rid = p.ctid)
HAVING count(*) > 0;
SELECT * FROM collateral;
SELECT 'user' AS kind, email AS who FROM users
 WHERE ctid = ANY(ARRAY(SELECT rid FROM doomed WHERE tbl = 'users'::regclass AND NOT seed))
UNION ALL
SELECT 'person', document_type || '-' || document_number || ' ' || coalesce(email, '') FROM persons
 WHERE ctid = ANY(ARRAY(SELECT rid FROM doomed WHERE tbl = 'persons'::regclass AND NOT seed));
SELECT (count(*) > 0) AS has_collateral FROM collateral \gset

\echo '== R2 objects to remove by hand after COMMIT =='
SELECT file_key AS r2_key FROM attached_files
 WHERE ctid = ANY(ARRAY(SELECT rid FROM doomed WHERE tbl = 'attached_files'::regclass)) AND file_key IS NOT NULL
UNION
SELECT public_key FROM media_items
 WHERE ctid = ANY(ARRAY(SELECT rid FROM doomed WHERE tbl = 'media_items'::regclass)) AND public_key IS NOT NULL;

-- ---------- detach, then delete ----------
DO $apply$
DECLARE
    t record;
    set_list text;
    pass int;
    progress boolean;
BEGIN
    -- One UPDATE per table so a row with two detached columns keeps a valid ctid.
    FOR t IN SELECT tbl, array_agg(DISTINCT col) AS cols FROM to_null GROUP BY tbl LOOP
        SELECT string_agg(format('%I = CASE WHEN ctid = ANY(ARRAY(SELECT rid FROM to_null WHERE tbl = %L::regclass AND col = %L))
                                            THEN NULL ELSE %I END', c, t.tbl, c, c), ', ')
          INTO set_list FROM unnest(t.cols) AS c;
        EXECUTE format('UPDATE %s SET %s WHERE ctid = ANY(ARRAY(SELECT rid FROM to_null WHERE tbl = %L::regclass))',
                       t.tbl, set_list, t.tbl);
    END LOOP;

    -- Children first: retry each table until the ones it depends on are gone.
    FOR pass IN 1..50 LOOP
        progress := FALSE;
        FOR t IN SELECT DISTINCT tbl FROM doomed WHERE NOT done LOOP
            BEGIN
                EXECUTE format('DELETE FROM %s WHERE ctid = ANY(ARRAY(SELECT rid FROM doomed WHERE tbl = %L::regclass))',
                               t.tbl, t.tbl);
                UPDATE doomed SET done = TRUE WHERE tbl = t.tbl;
                progress := TRUE;
            EXCEPTION WHEN foreign_key_violation THEN
                NULL;  -- still referenced by a row deleted later in this loop
            END;
        END LOOP;
        EXIT WHEN NOT EXISTS (SELECT 1 FROM doomed WHERE NOT done);
        IF NOT progress THEN
            RAISE EXCEPTION 'cannot delete, still referenced: %',
                (SELECT string_agg(DISTINCT tbl::text, ', ') FROM doomed WHERE NOT done);
        END IF;
    END LOOP;
END;
$apply$;

\echo '== Demo users left (should be none) =='
SELECT email, status FROM users WHERE email LIKE '%@optibienestar360.centroopticovicente.com' ORDER BY email;

\if :{?commit}
  \if :has_collateral
    \if :{?allow_collateral}
COMMIT;
\echo '== COMMITTED (with collateral, allowed by -v allow_collateral=1) =='
    \else
ROLLBACK;
\echo '== NOT COMMITTED: the cascade reaches untagged core rows (see "Collateral"). Review them and run again with -v allow_collateral=1. =='
    \endif
  \else
COMMIT;
\echo '== COMMITTED =='
  \endif
\else
ROLLBACK;
\echo '== DRY RUN: rolled back. Run again with -v commit=1 to apply. =='
\endif
