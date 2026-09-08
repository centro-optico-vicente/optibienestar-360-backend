SET search_path TO app, public;

-- ============================================================================
-- V101: promoter hierarchy schema (hub plan
-- ".ai/plans/2026-09-07-hierarchical-commissions-plan.md", PR1 "Schema de
-- jerarquía"). Introduces the "cargo" (rank) axis for promoters — independent
-- of promoter_type (Independiente/Empleado/Aliado) — plus the supervisor link
-- and its insert-only assignment history.
--
-- Three things ship here:
--   1. promoter_ranks — a small catalog (mirrors promoter_types) with
--      hierarchy_level (int) as the sole comparison key the whole hierarchy
--      engine uses — never a hardcoded rank name — so N levels are supported
--      without further schema changes. Seed: PROMOTOR(1)/SUPERVISOR(2)/
--      COORDINADOR(3).
--   2. promoters.rank_id (FK, backfilled to PROMOTOR for existing rows, then
--      NOT NULL) + promoters.supervisor_id (FK to promoters, nullable — NULL
--      means "top of the chain, no supervisor").
--   3. promoter_supervisor_assignments — insert-only audit trail of every
--      supervisor (re)assignment, structurally identical to
--      member_promoter_assignments (V35): no effective_from/to columns, the
--      "vigente" row for a cut is the most recent one with
--      created_at <= asOf. This is what lets a commission cut always use the
--      supervisor who was actually in charge at that time, not today's.
--
-- Permissions ride the existing PROMOTERS domain (V35) rather than opening a
-- new one: PROMOTER_RANK_VIEW_ALL/_CREATE/_UPDATE/_DELETE (catalog CRUD) and
-- PROMOTER_ASSIGN_SUPERVISOR (the reassignment action), granted to
-- SYSTEM/ADMINISTRADOR only for now.
-- ============================================================================


-- ─── 1. promoter_ranks catalog ──────────────────────────────────────────────
CREATE TABLE promoter_ranks
(
    promoter_ranks_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid              UUID         NOT NULL UNIQUE,

    code              VARCHAR(40)  NOT NULL UNIQUE,
    name              VARCHAR(100) NOT NULL UNIQUE,
    -- The genericity key: every hierarchy comparison ("is this promoter's
    -- candidate supervisor strictly above them?") reads this integer, never
    -- the code/name. Higher = further up the chain.
    hierarchy_level   INTEGER      NOT NULL UNIQUE,
    -- Configurable cap on direct subordinates for this rank (NULL = no cap,
    -- e.g. PROMOTOR itself never supervises anyone). Enforced by
    -- PromoterHierarchyService.assignSupervisor, not a DB constraint — the
    -- count depends on the vigente-at-now supervisor link, which the DB
    -- can't cheaply check per-insert.
    max_subordinates  INTEGER,
    description       VARCHAR(200),

    is_active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by        UUID,
    updated_by        UUID
);

CREATE TRIGGER trg_promoter_ranks_updated_at
    BEFORE UPDATE ON promoter_ranks
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

INSERT INTO promoter_ranks (uuid, code, name, hierarchy_level, max_subordinates, description)
VALUES (gen_random_uuid(), 'PROMOTOR',    'Promotor',    1, NULL, 'Vende directamente — no supervisa a nadie'),
       (gen_random_uuid(), 'SUPERVISOR',  'Supervisor',  2, 10,   'Supervisa hasta 10 promotores/asesores'),
       (gen_random_uuid(), 'COORDINADOR', 'Coordinador', 3, 5,    'Coordina hasta 5 supervisores');


-- ─── 2. promoters.rank_id / supervisor_id ───────────────────────────────────
ALTER TABLE promoters
    ADD COLUMN rank_id       BIGINT REFERENCES promoter_ranks (promoter_ranks_id),
    ADD COLUMN supervisor_id BIGINT REFERENCES promoters (promoters_id);

-- Backfill: every existing promoter starts at the base rank (no assumption
-- about who "should" be a Supervisor/Coordinador — that's an explicit
-- promotion the admin performs afterward via the new endpoint).
UPDATE promoters
SET rank_id = (SELECT promoter_ranks_id FROM promoter_ranks WHERE code = 'PROMOTOR')
WHERE rank_id IS NULL;

ALTER TABLE promoters
    ALTER COLUMN rank_id SET NOT NULL;

CREATE INDEX idx_promoters_rank ON promoters (rank_id);
CREATE INDEX idx_promoters_supervisor ON promoters (supervisor_id);


-- ─── 3. promoter_supervisor_assignments (insert-only history, mirrors V35) ──
CREATE TABLE promoter_supervisor_assignments
(
    promoter_supervisor_assignments_id BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                                UUID        NOT NULL UNIQUE,

    promoter_id                        BIGINT      NOT NULL REFERENCES promoters (promoters_id),
    -- NULL when the promoter had no supervisor before (first assignment / top of chain).
    from_supervisor_id                 BIGINT      REFERENCES promoters (promoters_id),
    to_supervisor_id                   BIGINT      REFERENCES promoters (promoters_id),
    actor_user_id                      BIGINT      REFERENCES users (users_id),
    reason                             TEXT        NOT NULL,

    is_active                          BOOLEAN     NOT NULL DEFAULT TRUE,
    status                             VARCHAR(50),
    created_at                         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by                         UUID,
    updated_by                         UUID
);

-- "Who was Juan's vigente supervisor as of a given cut" — newest-first per promoter.
CREATE INDEX idx_promoter_supervisor_assignments_promoter
    ON promoter_supervisor_assignments (promoter_id, created_at DESC);


-- ─── 4. Permissions (PROMOTERS domain, V35) ─────────────────────────────────
INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('PROMOTER_RANK_VIEW_ALL',        'PROMOTERS', 'Ver los cargos/rangos jerárquicos de promotor'),
    ('PROMOTER_RANK_CREATE',          'PROMOTERS', 'Crear cargos/rangos jerárquicos de promotor'),
    ('PROMOTER_RANK_UPDATE',          'PROMOTERS', 'Editar cargos/rangos jerárquicos de promotor'),
    ('PROMOTER_RANK_DELETE',          'PROMOTERS', 'Desactivar cargos/rangos jerárquicos de promotor'),
    ('PROMOTER_ASSIGN_SUPERVISOR',    'PROMOTERS', 'Asignar/reasignar el supervisor de un promotor')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name IN ('SYSTEM', 'ADMINISTRADOR')
  AND p.name IN (
      'PROMOTER_RANK_VIEW_ALL', 'PROMOTER_RANK_CREATE', 'PROMOTER_RANK_UPDATE', 'PROMOTER_RANK_DELETE',
      'PROMOTER_ASSIGN_SUPERVISOR'
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- ─── 5. Audit config (fail-safe if missing, but onboard properly) ──────────
-- Table renamed audit_entity_config → entity_config by V80.
INSERT INTO entity_config (entity_key, display_name, table_name) VALUES
    ('promoter_rank',       'Cargos jerárquicos de promotor',    'promoter_ranks'),
    ('promoter_supervisor', 'Asignación de supervisor',          'promoter_supervisor_assignments')
ON CONFLICT (entity_key) DO NOTHING;


-- ─── 6. Fail loudly rather than migrate into a half-applied state ──────────
DO $$
BEGIN
    IF (SELECT COUNT(*) FROM promoter_ranks) < 3 THEN
        RAISE EXCEPTION 'V101: promoter_ranks seed did not insert the 3 base ranks';
    END IF;

    IF EXISTS (SELECT 1 FROM promoters WHERE rank_id IS NULL) THEN
        RAISE EXCEPTION 'V101: some promoters were not backfilled with a rank_id';
    END IF;

    IF EXISTS (
        SELECT 1 FROM (VALUES
            ('PROMOTER_RANK_VIEW_ALL'), ('PROMOTER_RANK_CREATE'), ('PROMOTER_RANK_UPDATE'),
            ('PROMOTER_RANK_DELETE'), ('PROMOTER_ASSIGN_SUPERVISOR')
        ) AS want(name)
        WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.name = want.name)
    ) THEN
        RAISE EXCEPTION 'V101: one or more new permissions were not created';
    END IF;

    IF EXISTS (
        SELECT 1 FROM permissions p
        WHERE p.name IN (
            'PROMOTER_RANK_VIEW_ALL', 'PROMOTER_RANK_CREATE', 'PROMOTER_RANK_UPDATE',
            'PROMOTER_RANK_DELETE', 'PROMOTER_ASSIGN_SUPERVISOR'
        )
          AND NOT EXISTS (
            SELECT 1 FROM role_permissions rp
                     JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'SYSTEM'
            WHERE rp.permission_id = p.permissions_id)
    ) THEN
        RAISE EXCEPTION 'V101: SYSTEM did not receive the new permissions (V30 trigger?)';
    END IF;

    IF EXISTS (
        SELECT 1 FROM permissions p
        WHERE p.name IN (
            'PROMOTER_RANK_VIEW_ALL', 'PROMOTER_RANK_CREATE', 'PROMOTER_RANK_UPDATE',
            'PROMOTER_RANK_DELETE', 'PROMOTER_ASSIGN_SUPERVISOR'
        )
          AND NOT EXISTS (
            SELECT 1 FROM role_permissions rp
                     JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'ADMINISTRADOR'
            WHERE rp.permission_id = p.permissions_id)
    ) THEN
        RAISE EXCEPTION 'V101: ADMINISTRADOR did not receive one of the new permissions';
    END IF;
END $$;
