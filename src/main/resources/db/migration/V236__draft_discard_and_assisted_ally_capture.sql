SET search_path TO app, public;

-- ============================================================================
-- V236: discard of registrations in progress, cleanup of abandoned drafts and
-- advisor-assisted ally capture (hub ADR 0037 §2-5).
--
--   1. allies.registration_channel gains ADVISOR: an ally captured from the
--      advisor portal (the advisor fills the request, the contact person has
--      no login until the request is submitted).
--   2. Granular permissions, one per action:
--        AFFILIATION_REGISTRATION_DISCARD_OWN        the person discards their own DRAFT
--        AFFILIATION_REQUEST_DRAFT_DISCARD_ASSISTED  the advisor discards their own assisted DRAFT
--        ALLY_REGISTRATION_DISCARD_OWN               the owner discards an in-progress ally registration
--        ALLY_CREATE_ASSISTED                        the advisor starts an ally capture
--        ALLY_DRAFT_VIEW_ASSISTED                    the advisor lists / reads their own ally drafts
--        ALLY_DRAFT_UPDATE_ASSISTED                  the advisor saves the steps of their own ally draft
--        ALLY_DRAFT_SUBMIT_ASSISTED                  the advisor sends their own ally draft to review
--        ALLY_DRAFT_DISCARD_ASSISTED                 the advisor discards their own ally draft
--      The assisted ones go to ASESOR, ADMINISTRADOR and SYSTEM (same as the
--      assisted-affiliation permissions of V194/V234); the "own" ones to the
--      pending roles that already hold the matching *_UPDATE_OWN permission.
--   3. Scheduled job REGISTRATION_DRAFT_CLEANUP: daily, discards the DRAFT
--      registrations abandoned for app.onboarding.draft-cleanup.retention-days.
-- ============================================================================

-- ─── 1. Channel ADVISOR ─────────────────────────────────────────────────────

ALTER TABLE allies DROP CONSTRAINT chk_allies_registration_channel;
ALTER TABLE allies
    ADD CONSTRAINT chk_allies_registration_channel
        CHECK (registration_channel IN ('ADMIN', 'SELF_SERVICE', 'IMPORT', 'ADVISOR'));

COMMENT ON COLUMN allies.registration_channel IS
    'Origen del aliado: ADMIN (alta desde el panel), SELF_SERVICE (/solicitar-alianza), IMPORT (plantilla Excel/CSV), ADVISOR (captado por un asesor desde su portal)';

-- ─── 2. Permissions ─────────────────────────────────────────────────────────

INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('AFFILIATION_REGISTRATION_DISCARD_OWN',       'AFFILIATION_REQUESTS', 'Descartar el borrador de registro de afiliación propio'),
    ('AFFILIATION_REQUEST_DRAFT_DISCARD_ASSISTED', 'AFFILIATION_REQUESTS', 'Descartar un borrador de afiliación asistida propio (portal del asesor)'),
    ('ALLY_REGISTRATION_DISCARD_OWN',              'ALLIES',               'Descartar el registro de aliado propio que aún no se ha enviado a revisión'),
    ('ALLY_CREATE_ASSISTED',                       'ALLIES',               'Iniciar la captación de un aliado desde el portal del asesor'),
    ('ALLY_DRAFT_VIEW_ASSISTED',                   'ALLIES',               'Ver los borradores de aliado captados por el asesor propio'),
    ('ALLY_DRAFT_UPDATE_ASSISTED',                 'ALLIES',               'Guardar por pasos un borrador de aliado propio (portal del asesor)'),
    ('ALLY_DRAFT_SUBMIT_ASSISTED',                 'ALLIES',               'Enviar a revisión un borrador de aliado propio (portal del asesor)'),
    ('ALLY_DRAFT_DISCARD_ASSISTED',                'ALLIES',               'Descartar un borrador de aliado propio (portal del asesor)')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
CROSS JOIN permissions p
WHERE r.name IN ('SYSTEM', 'ADMINISTRADOR', 'ASESOR')
  AND p.name IN ('AFFILIATION_REQUEST_DRAFT_DISCARD_ASSISTED', 'ALLY_CREATE_ASSISTED', 'ALLY_DRAFT_VIEW_ASSISTED',
                 'ALLY_DRAFT_UPDATE_ASSISTED', 'ALLY_DRAFT_SUBMIT_ASSISTED', 'ALLY_DRAFT_DISCARD_ASSISTED')
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
CROSS JOIN permissions p
WHERE (r.name IN ('AFILIADO_PENDIENTE', 'SYSTEM') AND p.name = 'AFFILIATION_REGISTRATION_DISCARD_OWN')
   OR (r.name IN ('ALIADO_PENDIENTE', 'SYSTEM') AND p.name = 'ALLY_REGISTRATION_DISCARD_OWN')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ─── 3. Abandoned-draft cleanup job ─────────────────────────────────────────

INSERT INTO scheduled_jobs (code, display_name, description, cron_expression, timezone, parameters)
VALUES (
    'REGISTRATION_DRAFT_CLEANUP',
    'Limpieza de borradores de registro abandonados',
    'Descarta los registros de afiliado y aliado que siguen en borrador (sin enviar a revisión) tras los días de retención configurados; libera su cédula y correo. Nunca toca lo ya enviado.',
    '0 15 3 * * *',
    'America/Caracas',
    '{}'::jsonb
);

DO $$
BEGIN
    IF (SELECT COUNT(*) FROM permissions
        WHERE name IN ('AFFILIATION_REGISTRATION_DISCARD_OWN', 'AFFILIATION_REQUEST_DRAFT_DISCARD_ASSISTED',
                       'ALLY_REGISTRATION_DISCARD_OWN', 'ALLY_CREATE_ASSISTED', 'ALLY_DRAFT_VIEW_ASSISTED',
                       'ALLY_DRAFT_UPDATE_ASSISTED', 'ALLY_DRAFT_SUBMIT_ASSISTED', 'ALLY_DRAFT_DISCARD_ASSISTED')) <> 8 THEN
        RAISE EXCEPTION 'V236: the discard / assisted ally permissions were not created (domain missing?)';
    END IF;
    IF (SELECT COUNT(*) FROM role_permissions rp
        JOIN roles r ON r.roles_id = rp.role_id AND r.name IN ('SYSTEM', 'ADMINISTRADOR', 'ASESOR')
        JOIN permissions p ON p.permissions_id = rp.permission_id
         AND p.name IN ('AFFILIATION_REQUEST_DRAFT_DISCARD_ASSISTED', 'ALLY_CREATE_ASSISTED', 'ALLY_DRAFT_VIEW_ASSISTED',
                        'ALLY_DRAFT_UPDATE_ASSISTED', 'ALLY_DRAFT_SUBMIT_ASSISTED', 'ALLY_DRAFT_DISCARD_ASSISTED')) <> 18 THEN
        RAISE EXCEPTION 'V236: SYSTEM/ADMINISTRADOR/ASESOR did not receive the assisted permissions';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM scheduled_jobs WHERE code = 'REGISTRATION_DRAFT_CLEANUP') THEN
        RAISE EXCEPTION 'V236: REGISTRATION_DRAFT_CLEANUP was not seeded';
    END IF;
END $$;
