SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V57: dedicated permissions for AllyService catalog image management
-- (spec 08-storage-r2.md §5), instead of reusing ALLY_UPDATE.
--
-- ALLY_UPDATE governs editing a service's business fields (name, price,
-- description, appointment flag). Bundling image upload/delete into it would
-- mean "can edit pricing" and "can manage the public image" are always the
-- same authority — breaking the independent-authorities principle already
-- applied to member/ally documents (§4.2), where a content-moderation role
-- should be able to manage images without touching commercial fields.
--
-- No *_VIEW permission is added: the image lives in the PUBLIC bucket, served
-- without presigning once the service is published && approved — there is
-- nothing to authorize for "viewing" it. Anyone with ALLY_VIEW_ALL already
-- sees imageKey/imageUrl as a regular field on the service detail.
-- ────────────────────────────────────────────────────────────────────────────

INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('ALLY_SERVICE_IMAGE_UPLOAD', 'ALLIES', 'Cargar/reemplazar la imagen pública de un servicio de catálogo'),
    ('ALLY_SERVICE_IMAGE_DELETE', 'ALLIES', 'Eliminar la imagen pública de un servicio de catálogo')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code;


-- ADMINISTRADOR: explicit grant (V6's blanket grant was a one-time snapshot).
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name = 'ADMINISTRADOR'
  AND p.name IN ('ALLY_SERVICE_IMAGE_UPLOAD', 'ALLY_SERVICE_IMAGE_DELETE')
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- OPERADOR / OPERADOR_MEDICO: same operational access as ALLY_UPDATE already has.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name IN ('OPERADOR', 'OPERADOR_MEDICO')
  AND p.name IN ('ALLY_SERVICE_IMAGE_UPLOAD', 'ALLY_SERVICE_IMAGE_DELETE')
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- ─── Fail loudly rather than migrate into a half-applied state ──────────────
DO $$
DECLARE
    new_permission TEXT;
BEGIN
    FOREACH new_permission IN ARRAY ARRAY['ALLY_SERVICE_IMAGE_UPLOAD', 'ALLY_SERVICE_IMAGE_DELETE']
    LOOP
        IF NOT EXISTS (SELECT 1 FROM permissions WHERE name = new_permission) THEN
            RAISE EXCEPTION 'V57: % was not created', new_permission;
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM role_permissions rp
                     JOIN roles r      ON r.roles_id = rp.role_id AND r.name = 'SYSTEM'
                     JOIN permissions p ON p.permissions_id = rp.permission_id AND p.name = new_permission
        ) THEN
            RAISE EXCEPTION 'V57: SYSTEM did not receive % (V30 trigger?)', new_permission;
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM role_permissions rp
                     JOIN roles r      ON r.roles_id = rp.role_id AND r.name = 'ADMINISTRADOR'
                     JOIN permissions p ON p.permissions_id = rp.permission_id AND p.name = new_permission
        ) THEN
            RAISE EXCEPTION 'V57: ADMINISTRADOR did not receive %', new_permission;
        END IF;
    END LOOP;
END $$;
