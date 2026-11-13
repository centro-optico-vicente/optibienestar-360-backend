-- ────────────────────────────────────────────────────────────────────────────
-- scripts/db/schema-map.sql — renders src/main/resources/db/snapshot/schema-map.md from the catalog
-- of an already-migrated database. Run by scripts/db/snapshot.sh (psql -At).
--
-- Output is deterministic (everything ordered by name, no timestamps) so the
-- generated file only changes when the schema does.
-- ────────────────────────────────────────────────────────────────────────────
SET search_path TO app, public;

WITH
tbl AS (
    SELECT c.oid, c.relname
    FROM pg_class c
    WHERE c.relnamespace = 'app'::regnamespace
      AND c.relkind IN ('r', 'p')
      AND c.relname <> 'flyway_schema_history'
),
col AS (
    SELECT a.attrelid,
           string_agg(
               '- `' || a.attname || '` ' || format_type(a.atttypid, a.atttypmod)
               || CASE WHEN a.attnotnull THEN ' NOT NULL' ELSE '' END
               || CASE WHEN a.attidentity <> '' THEN ' IDENTITY'
                       WHEN a.attgenerated <> '' THEN ' GENERATED AS (' || pg_get_expr(d.adbin, d.adrelid) || ')'
                       WHEN d.adbin IS NOT NULL THEN ' DEFAULT ' || pg_get_expr(d.adbin, d.adrelid)
                       ELSE '' END
               || COALESCE(' — ' || replace(col_description(a.attrelid, a.attnum), E'\n', ' '), ''),
               E'\n' ORDER BY a.attnum) AS txt
    FROM pg_attribute a
    JOIN tbl t ON t.oid = a.attrelid
    LEFT JOIN pg_attrdef d ON d.adrelid = a.attrelid AND d.adnum = a.attnum
    WHERE a.attnum > 0 AND NOT a.attisdropped
    GROUP BY a.attrelid
),
con AS (
    SELECT k.conrelid,
           string_agg(
               CASE k.contype
                   WHEN 'p' THEN '- PK ' || pg_get_constraintdef(k.oid)
                   WHEN 'f' THEN '- FK `' || k.conname || '` ' || pg_get_constraintdef(k.oid)
                   WHEN 'u' THEN '- UNIQUE `' || k.conname || '` ' || pg_get_constraintdef(k.oid)
                   WHEN 'c' THEN '- CHECK `' || k.conname || '` ' || pg_get_constraintdef(k.oid)
                   WHEN 'x' THEN '- EXCLUDE `' || k.conname || '` ' || pg_get_constraintdef(k.oid)
               END,
               E'\n' ORDER BY CASE k.contype WHEN 'p' THEN 0 WHEN 'f' THEN 1 WHEN 'u' THEN 2 WHEN 'x' THEN 3 ELSE 4 END,
                              k.conname) AS txt
    FROM pg_constraint k
    JOIN tbl t ON t.oid = k.conrelid
    GROUP BY k.conrelid
),
idx AS (
    -- Indexes not backing a PK/UNIQUE/EXCLUDE constraint (those are listed above).
    SELECT i.indrelid,
           string_agg('- `' || ic.relname || '` '
                      || regexp_replace(pg_get_indexdef(i.indexrelid), '^CREATE (UNIQUE )?INDEX \S+ ON \S+ USING ', '\1'),
                      E'\n' ORDER BY ic.relname) AS txt
    FROM pg_index i
    JOIN tbl t ON t.oid = i.indrelid
    JOIN pg_class ic ON ic.oid = i.indexrelid
    WHERE NOT EXISTS (SELECT 1 FROM pg_constraint k WHERE k.conindid = i.indexrelid AND k.contype IN ('p', 'u', 'x'))
    GROUP BY i.indrelid
),
trg AS (
    SELECT g.tgrelid,
           string_agg('- `' || g.tgname || '` '
                      || regexp_replace(pg_get_triggerdef(g.oid), '^CREATE (CONSTRAINT )?TRIGGER \S+ ', ''),
                      E'\n' ORDER BY g.tgname) AS txt
    FROM pg_trigger g
    JOIN tbl t ON t.oid = g.tgrelid
    WHERE NOT g.tgisinternal
    GROUP BY g.tgrelid
),
refby AS (
    SELECT k.confrelid,
           string_agg(DISTINCT '`' || src.relname || '`.(' ||
                      (SELECT string_agg(a.attname, ', ' ORDER BY x.ord)
                       FROM unnest(k.conkey) WITH ORDINALITY x(n, ord)
                       JOIN pg_attribute a ON a.attrelid = k.conrelid AND a.attnum = x.n) || ')', ', ') AS txt
    FROM pg_constraint k
    JOIN tbl t ON t.oid = k.confrelid
    JOIN pg_class src ON src.oid = k.conrelid
    WHERE k.contype = 'f'
    GROUP BY k.confrelid
),
rel AS (
    -- Flat relationship list: child.col → parent.col
    SELECT string_agg(line, E'\n' ORDER BY line) AS txt
    FROM (
        SELECT '- `' || src.relname || '`.' ||
               (SELECT string_agg(a.attname, ',' ORDER BY x.ord)
                FROM unnest(k.conkey) WITH ORDINALITY x(n, ord)
                JOIN pg_attribute a ON a.attrelid = k.conrelid AND a.attnum = x.n)
               || ' → `' || dst.relname || '`.' ||
               (SELECT string_agg(a.attname, ',' ORDER BY x.ord)
                FROM unnest(k.confkey) WITH ORDINALITY x(n, ord)
                JOIN pg_attribute a ON a.attrelid = k.confrelid AND a.attnum = x.n)
               || CASE k.confdeltype WHEN 'c' THEN ' (ON DELETE CASCADE)'
                                     WHEN 'n' THEN ' (ON DELETE SET NULL)'
                                     WHEN 'd' THEN ' (ON DELETE SET DEFAULT)'
                                     WHEN 'r' THEN ' (ON DELETE RESTRICT)'
                                     ELSE '' END AS line
        FROM pg_constraint k
        JOIN pg_class src ON src.oid = k.conrelid
        JOIN pg_class dst ON dst.oid = k.confrelid
        WHERE k.contype = 'f' AND k.connamespace = 'app'::regnamespace
    ) s
),
views AS (
    SELECT string_agg('### ' || c.relname || CASE c.relkind WHEN 'm' THEN ' (materialized)' ELSE '' END
                      || E'\n\n```sql\n' || rtrim(pg_get_viewdef(c.oid, true)) || E'\n```',
                      E'\n\n' ORDER BY c.relname) AS txt
    FROM pg_class c
    WHERE c.relnamespace = 'app'::regnamespace AND c.relkind IN ('v', 'm')
),
funcs AS (
    SELECT string_agg('- `' || p.proname || '(' || pg_get_function_identity_arguments(p.oid) || ')` → '
                      || CASE WHEN p.prokind = 'p' THEN 'procedure' ELSE pg_get_function_result(p.oid) END
                      || ' [' || l.lanname || ']',
                      E'\n' ORDER BY p.proname, pg_get_function_identity_arguments(p.oid)) AS txt
    FROM pg_proc p
    JOIN pg_language l ON l.oid = p.prolang
    WHERE p.pronamespace = 'app'::regnamespace
      AND NOT EXISTS (SELECT 1 FROM pg_depend d WHERE d.objid = p.oid AND d.deptype = 'e')
),
body AS (
    SELECT string_agg(
               '## ' || t.relname
               || COALESCE(E'\n\n> ' || replace(obj_description(t.oid, 'pg_class'), E'\n', ' '), '')
               || E'\n\n**Columns**\n' || COALESCE(col.txt, '')
               || COALESCE(E'\n\n**Constraints**\n' || con.txt, '')
               || COALESCE(E'\n\n**Indexes**\n' || idx.txt, '')
               || COALESCE(E'\n\n**Triggers**\n' || trg.txt, '')
               || COALESCE(E'\n\n**Referenced by** ' || refby.txt, ''),
               E'\n\n' ORDER BY t.relname) AS txt,
           string_agg('`' || t.relname || '`', ' · ' ORDER BY t.relname) AS toc,
           count(*) AS n
    FROM tbl t
    LEFT JOIN col   ON col.attrelid  = t.oid
    LEFT JOIN con   ON con.conrelid  = t.oid
    LEFT JOIN idx   ON idx.indrelid  = t.oid
    LEFT JOIN trg   ON trg.tgrelid   = t.oid
    LEFT JOIN refby ON refby.confrelid = t.oid
)
SELECT '# Schema map — `app`' || E'\n\n'
    || '> **GENERATED — do not edit.** Final database after every migration, regenerated with `scripts/db/snapshot.sh --fresh`'
    || ' in the same PR as each new migration (the CI bot commits it if forgotten). Latest migration: '
    || COALESCE((SELECT 'V' || version FROM app.flyway_schema_history
                 WHERE success AND version IS NOT NULL
                 ORDER BY string_to_array(version, '.')::int[] DESC LIMIT 1), '?')
    || '. Read this instead of replaying migrations. Full DDL: [`schema.sql`](schema.sql); seeded rows: [`data.sql`](data.sql).'
    || E'\n\n' || body.n || ' tables.' || E'\n\n' || body.toc
    || E'\n\n# Relationships (child → parent)\n\n' || COALESCE(rel.txt, '')
    || E'\n\n# Tables\n\n' || body.txt
    || E'\n\n# Views\n\n' || COALESCE(views.txt, '_none_')
    || E'\n\n# Functions\n\n' || COALESCE(funcs.txt, '_none_')
FROM body, rel, views, funcs;
