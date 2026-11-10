SET search_path TO app, public;

-- ============================================================================
-- V234: advisor-assisted affiliation as a DRAFT saved step by step (hub ADR
-- 0037 §5). No schema change: V233 already allows DRAFT requests, and an
-- ASSISTED request never needed a login (the person is created on approval).
-- The assisted draft is an affiliation_requests row with channel = 'ASSISTED',
-- status = 'DRAFT', user_id NULL and promoter_id = the advisor.
--
-- Two new granular permissions (AFFILIATION_REQUESTS domain), one per action,
-- granted to the roles that already hold AFFILIATION_REQUEST_CREATE_ASSISTED
-- (ASESOR, ADMINISTRADOR; SYSTEM explicitly as well). OPERADOR gets none.
--   AFFILIATION_REQUEST_DRAFT_UPDATE_ASSISTED  save a step of the caller's draft
--   AFFILIATION_REQUEST_DRAFT_SUBMIT_ASSISTED  send the caller's draft to review
-- Starting a draft reuses AFFILIATION_REQUEST_CREATE_ASSISTED; listing and
-- reading the caller's own drafts reuses AFFILIATION_REQUEST_VIEW_OWN.
-- ============================================================================

INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('AFFILIATION_REQUEST_DRAFT_UPDATE_ASSISTED', 'Guardar por pasos un borrador de afiliación asistida propio (portal del asesor)'),
    ('AFFILIATION_REQUEST_DRAFT_SUBMIT_ASSISTED', 'Enviar a revisión un borrador de afiliación asistida propio (portal del asesor)')
) AS v(name, description)
JOIN permission_domains pd ON pd.code = 'AFFILIATION_REQUESTS'
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
CROSS JOIN permissions p
WHERE r.name IN ('SYSTEM', 'ADMINISTRADOR', 'ASESOR')
  AND p.name IN ('AFFILIATION_REQUEST_DRAFT_UPDATE_ASSISTED', 'AFFILIATION_REQUEST_DRAFT_SUBMIT_ASSISTED')
ON CONFLICT (role_id, permission_id) DO NOTHING;

DO $$
BEGIN
    IF (SELECT COUNT(*) FROM permissions
        WHERE name IN ('AFFILIATION_REQUEST_DRAFT_UPDATE_ASSISTED', 'AFFILIATION_REQUEST_DRAFT_SUBMIT_ASSISTED')) <> 2 THEN
        RAISE EXCEPTION 'V234: assisted draft permissions were not created (AFFILIATION_REQUESTS domain missing?)';
    END IF;
    IF (SELECT COUNT(*) FROM role_permissions rp
        JOIN roles r ON r.roles_id = rp.role_id AND r.name IN ('SYSTEM', 'ADMINISTRADOR', 'ASESOR')
        JOIN permissions p ON p.permissions_id = rp.permission_id
         AND p.name IN ('AFFILIATION_REQUEST_DRAFT_UPDATE_ASSISTED', 'AFFILIATION_REQUEST_DRAFT_SUBMIT_ASSISTED')) <> 6 THEN
        RAISE EXCEPTION 'V234: SYSTEM/ADMINISTRADOR/ASESOR did not receive the assisted draft permissions';
    END IF;
END $$;
