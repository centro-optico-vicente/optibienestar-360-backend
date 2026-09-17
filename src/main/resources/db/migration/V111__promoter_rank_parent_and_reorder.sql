SET search_path TO app, public;

-- ============================================================================
-- V111: editable parent/superior relation + reorderable hierarchy_level for
-- promoter_ranks (V101). Closes two gaps in the "cargo" catalog:
--   1. There was no real parent/child column — the "who is above whom" chain
--      was implied purely by comparing hierarchy_level at query time. This
--      adds parent_rank_id, pointing at the IMMEDIATE SUPERIOR (the next
--      active rank with a strictly greater hierarchy_level) — same "boss"
--      semantics as promoters.supervisor_id. The rank with the highest
--      hierarchy_level (today COORDINADOR) has parent_rank_id = NULL.
--   2. hierarchy_level was seeded densely (1/2/3) and its UNIQUE constraint
--      was NOT DEFERRABLE, so no admin-driven reorder/insert-between could
--      ever be done in a single transaction without a temporary collision.
--      This reseeds existing ranks into gaps of 10 (10/20/30) and makes the
--      uniqueness constraint DEFERRABLE INITIALLY DEFERRED so
--      PromoterRankService.reorder()/create() can renumber several rows in
--      one transaction, checked only at COMMIT.
--
-- Also mints PROMOTER_RANK_REORDER (PROMOTERS domain, same pattern as
-- PROMOTER_RANK_UPDATE from V101), granted to SYSTEM/ADMINISTRADOR.
-- ============================================================================


-- ─── 1. parent_rank_id column ───────────────────────────────────────────────
ALTER TABLE promoter_ranks
    ADD COLUMN parent_rank_id BIGINT REFERENCES promoter_ranks (promoter_ranks_id);

CREATE INDEX idx_promoter_ranks_parent ON promoter_ranks (parent_rank_id);

-- Backfill: every active rank points at the active rank with the immediate
-- next-higher hierarchy_level (its "boss"). The top rank (no active rank
-- above it) is left NULL.
UPDATE promoter_ranks pr
SET parent_rank_id = boss.promoter_ranks_id
FROM (
    SELECT pr1.promoter_ranks_id AS child_id,
           (SELECT pr2.promoter_ranks_id
            FROM promoter_ranks pr2
            WHERE pr2.is_active = TRUE
              AND pr2.hierarchy_level > pr1.hierarchy_level
            ORDER BY pr2.hierarchy_level ASC
            LIMIT 1) AS promoter_ranks_id
    FROM promoter_ranks pr1
    WHERE pr1.is_active = TRUE
) boss
WHERE pr.promoter_ranks_id = boss.child_id
  AND boss.promoter_ranks_id IS NOT NULL;


-- ─── 2. reseed hierarchy_level into gaps of 10 ──────────────────────────────
-- Frees room for future inserts (e.g. level 15 between 10 and 20) without a
-- full renumbering pass. Order-preserving (multiplying by a positive constant
-- never changes relative order), so it is safe to run after the backfill
-- above, which already resolved parent_rank_id from the pre-reseed values.
UPDATE promoter_ranks SET hierarchy_level = hierarchy_level * 10;


-- ─── 3. make the hierarchy_level uniqueness constraint deferrable ───────────
-- Column-level "INTEGER NOT NULL UNIQUE" in V101 created the default-named
-- table constraint below. Recreated as DEFERRABLE INITIALLY DEFERRED so a
-- reorder/insert-between renumbering several rows in one statement/txn is
-- only checked at COMMIT, not after each row.
ALTER TABLE promoter_ranks
    DROP CONSTRAINT promoter_ranks_hierarchy_level_key;

ALTER TABLE promoter_ranks
    ADD CONSTRAINT promoter_ranks_hierarchy_level_key UNIQUE (hierarchy_level)
        DEFERRABLE INITIALLY DEFERRED;


-- ─── 4. PROMOTER_RANK_REORDER permission (PROMOTERS domain, V101 pattern) ──
INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('PROMOTER_RANK_REORDER', 'PROMOTERS', 'Reordenar/insertar cargos jerárquicos de promotor')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name IN ('SYSTEM', 'ADMINISTRADOR')
  AND p.name = 'PROMOTER_RANK_REORDER'
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- ─── 5. Fail loudly rather than migrate into a half-applied state ──────────
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM promoter_ranks
        WHERE is_active = TRUE
          AND parent_rank_id IS NULL
          AND hierarchy_level < (SELECT MAX(hierarchy_level) FROM promoter_ranks WHERE is_active = TRUE)
    ) THEN
        RAISE EXCEPTION 'V111: an active non-top promoter_rank was left without a parent_rank_id';
    END IF;

    IF (SELECT COUNT(*) FROM promoter_ranks WHERE is_active = TRUE AND parent_rank_id IS NULL) <> 1 THEN
        RAISE EXCEPTION 'V111: expected exactly one active top-of-chain promoter_rank (parent_rank_id IS NULL)';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'promoter_ranks_hierarchy_level_key' AND condeferrable
    ) THEN
        RAISE EXCEPTION 'V111: promoter_ranks_hierarchy_level_key was not recreated as DEFERRABLE';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'PROMOTER_RANK_REORDER') THEN
        RAISE EXCEPTION 'V111: PROMOTER_RANK_REORDER was not created';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM role_permissions rp
                 JOIN roles r       ON r.roles_id = rp.role_id AND r.name = 'SYSTEM'
                 JOIN permissions p ON p.permissions_id = rp.permission_id AND p.name = 'PROMOTER_RANK_REORDER'
    ) THEN
        RAISE EXCEPTION 'V111: SYSTEM did not receive PROMOTER_RANK_REORDER (V30 trigger?)';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM role_permissions rp
                 JOIN roles r       ON r.roles_id = rp.role_id AND r.name = 'ADMINISTRADOR'
                 JOIN permissions p ON p.permissions_id = rp.permission_id AND p.name = 'PROMOTER_RANK_REORDER'
    ) THEN
        RAISE EXCEPTION 'V111: ADMINISTRADOR did not receive PROMOTER_RANK_REORDER';
    END IF;
END $$;
