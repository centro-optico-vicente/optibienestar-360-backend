SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V228: ally self-registration ("Solicitar alianza") — hub ADR 0036.
--
--   1. allies gains a registration lifecycle independent from is_published:
--        registration_status  PENDING | APPROVED | REJECTED
--        registration_channel ADMIN | SELF_SERVICE | IMPORT
--      plus the note the applicant typed (proposed benefit) and who/when
--      approved or rejected it. Every existing ally (incl. the 8 seeded in
--      V227) is APPROVED / ADMIN — confirmed by the client on 2026-11-06.
--      An ally is publicly visible only when active + published + APPROVED;
--      the CHECK below makes "published but not approved" impossible.
--   2. Role ALIADO_PENDIENTE: the self-registered ally user. It only sees and
--      edits its own ally profile until staff approve the request, when the
--      role is swapped for ALIADO.
--   3. Permissions: ALLY_UPDATE_OWN / ALLY_LICENSE_UPDATE_OWN (portal profile
--      and own licenses, also for ALIADO)
--      and the review / import permissions of the new ALLY_REQUESTS domain.
--
-- The signed agreement support reuses ally_agreements.signed_pdf_url, which
-- already stores an R2 key (V11).
-- ────────────────────────────────────────────────────────────────────────────

-- ─── 1. allies registration lifecycle ───────────────────────────────────────

ALTER TABLE allies
    ADD COLUMN registration_status  VARCHAR(10)  NOT NULL DEFAULT 'APPROVED',
    ADD COLUMN registration_channel VARCHAR(12)  NOT NULL DEFAULT 'ADMIN',
    ADD COLUMN registration_note    TEXT,
    ADD COLUMN approved_at          TIMESTAMPTZ,
    ADD COLUMN approved_by          UUID,
    ADD COLUMN rejected_at          TIMESTAMPTZ,
    ADD COLUMN rejected_by          UUID,
    ADD COLUMN rejection_reason     TEXT;

-- Defaults only served the backfill: from now on the code always states them.
ALTER TABLE allies
    ALTER COLUMN registration_status DROP DEFAULT,
    ALTER COLUMN registration_channel DROP DEFAULT;

ALTER TABLE allies
    ADD CONSTRAINT chk_allies_registration_status
        CHECK (registration_status IN ('PENDING', 'APPROVED', 'REJECTED')),
    ADD CONSTRAINT chk_allies_registration_channel
        CHECK (registration_channel IN ('ADMIN', 'SELF_SERVICE', 'IMPORT')),
    ADD CONSTRAINT chk_allies_published_requires_approval
        CHECK (NOT is_published OR registration_status = 'APPROVED'),
    ADD CONSTRAINT chk_allies_rejection_reason
        CHECK (registration_status <> 'REJECTED' OR rejection_reason IS NOT NULL);

CREATE INDEX idx_allies_registration_pending
    ON allies (created_at DESC)
    WHERE registration_status = 'PENDING';

COMMENT ON COLUMN allies.registration_status IS
    'Ciclo de la solicitud de alianza (ADR 0036): PENDING hasta que un operador registra el convenio y la aprueba; solo APPROVED puede publicarse';
COMMENT ON COLUMN allies.registration_channel IS
    'Origen del aliado: ADMIN (alta desde el panel), SELF_SERVICE (/solicitar-alianza), IMPORT (plantilla Excel/CSV)';
COMMENT ON COLUMN allies.registration_note IS
    'Beneficio que el aliado propone al registrarse (texto libre para el operador)';

-- ─── 2. Role ALIADO_PENDIENTE ───────────────────────────────────────────────

INSERT INTO roles (name, description)
VALUES ('ALIADO_PENDIENTE', 'Aliado con solicitud de alianza en revisión — solo ve y completa su ficha')
ON CONFLICT (name) DO NOTHING;

-- ─── 3. Permissions ─────────────────────────────────────────────────────────

