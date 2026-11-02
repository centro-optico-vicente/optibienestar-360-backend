-- ====================================================
-- V212__member_emergency_contact_permissions.sql
--
-- The titular's emergency phone is part of the client's B2C form ("Teléfono
-- de Emergencia", hub ADR 0031 §14), but it lives in the medical record
-- (medical_records.emergency_contact_*, V19), writable only with
-- MEDICAL_RECORD_UPDATE (SYSTEM / ADMINISTRADOR / OPERADOR_MEDICO). The people
-- who load the titular's data (operators, advisors, the affiliate) must not
-- get the rest of the medical data, so the emergency contact gets its own
-- granular permissions, which read and write ONLY those three fields:
--
--   MEMBER_EMERGENCY_CONTACT_VIEW_ALL        staff, any member
--   MEMBER_EMERGENCY_CONTACT_UPDATE          staff, any member
--   MEMBER_EMERGENCY_CONTACT_UPDATE_DOWNLINE advisor, own portfolio only
--   MEMBER_EMERGENCY_CONTACT_VIEW_OWN        the caller's own member record
--   MEMBER_EMERGENCY_CONTACT_UPDATE_OWN      the caller's own member record
--
-- No schema change: the columns already exist. The _OWN pair goes to every
-- portal role (an advisor or an ally is also a person who may be an
-- affiliate, same reasoning as V202); it only ever touches the caller's own
-- record. SYSTEM gets every permission implicitly (PermissionResolver).
-- ====================================================

INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('MEMBER_EMERGENCY_CONTACT_VIEW_ALL',        'MEMBERS', 'Ver el contacto de emergencia de cualquier afiliado'),
    ('MEMBER_EMERGENCY_CONTACT_UPDATE',          'MEMBERS', 'Editar el contacto de emergencia de cualquier afiliado'),
    ('MEMBER_EMERGENCY_CONTACT_UPDATE_DOWNLINE', 'MEMBERS', 'Ver y editar el contacto de emergencia de los afiliados de la cartera propia'),
    ('MEMBER_EMERGENCY_CONTACT_VIEW_OWN',        'MEMBERS', 'Ver el contacto de emergencia propio'),
    ('MEMBER_EMERGENCY_CONTACT_UPDATE_OWN',      'MEMBERS', 'Editar el contacto de emergencia propio')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r CROSS JOIN permissions p
WHERE r.name IN ('ADMINISTRADOR', 'OPERADOR')
  AND p.name IN ('MEMBER_EMERGENCY_CONTACT_VIEW_ALL', 'MEMBER_EMERGENCY_CONTACT_UPDATE')
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ASESOR'
  AND p.name = 'MEMBER_EMERGENCY_CONTACT_UPDATE_DOWNLINE'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r CROSS JOIN permissions p
WHERE r.name IN ('AFILIADO', 'ASESOR', 'ALIADO')
  AND p.name IN ('MEMBER_EMERGENCY_CONTACT_VIEW_OWN', 'MEMBER_EMERGENCY_CONTACT_UPDATE_OWN')
ON CONFLICT (role_id, permission_id) DO NOTHING;

DO $$
BEGIN
    IF (SELECT COUNT(*) FROM permissions WHERE name LIKE 'MEMBER_EMERGENCY_CONTACT_%') <> 5 THEN
        RAISE EXCEPTION 'V212: the five MEMBER_EMERGENCY_CONTACT_* permissions were not created';
    END IF;
    IF EXISTS (SELECT 1 FROM roles WHERE name = 'ASESOR')
       AND NOT EXISTS (
           SELECT 1 FROM role_permissions rp
                    JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'ASESOR'
                    JOIN permissions p ON p.permissions_id = rp.permission_id
                                      AND p.name = 'MEMBER_EMERGENCY_CONTACT_UPDATE_DOWNLINE') THEN
        RAISE EXCEPTION 'V212: ASESOR did not receive MEMBER_EMERGENCY_CONTACT_UPDATE_DOWNLINE';
    END IF;
END $$;
