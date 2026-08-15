SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V54: permission catalog for the attached_files mechanism (V52), scoped to
-- the two domains that get a wired controller in this vertical: MEMBERS and
-- ALLIES (spec 08-storage-r2.md §4, §4.2).
--
-- MEMBER_UPLOAD_DOCUMENT already exists (seeded unused in V6) — reused here
-- instead of duplicating it as MEMBER_DOCUMENT_UPLOAD.
--
-- Each domain gets 4 independent authorities so a role can hold any subset
-- (view the record without seeing its files, view files without uploading,
-- upload without deleting, etc.):
--   <DOMAIN>_DOCUMENT_VIEW_ALL / _VIEW_OWN / _UPLOAD / _DELETE
--
-- SYSTEM receives every new permission automatically via the V30 trigger —
-- not repeated here. ADMINISTRADOR/OPERADOR/OPERADOR_MEDICO get explicit
-- grants because their V6 INSERTs were one-time snapshots, not triggers.
--
-- AFILIADO/ALIADO get *_VIEW_OWN only (mirrors their existing MEMBER_VIEW_OWN /
-- ALLY_VIEW_OWN) — self-service upload/delete of one's own compliance
-- documents (cédula, contrato) is intentionally out of scope: allowing a
-- member/ally to delete their own submitted ID document would undermine the
-- validation it exists for. That would need a separate, explicit decision.
-- ────────────────────────────────────────────────────────────────────────────

INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('MEMBER_DOCUMENT_VIEW_ALL', 'MEMBERS', 'Ver todos los documentos adjuntos de cualquier afiliado'),
    ('MEMBER_DOCUMENT_VIEW_OWN', 'MEMBERS', 'Ver los documentos adjuntos propios y los compartidos'),
    ('MEMBER_DOCUMENT_DELETE',   'MEMBERS', 'Eliminar documentos adjuntos de un afiliado'),

    ('ALLY_UPLOAD_DOCUMENT',   'ALLIES', 'Cargar documentos de un aliado (contrato, RIF, etc.)'),
    ('ALLY_DOCUMENT_VIEW_ALL', 'ALLIES', 'Ver todos los documentos adjuntos de cualquier aliado'),
    ('ALLY_DOCUMENT_VIEW_OWN', 'ALLIES', 'Ver los documentos adjuntos propios y los compartidos'),
    ('ALLY_DOCUMENT_DELETE',   'ALLIES', 'Eliminar documentos adjuntos de un aliado')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code;


-- ADMINISTRADOR: full access to all new document permissions.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name = 'ADMINISTRADOR'
  AND p.name IN (
      'MEMBER_DOCUMENT_VIEW_ALL', 'MEMBER_DOCUMENT_VIEW_OWN', 'MEMBER_DOCUMENT_DELETE',
      'ALLY_UPLOAD_DOCUMENT', 'ALLY_DOCUMENT_VIEW_ALL', 'ALLY_DOCUMENT_VIEW_OWN', 'ALLY_DOCUMENT_DELETE'
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- OPERADOR / OPERADOR_MEDICO: same operational access as ADMINISTRADOR here —
-- validating recaudos is day-to-day operational work, not an admin-only task.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name IN ('OPERADOR', 'OPERADOR_MEDICO')
  AND p.name IN (
      'MEMBER_DOCUMENT_VIEW_ALL', 'MEMBER_DOCUMENT_VIEW_OWN', 'MEMBER_DOCUMENT_DELETE',
      'ALLY_UPLOAD_DOCUMENT', 'ALLY_DOCUMENT_VIEW_ALL', 'ALLY_DOCUMENT_VIEW_OWN', 'ALLY_DOCUMENT_DELETE'
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- AFILIADO: view own member documents only (mirrors MEMBER_VIEW_OWN).
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name = 'AFILIADO'
  AND p.name = 'MEMBER_DOCUMENT_VIEW_OWN'
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- ALIADO: view own ally documents only (mirrors ALLY_VIEW_OWN).
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name = 'ALIADO'
  AND p.name = 'ALLY_DOCUMENT_VIEW_OWN'
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- ─── Fail loudly rather than migrate into a half-applied state ──────────────
DO $$
DECLARE
    new_permission TEXT;
BEGIN
    FOREACH new_permission IN ARRAY ARRAY[
        'MEMBER_DOCUMENT_VIEW_ALL', 'MEMBER_DOCUMENT_VIEW_OWN', 'MEMBER_DOCUMENT_DELETE',
        'ALLY_UPLOAD_DOCUMENT', 'ALLY_DOCUMENT_VIEW_ALL', 'ALLY_DOCUMENT_VIEW_OWN', 'ALLY_DOCUMENT_DELETE'
    ]
    LOOP
        IF NOT EXISTS (SELECT 1 FROM permissions WHERE name = new_permission) THEN
            RAISE EXCEPTION 'V54: % was not created', new_permission;
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM role_permissions rp
                     JOIN roles r      ON r.roles_id = rp.role_id AND r.name = 'SYSTEM'
                     JOIN permissions p ON p.permissions_id = rp.permission_id AND p.name = new_permission
        ) THEN
            RAISE EXCEPTION 'V54: SYSTEM did not receive % (V30 trigger?)', new_permission;
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM role_permissions rp
                     JOIN roles r      ON r.roles_id = rp.role_id AND r.name = 'ADMINISTRADOR'
                     JOIN permissions p ON p.permissions_id = rp.permission_id AND p.name = new_permission
        ) THEN
            RAISE EXCEPTION 'V54: ADMINISTRADOR did not receive %', new_permission;
        END IF;
    END LOOP;
END $$;
