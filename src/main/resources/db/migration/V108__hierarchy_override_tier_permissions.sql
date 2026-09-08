SET search_path TO app, public;

-- ============================================================================
-- V108: admin CRUD for hierarchy_override_tiers (V102) — the Supervisor/
-- Coordinador override bands had no admin surface of their own; only the
-- migration seed configured them. Same 4-permission-per-catalog shape as
-- COMMISSION_TIER_* (V79), under the same COMMISSIONS domain.
-- ============================================================================

INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('HIERARCHY_OVERRIDE_TIER_VIEW_ALL', 'COMMISSIONS', 'Ver bandas de override jerárquico'),
    ('HIERARCHY_OVERRIDE_TIER_CREATE',   'COMMISSIONS', 'Crear bandas de override jerárquico'),
    ('HIERARCHY_OVERRIDE_TIER_UPDATE',   'COMMISSIONS', 'Editar bandas de override jerárquico'),
    ('HIERARCHY_OVERRIDE_TIER_DELETE',   'COMMISSIONS', 'Eliminar bandas de override jerárquico')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name IN ('SYSTEM', 'ADMINISTRADOR')
  AND p.name IN (
      'HIERARCHY_OVERRIDE_TIER_VIEW_ALL', 'HIERARCHY_OVERRIDE_TIER_CREATE',
      'HIERARCHY_OVERRIDE_TIER_UPDATE', 'HIERARCHY_OVERRIDE_TIER_DELETE'
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- ─── Fail loudly rather than migrate into a half-applied state ──────────────
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM (VALUES
            ('HIERARCHY_OVERRIDE_TIER_VIEW_ALL'), ('HIERARCHY_OVERRIDE_TIER_CREATE'),
            ('HIERARCHY_OVERRIDE_TIER_UPDATE'), ('HIERARCHY_OVERRIDE_TIER_DELETE')
        ) AS want(name)
        WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.name = want.name)
    ) THEN
        RAISE EXCEPTION 'V108: one or more new permissions were not created';
    END IF;

    IF EXISTS (
        SELECT 1 FROM permissions p
        WHERE p.name LIKE 'HIERARCHY_OVERRIDE_TIER_%'
          AND NOT EXISTS (
            SELECT 1 FROM role_permissions rp
                     JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'SYSTEM'
            WHERE rp.permission_id = p.permissions_id)
    ) THEN
        RAISE EXCEPTION 'V108: SYSTEM did not receive one of the new permissions (V30 trigger?)';
    END IF;

    IF EXISTS (
        SELECT 1 FROM permissions p
        WHERE p.name LIKE 'HIERARCHY_OVERRIDE_TIER_%'
          AND NOT EXISTS (
            SELECT 1 FROM role_permissions rp
                     JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'ADMINISTRADOR'
            WHERE rp.permission_id = p.permissions_id)
    ) THEN
        RAISE EXCEPTION 'V108: ADMINISTRADOR did not receive one of the new permissions';
    END IF;
END $$;
