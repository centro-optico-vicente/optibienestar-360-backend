SET search_path TO app, public;

-- V35: the promoter-attribution link surface (v2 PDF #4 "Módulo de Promotores").
--
-- Two things ship here, both backing the code in the same PR:
--   1. member_promoter_assignments — an insert-only audit trail for the
--      permanent client↔promoter link (PDF 2.a). members.promoter_id is set
--      once at enrollment and only ever changes through the admin endpoint
--      POST /v1/admin/members/{uuid}/assign-promoter; every such change writes
--      one row here (from → to, actor, reason) so the link's history is auditable.
--   2. two permissions:
--        - MEMBER_ASSIGN_PROMOTER (MEMBERS domain) — gates the reassignment endpoint.
--        - PROMOTER_VIEW_OWN (PROMOTERS domain) — gates the promoter self-service
--          dashboard GET /v1/promoter/me.
--      The V30 trigger auto-grants both to SYSTEM on insert; the grants below add
--      them to the business roles.


-- ─── 1. Audit table ─────────────────────────────────────────────────────────
-- Columns mirror BaseEntity (id/uuid/is_active/status/audit) so the JPA entity
-- validates clean; the row is insert-only by service convention (never updated).
CREATE TABLE member_promoter_assignments
(
    member_promoter_assignments_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                           UUID         NOT NULL UNIQUE,

    member_id                      BIGINT       NOT NULL REFERENCES members (members_id),
    -- NULL when the member had no promoter before (first assignment / back-fill row).
    from_promoter_id               BIGINT       REFERENCES promoters (promoters_id),
    to_promoter_id                 BIGINT       NOT NULL REFERENCES promoters (promoters_id),
    -- The admin who performed the reassignment; NULL only for system-driven moves.
    actor_user_id                  BIGINT       REFERENCES users (users_id),
    reason                         TEXT         NOT NULL,

    is_active                      BOOLEAN      NOT NULL DEFAULT TRUE,
    status                         VARCHAR(50),
    created_at                     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by                     UUID,
    updated_by                     UUID
);

-- Chronological history per member (newest first) for an audit view.
CREATE INDEX idx_member_promoter_assignments_member
    ON member_promoter_assignments (member_id, created_at DESC);


-- ─── 2. Permissions ─────────────────────────────────────────────────────────
-- The V30 trigger auto-grants each to SYSTEM on insert.
INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('MEMBER_ASSIGN_PROMOTER', 'MEMBERS',    'Reasignar el promotor de un afiliado (enlace permanente)'),
    ('PROMOTER_VIEW_OWN',      'PROMOTERS',  'Ver el panel propio del promotor (cartera y comisiones)')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code;


-- ADMINISTRADOR: full business access — gets both.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name = 'ADMINISTRADOR'
  AND p.name IN ('MEMBER_ASSIGN_PROMOTER', 'PROMOTER_VIEW_OWN')
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- PROMOTOR: self-service dashboard only (never reassigns their own attribution).
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name = 'PROMOTOR'
  AND p.name = 'PROMOTER_VIEW_OWN'
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- ─── 3. Fail loudly rather than migrate into a half-applied state ────────────
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'MEMBER_ASSIGN_PROMOTER') THEN
        RAISE EXCEPTION 'V35: MEMBER_ASSIGN_PROMOTER was not created';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'PROMOTER_VIEW_OWN') THEN
        RAISE EXCEPTION 'V35: PROMOTER_VIEW_OWN was not created';
    END IF;

    -- SYSTEM must hold both (V30 trigger).
    IF EXISTS (
        SELECT 1 FROM permissions p
        WHERE p.name IN ('MEMBER_ASSIGN_PROMOTER', 'PROMOTER_VIEW_OWN')
          AND NOT EXISTS (
            SELECT 1 FROM role_permissions rp
                     JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'SYSTEM'
            WHERE rp.permission_id = p.permissions_id)
    ) THEN
        RAISE EXCEPTION 'V35: SYSTEM did not receive the new permissions (V30 trigger?)';
    END IF;

    -- ADMINISTRADOR must hold both.
    IF EXISTS (
        SELECT 1 FROM permissions p
        WHERE p.name IN ('MEMBER_ASSIGN_PROMOTER', 'PROMOTER_VIEW_OWN')
          AND NOT EXISTS (
            SELECT 1 FROM role_permissions rp
                     JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'ADMINISTRADOR'
            WHERE rp.permission_id = p.permissions_id)
    ) THEN
        RAISE EXCEPTION 'V35: ADMINISTRADOR is missing one of the new permissions';
    END IF;

    -- PROMOTOR must hold PROMOTER_VIEW_OWN.
    IF NOT EXISTS (
        SELECT 1 FROM role_permissions rp
                 JOIN roles r       ON r.roles_id = rp.role_id AND r.name = 'PROMOTOR'
                 JOIN permissions p  ON p.permissions_id = rp.permission_id AND p.name = 'PROMOTER_VIEW_OWN'
    ) THEN
        RAISE EXCEPTION 'V35: PROMOTOR did not receive PROMOTER_VIEW_OWN';
    END IF;
END $$;
