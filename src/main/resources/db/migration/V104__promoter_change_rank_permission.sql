SET search_path TO app, public;

-- ============================================================================
-- V104: PROMOTER_CHANGE_RANK — gates POST
-- /v1/admin/promoters/{uuid}/change-rank (hub plan
-- ".ai/plans/2026-09-07-hierarchical-commissions-plan.md" §1 follow-up).
-- Separate from PROMOTER_ASSIGN_SUPERVISOR (V101): changing a promoter's
-- cargo affects which commission/override tiers apply to them, a more
-- sensitive action than a plain supervisor reassignment, so it gets its own
-- permission rather than reusing that one.
-- ============================================================================

INSERT INTO permissions (name, domain_id, description)
SELECT 'PROMOTER_CHANGE_RANK', pd.permission_domains_id, 'Ascender o degradar el cargo jerárquico de un promotor'
FROM permission_domains pd
WHERE pd.code = 'PROMOTERS';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name IN ('SYSTEM', 'ADMINISTRADOR')
  AND p.name = 'PROMOTER_CHANGE_RANK'
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- ─── Fail loudly rather than migrate into a half-applied state ──────────────
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'PROMOTER_CHANGE_RANK') THEN
        RAISE EXCEPTION 'V104: PROMOTER_CHANGE_RANK was not created';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM role_permissions rp
                 JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'SYSTEM'
                 JOIN permissions p ON p.permissions_id = rp.permission_id AND p.name = 'PROMOTER_CHANGE_RANK'
    ) THEN
        RAISE EXCEPTION 'V104: SYSTEM did not receive PROMOTER_CHANGE_RANK (V30 trigger?)';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM role_permissions rp
                 JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'ADMINISTRADOR'
                 JOIN permissions p ON p.permissions_id = rp.permission_id AND p.name = 'PROMOTER_CHANGE_RANK'
    ) THEN
        RAISE EXCEPTION 'V104: ADMINISTRADOR did not receive PROMOTER_CHANGE_RANK';
    END IF;
END $$;
