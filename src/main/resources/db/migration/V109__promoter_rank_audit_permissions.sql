SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V109: audit permissions for `promoter_rank` — V101 wired the entity into
-- `entity_config` (so AuditModal/data-changes/reports pick it up) but never
-- minted its granular <DOMAIN>_RECORD_AUDIT_VIEW/<DOMAIN>_REPORT_AUDIT_VIEW
-- pair, unlike every sibling catalog (V66/V71/V73/V75/V77). Without them,
-- a role holding only PROMOTER_RANK_VIEW_ALL (not the blanket
-- AUDIT_VIEW_ALL/REPORT_AUDIT_VIEW_ALL) can manage ranks but can never see
-- their change/report history. Minted directly with the RECORD name (no
-- rename step needed — V77 already retired the old <DOMAIN>_AUDIT_VIEW
-- shape). Same PROMOTERS domain as PROMOTER_RECORD_AUDIT_VIEW (V66/V77).
--
-- SYSTEM receives both automatically via the V30 trigger. ADMINISTRADOR gets
-- explicit grants for the same reason as V64/V66/V71/V73/V101.
-- ────────────────────────────────────────────────────────────────────────────

INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('PROMOTER_RANK_RECORD_AUDIT_VIEW', 'PROMOTERS', 'Ver el historial de cambios de un cargo jerárquico de promotor'),
    ('PROMOTER_RANK_REPORT_AUDIT_VIEW', 'PROMOTERS', 'Ver el historial de reportes generados de cargos jerárquicos de promotor')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name IN ('SYSTEM', 'ADMINISTRADOR')
  AND p.name IN ('PROMOTER_RANK_RECORD_AUDIT_VIEW', 'PROMOTER_RANK_REPORT_AUDIT_VIEW')
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- ─── Fail loudly rather than migrate into a half-applied state ──────────────
DO $$
DECLARE
    new_permission TEXT;
BEGIN
    FOREACH new_permission IN ARRAY ARRAY[
        'PROMOTER_RANK_RECORD_AUDIT_VIEW', 'PROMOTER_RANK_REPORT_AUDIT_VIEW'
    ]
    LOOP
        IF NOT EXISTS (SELECT 1 FROM permissions WHERE name = new_permission) THEN
            RAISE EXCEPTION 'V109: % was not created', new_permission;
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM role_permissions rp
                     JOIN roles r       ON r.roles_id = rp.role_id AND r.name = 'SYSTEM'
                     JOIN permissions p ON p.permissions_id = rp.permission_id AND p.name = new_permission
        ) THEN
            RAISE EXCEPTION 'V109: SYSTEM did not receive % (V30 trigger?)', new_permission;
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM role_permissions rp
                     JOIN roles r       ON r.roles_id = rp.role_id AND r.name = 'ADMINISTRADOR'
                     JOIN permissions p ON p.permissions_id = rp.permission_id AND p.name = new_permission
        ) THEN
            RAISE EXCEPTION 'V109: ADMINISTRADOR did not receive %', new_permission;
        END IF;
    END LOOP;
END $$;