INSERT INTO permission_domains (code, name, icon, description, display_order)
VALUES ('ALLY_REQUESTS', 'Solicitudes de alianza', 'i-lucide-handshake',
        'Revisión de las solicitudes de alianza y carga masiva de aliados', 26)
ON CONFLICT (code) DO NOTHING;

-- SYSTEM receives every permission through the V30 trigger.
INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('ALLY_UPDATE_OWN',            'ALLIES',        'Editar la ficha del aliado propio desde el portal (como propietario)'),
    ('ALLY_LICENSE_UPDATE_OWN',    'ALLIES',        'Agregar o quitar las colegiaturas propias desde el portal del aliado (como propietario)'),
    ('ALLY_REGISTRATION_VIEW_ALL', 'ALLY_REQUESTS', 'Ver las solicitudes de alianza'),
    ('ALLY_REGISTRATION_APPROVE',  'ALLY_REQUESTS', 'Aprobar una solicitud de alianza (exige un convenio activo)'),
    ('ALLY_REGISTRATION_REJECT',   'ALLY_REQUESTS', 'Rechazar o reabrir una solicitud de alianza'),
    ('ALLY_REGISTRATION_INVITE',   'ALLY_REQUESTS', 'Invitar al portal al contacto de un aliado importado'),
    ('ALLY_IMPORT',                'ALLY_REQUESTS', 'Importar aliados desde la plantilla Excel o un archivo CSV')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code
ON CONFLICT (name) DO NOTHING;

-- Admin side. OPERADOR gets none by default: it is assigned from Roles.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
CROSS JOIN permissions p
WHERE r.name IN ('SYSTEM', 'ADMINISTRADOR')
  AND p.name IN ('ALLY_REGISTRATION_VIEW_ALL', 'ALLY_REGISTRATION_APPROVE', 'ALLY_REGISTRATION_REJECT',
                 'ALLY_REGISTRATION_INVITE', 'ALLY_IMPORT')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Ally portal: approved allies may edit their own profile and licenses too.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ALIADO'
  AND p.name IN ('ALLY_UPDATE_OWN', 'ALLY_LICENSE_UPDATE_OWN')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Pending ally: own profile only (no offers, validator, usages or patients).
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ALIADO_PENDIENTE'
  AND p.name IN ('ALLY_VIEW_OWN', 'ALLY_UPDATE_OWN', 'ALLY_OPENING_HOURS_UPDATE_OWN', 'ALLY_LICENSE_UPDATE_OWN')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ─── Fail loudly rather than migrate into a half-applied state ──────────────

DO $$
BEGIN
    IF (SELECT COUNT(*) FROM permissions
        WHERE name IN ('ALLY_UPDATE_OWN', 'ALLY_LICENSE_UPDATE_OWN', 'ALLY_REGISTRATION_VIEW_ALL',
                       'ALLY_REGISTRATION_APPROVE', 'ALLY_REGISTRATION_REJECT', 'ALLY_REGISTRATION_INVITE',
                       'ALLY_IMPORT')) <> 7 THEN
        RAISE EXCEPTION 'V228: ally self-registration permissions were not created (ALLIES / ALLY_REQUESTS domain missing?)';
    END IF;
    IF (SELECT COUNT(*) FROM role_permissions rp
        JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'ALIADO_PENDIENTE') <> 4 THEN
        RAISE EXCEPTION 'V228: ALIADO_PENDIENTE must hold exactly ALLY_VIEW_OWN, ALLY_UPDATE_OWN, ALLY_OPENING_HOURS_UPDATE_OWN and ALLY_LICENSE_UPDATE_OWN';
    END IF;
    IF EXISTS (SELECT 1 FROM allies WHERE registration_status <> 'APPROVED') THEN
        RAISE EXCEPTION 'V228: existing allies must be backfilled as APPROVED';
    END IF;
END $$;
