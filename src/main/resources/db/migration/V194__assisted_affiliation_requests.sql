SET search_path TO app, public;

-- ============================================================================
-- V194: assisted affiliation — the advisor fills the affiliation form for a
-- family from their own portal (client doc F1), on their device, and the
-- family reads and accepts the contract there.
--
-- affiliation_requests gains `channel`:
--   SELF_SERVICE  the public form (advisor QR → /afiliarse), as until now;
--   ASSISTED      the advisor portal (POST /v1/me/advisor/affiliation-requests).
-- An assisted request is attributed to the calling advisor (promoter_id, not a
-- referral code typed by the applicant) and created_by is the advisor's user.
--
-- The password is optional in ASSISTED: the family may not want to type it on
-- the advisor's phone. Then password_hash stays NULL and, on approval, the
-- user gets an unusable random password plus a "create your password" link
-- (the reset-token mechanism with a longer TTL) in the approval email.
-- SELF_SERVICE keeps requiring it (CHECK below).
--
-- New permission AFFILIATION_REQUEST_CREATE_ASSISTED (AFFILIATION_REQUESTS
-- domain): ASESOR (it is their own action) and ADMINISTRADOR; OPERADOR none.
-- SYSTEM gets every permission through the V30 trigger; listed explicitly anyway.
-- ============================================================================

-- ─── 1. Channel + optional password ─────────────────────────────────────────

ALTER TABLE affiliation_requests
    ADD COLUMN channel VARCHAR(20) NOT NULL DEFAULT 'SELF_SERVICE';

ALTER TABLE affiliation_requests
    ADD CONSTRAINT ck_affiliation_requests_channel
        CHECK (channel IN ('SELF_SERVICE', 'ASSISTED'));

ALTER TABLE affiliation_requests
    ALTER COLUMN password_hash DROP NOT NULL;

ALTER TABLE affiliation_requests
    ADD CONSTRAINT ck_affiliation_requests_password_by_channel
        CHECK (channel = 'ASSISTED' OR password_hash IS NOT NULL);

CREATE INDEX idx_affiliation_requests_channel ON affiliation_requests (channel);

COMMENT ON COLUMN affiliation_requests.channel IS
    'Canal de la solicitud: SELF_SERVICE (página pública, QR del asesor) o ASSISTED (el asesor la llenó desde su portal con la familia)';
COMMENT ON COLUMN affiliation_requests.password_hash IS
    'Hash BCrypt de la contraseña elegida en el formulario; pasa al usuario al aprobar. Vacío en una afiliación asistida sin contraseña: al aprobar se envía un enlace para crearla';
COMMENT ON COLUMN affiliation_requests.promoter_id IS
    'Asesor de la solicitud: el del código de referido (?ref=) en la página pública, o el asesor que la llenó en una afiliación asistida';

-- ─── 2. Permission ──────────────────────────────────────────────────────────

INSERT INTO permissions (name, domain_id, description)
SELECT 'AFFILIATION_REQUEST_CREATE_ASSISTED', pd.permission_domains_id,
       'Registrar una solicitud de afiliación asistida desde el portal del asesor (la familia lee y acepta el contrato en el dispositivo del asesor)'
FROM permission_domains pd
WHERE pd.code = 'AFFILIATION_REQUESTS'
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
CROSS JOIN permissions p
WHERE r.name IN ('SYSTEM', 'ADMINISTRADOR', 'ASESOR')
  AND p.name = 'AFFILIATION_REQUEST_CREATE_ASSISTED'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ─── Fail loudly rather than migrate into a half-applied state ──────────────
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'AFFILIATION_REQUEST_CREATE_ASSISTED') THEN
        RAISE EXCEPTION 'V194: AFFILIATION_REQUEST_CREATE_ASSISTED was not created (AFFILIATION_REQUESTS domain missing?)';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM roles r
        WHERE r.name IN ('SYSTEM', 'ADMINISTRADOR', 'ASESOR')
          AND NOT EXISTS (
            SELECT 1 FROM role_permissions rp
            JOIN permissions p ON p.permissions_id = rp.permission_id
            WHERE rp.role_id = r.roles_id AND p.name = 'AFFILIATION_REQUEST_CREATE_ASSISTED')
    ) THEN
        RAISE EXCEPTION 'V194: SYSTEM/ADMINISTRADOR/ASESOR did not receive AFFILIATION_REQUEST_CREATE_ASSISTED';
    END IF;

    IF EXISTS (
        SELECT 1 FROM role_permissions rp
        JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'OPERADOR'
        JOIN permissions p ON p.permissions_id = rp.permission_id AND p.name = 'AFFILIATION_REQUEST_CREATE_ASSISTED'
    ) THEN
        RAISE EXCEPTION 'V194: OPERADOR must not receive AFFILIATION_REQUEST_CREATE_ASSISTED by default';
    END IF;

    IF EXISTS (SELECT 1 FROM affiliation_requests WHERE channel <> 'SELF_SERVICE') THEN
        RAISE EXCEPTION 'V194: existing affiliation requests must default to SELF_SERVICE';
    END IF;
END $$;
