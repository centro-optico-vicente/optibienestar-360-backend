-- ────────────────────────────────────────────────────────────────────────────
-- scripts/db/normalize.sql — make the migration-seeded data deterministic so
-- src/main/resources/db/snapshot/data.sql only changes when a migration
-- changes the data, not on every regeneration.
--
-- Run by scripts/db/snapshot.sh on the EPHEMERAL snapshot database only
-- (right after Flyway migrated it, before pg_dump). NEVER run it against a
-- dev/prod database: it rewrites uuids and timestamps in place.
--
-- What varies between two runs of the same migrations, and what is done:
--   * uuid columns filled by gen_random_uuid()  → md5('<table>:<pk>')::uuid,
--     propagated to every uuid-typed column that referenced the old value
--     (created_by, owner_uuid, ...), so references stay consistent.
--   * timestamps produced by now() while migrating (any value inside the
--     Flyway run window) → fixed instant 2026-01-01 00:00:00+00.
--   * dates produced by current_date (equal to a run date) → 2026-01-01.
--     Fixed literal dates (holidays, valid_from '2026-...') are untouched.
--   * physical row order (UPDATEs in later migrations move rows) → CLUSTER
--     on the primary key, so COPY emits rows in PK order.
--   * flyway_schema_history installed_on / execution_time / installed_by →
--     fixed values. Checksums stay real: they are all Flyway validates.
-- Seed password hashes are literals in the migrations (already stable).
-- ────────────────────────────────────────────────────────────────────────────
SET search_path TO app, public;

DO $$
DECLARE
    rt     record;
    rc     record;
    lo     timestamptz;
    hi     timestamptz;
    epoch  CONSTANT timestamptz := '2026-01-01 00:00:00+00';
BEGIN
    SELECT min(installed_on)::timestamptz - interval '1 minute',
           max(installed_on)::timestamptz + interval '1 minute'
      INTO lo, hi
      FROM app.flyway_schema_history;

    -- Rewrite without firing triggers (set_updated_at) or FK checks.
    SET LOCAL session_replication_role = replica;

    -- 1. Deterministic uuids --------------------------------------------------
    CREATE TEMP TABLE snapshot_uuid_map (old uuid PRIMARY KEY, new uuid NOT NULL) ON COMMIT DROP;
    FOR rt IN
        SELECT cl.relname,
               (SELECT a.attname
                  FROM pg_index i
                  JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = i.indkey[0]
                 WHERE i.indrelid = cl.oid AND i.indisprimary) AS pk
          FROM pg_class cl
          JOIN pg_attribute u ON u.attrelid = cl.oid AND u.attname = 'uuid' AND NOT u.attisdropped
         WHERE cl.relnamespace = 'app'::regnamespace AND cl.relkind = 'r'
         ORDER BY cl.relname
    LOOP
        CONTINUE WHEN rt.pk IS NULL;
        EXECUTE format(
            'INSERT INTO snapshot_uuid_map
             SELECT uuid, md5(%L || '':'' || %I::text)::uuid FROM app.%I WHERE uuid IS NOT NULL
             ON CONFLICT DO NOTHING', rt.relname, rt.pk, rt.relname);
    END LOOP;

    FOR rc IN
        SELECT cl.relname, a.attname
          FROM pg_attribute a
          JOIN pg_class cl ON cl.oid = a.attrelid
         WHERE cl.relnamespace = 'app'::regnamespace AND cl.relkind = 'r'
           AND a.atttypid = 'uuid'::regtype AND a.attnum > 0 AND NOT a.attisdropped
    LOOP
        EXECUTE format('UPDATE app.%1$I x SET %2$I = m.new FROM snapshot_uuid_map m WHERE x.%2$I = m.old',
                       rc.relname, rc.attname);
    END LOOP;

    -- 2. Timestamps / dates generated while migrating ----------------------------
    FOR rc IN
        SELECT cl.relname, a.attname, a.atttypid
          FROM pg_attribute a
          JOIN pg_class cl ON cl.oid = a.attrelid
         WHERE cl.relnamespace = 'app'::regnamespace AND cl.relkind = 'r'
           AND cl.relname <> 'flyway_schema_history'
           AND a.atttypid IN ('timestamptz'::regtype, 'timestamp'::regtype, 'date'::regtype)
           AND a.attnum > 0 AND NOT a.attisdropped
    LOOP
        IF rc.atttypid = 'date'::regtype THEN
            EXECUTE format('UPDATE app.%1$I SET %2$I = %3$L::date WHERE %2$I BETWEEN %4$L::date AND %5$L::date',
                           rc.relname, rc.attname, epoch, lo, hi);
        ELSE
            EXECUTE format('UPDATE app.%1$I SET %2$I = %3$L WHERE %2$I::timestamptz BETWEEN %4$L AND %5$L',
                           rc.relname, rc.attname, epoch, lo, hi);
        END IF;
    END LOOP;

    -- 3. Flyway history --------------------------------------------------------
    UPDATE app.flyway_schema_history
       SET installed_on = epoch AT TIME ZONE 'UTC', execution_time = 0, installed_by = 'snapshot';
END $$;

-- 4. Physical order = primary key order. CLUSTER cannot run inside a DO block,
--    so the statements are generated and run with \gexec. SET WITHOUT CLUSTER
--    right after, so schema.sql does not pick up "ALTER TABLE ... CLUSTER ON".
SELECT format('CLUSTER app.%1$I USING %2$I; ALTER TABLE app.%1$I SET WITHOUT CLUSTER', cl.relname, ic.relname)
  FROM pg_index i
  JOIN pg_class cl ON cl.oid = i.indrelid
  JOIN pg_class ic ON ic.oid = i.indexrelid
 WHERE i.indisprimary AND cl.relnamespace = 'app'::regnamespace AND cl.relkind = 'r'
 ORDER BY cl.relname
\gexec
