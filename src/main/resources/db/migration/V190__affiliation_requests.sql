SET search_path TO app, public;

-- ============================================================================
-- V190: affiliation requests — the public self-affiliation (advisor QR →
-- /afiliarse) becomes a request that staff review before anything is created.
--
-- Until now POST /v1/public/affiliations created the AFILIADO login, the
-- member and the membership on the spot. Now the applicant's data, the BCrypt
-- hash of the password they chose and the evidence of the contract they
-- accepted (rendered text + sha256 + version + IP/User-Agent) are stored
-- here as PENDING. A reviewer may correct the data, then approves (user +
-- member + SUSPENDED membership are created from this row) or rejects with a
-- reason. If a correction changes the contract text, the acceptance is not
-- recorded on approval and the member is asked to accept it at first login.
--
-- affiliation_request_logs is the insert-only history (same idea as
-- ally_service_review_log, V11): one row per status change or edit, with who,
-- when, from/to and an optional note. Edits list the changed fields in
-- `changes` (JSONB array of {field, from, to}).
--
-- Permissions (new AFFILIATION_REQUESTS domain): AFFILIATION_REQUEST_VIEW_ALL,
-- _VIEW_OWN (advisor: the requests that came through their QR), _UPDATE,
-- _APPROVE, _REJECT. ADMINISTRADOR gets all of them, ASESOR only _VIEW_OWN,
-- OPERADOR none (assigned from the Roles screen when needed). SYSTEM gets
-- every permission through the V30 trigger; listed explicitly anyway.
-- ============================================================================

-- ─── 1. affiliation_requests ────────────────────────────────────────────────

CREATE TABLE affiliation_requests
(
    affiliation_requests_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                    UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    -- Applicant data (editable by the reviewer while PENDING).
    first_name              VARCHAR(50)  NOT NULL,
    last_name               VARCHAR(50)  NOT NULL,
    document_type           VARCHAR(2)   NOT NULL,
    document_number         VARCHAR(20)  NOT NULL,
    birth_date              DATE         NOT NULL,
    email                   VARCHAR(254) NOT NULL,
    phone                   VARCHAR(30),
    locale                  VARCHAR(10),

    plan_id                 BIGINT       NOT NULL REFERENCES plans (plans_id),
    promoter_id             BIGINT       REFERENCES promoters (promoters_id),
    referral_code           VARCHAR(20),

    -- BCrypt hash of the password chosen on the form; copied to users on approval.
    password_hash           VARCHAR(255) NOT NULL,

    -- Contract evidence: what the applicant read and accepted.
    terms_versions_id       BIGINT       NOT NULL REFERENCES terms_versions (terms_versions_id),
    terms_rendered_content  TEXT         NOT NULL,
    terms_content_sha256    VARCHAR(64)  NOT NULL,
    terms_accepted_at       TIMESTAMPTZ  NOT NULL,
    ip_address              VARCHAR(45),
    user_agent              VARCHAR(400),

    -- Review.
    reviewed_by             BIGINT       REFERENCES users (users_id),
    reviewed_at             TIMESTAMPTZ,
    rejection_reason        TEXT,

    -- What the approval created.
    user_id                 BIGINT       REFERENCES users (users_id),
    member_id               BIGINT       REFERENCES members (members_id),
    membership_id           BIGINT       REFERENCES memberships (memberships_id),

    is_active               BOOLEAN      NOT NULL DEFAULT TRUE,
    status                  VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by              UUID,
    updated_by              UUID,

    CONSTRAINT ck_affiliation_requests_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_affiliation_requests_document_type
        CHECK (document_type IN ('V', 'E')),
    CONSTRAINT ck_affiliation_requests_rejection_reason
        CHECK (status <> 'REJECTED' OR (rejection_reason IS NOT NULL AND BTRIM(rejection_reason) <> '')),
    CONSTRAINT ck_affiliation_requests_approved_links
        CHECK (status <> 'APPROVED'
            OR (user_id IS NOT NULL AND member_id IS NOT NULL AND membership_id IS NOT NULL))
);

-- One open request per email / document: a second submit while the first
-- one waits for review is rejected (the service checks first for a clean 422).
CREATE UNIQUE INDEX uq_affiliation_requests_pending_email
    ON affiliation_requests (LOWER(email))
    WHERE status = 'PENDING';
CREATE UNIQUE INDEX uq_affiliation_requests_pending_document
    ON affiliation_requests (document_type, document_number)
    WHERE status = 'PENDING';

CREATE INDEX idx_affiliation_requests_status_created ON affiliation_requests (status, created_at DESC);
CREATE INDEX idx_affiliation_requests_promoter ON affiliation_requests (promoter_id) WHERE promoter_id IS NOT NULL;
CREATE INDEX idx_affiliation_requests_plan ON affiliation_requests (plan_id);

CREATE TRIGGER trg_affiliation_requests_updated_at
    BEFORE UPDATE ON affiliation_requests
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE affiliation_requests IS 'Solicitudes de afiliación desde la página pública (QR del asesor); se revisan y se aprueban o rechazan';
COMMENT ON COLUMN affiliation_requests.status IS 'Estado de la solicitud: PENDING (por revisar), APPROVED (aprobada) o REJECTED (rechazada)';
COMMENT ON COLUMN affiliation_requests.locale IS 'Idioma del formulario (es / en), usado para los correos de la solicitud';
COMMENT ON COLUMN affiliation_requests.promoter_id IS 'Asesor del código de referido (?ref=) con el que llegó la solicitud';
COMMENT ON COLUMN affiliation_requests.referral_code IS 'Código de referido tal como llegó, normalizado en mayúsculas';
COMMENT ON COLUMN affiliation_requests.password_hash IS 'Hash BCrypt de la contraseña elegida en el formulario; pasa al usuario al aprobar';
COMMENT ON COLUMN affiliation_requests.terms_versions_id IS 'Versión del contrato de afiliado aceptada en el formulario';
COMMENT ON COLUMN affiliation_requests.terms_rendered_content IS 'Texto del contrato tal como lo leyó la persona, con sus datos';
COMMENT ON COLUMN affiliation_requests.terms_content_sha256 IS 'SHA-256 del texto aceptado; si al aprobar el texto cambia, se pide aceptar de nuevo al entrar';
COMMENT ON COLUMN affiliation_requests.terms_accepted_at IS 'Momento en que la persona aceptó el contrato en el formulario';
COMMENT ON COLUMN affiliation_requests.ip_address IS 'IP desde la que se envió la solicitud (evidencia de la aceptación)';
COMMENT ON COLUMN affiliation_requests.user_agent IS 'Navegador desde el que se envió la solicitud (evidencia de la aceptación)';
COMMENT ON COLUMN affiliation_requests.reviewed_by IS 'Usuario que aprobó o rechazó la solicitud';
COMMENT ON COLUMN affiliation_requests.reviewed_at IS 'Momento de la aprobación o el rechazo';
COMMENT ON COLUMN affiliation_requests.rejection_reason IS 'Motivo del rechazo, obligatorio cuando la solicitud se rechaza; se envía a la persona';
COMMENT ON COLUMN affiliation_requests.user_id IS 'Usuario creado al aprobar';
COMMENT ON COLUMN affiliation_requests.member_id IS 'Afiliado creado al aprobar';
COMMENT ON COLUMN affiliation_requests.membership_id IS 'Membresía creada al aprobar (suspendida hasta el primer pago)';

-- ─── 2. affiliation_request_logs ────────────────────────────────────────────

CREATE TABLE affiliation_request_logs
(
    affiliation_request_logs_id BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    affiliation_request_id      BIGINT      NOT NULL REFERENCES affiliation_requests (affiliation_requests_id) ON DELETE CASCADE,
    action                      VARCHAR(20) NOT NULL,
    from_status                 VARCHAR(20),
    to_status                   VARCHAR(20),
    changes                     JSONB,
    comment                     TEXT,
    actor_user_id               BIGINT      REFERENCES users (users_id),
    action_at                   TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_affiliation_request_logs_action
        CHECK (action IN ('CREATED', 'UPDATED', 'APPROVED', 'REJECTED'))
);

CREATE INDEX idx_affiliation_request_logs_request
    ON affiliation_request_logs (affiliation_request_id, action_at DESC);

COMMENT ON TABLE affiliation_request_logs IS 'Bitácora de cada solicitud de afiliación: creación, ediciones, aprobación y rechazo';
COMMENT ON COLUMN affiliation_request_logs.action IS 'Qué pasó: CREATED, UPDATED, APPROVED o REJECTED';
COMMENT ON COLUMN affiliation_request_logs.changes IS 'Campos editados: lista de {field, from, to}';
COMMENT ON COLUMN affiliation_request_logs.comment IS 'Nota de quien hizo el cambio (en un rechazo, el motivo)';
COMMENT ON COLUMN affiliation_request_logs.actor_user_id IS 'Usuario que hizo el cambio; vacío cuando lo envió la persona desde la página pública';

-- ─── 3. entity_config (default sort + data-change audit) ────────────────────

INSERT INTO entity_config (entity_key, display_name, table_name) VALUES
    ('affiliation_request', 'Solicitudes de afiliación', 'affiliation_requests')
ON CONFLICT (entity_key) DO NOTHING;

-- ─── 4. Permissions ─────────────────────────────────────────────────────────

INSERT INTO permission_domains (code, name, icon, description, display_order)
VALUES ('AFFILIATION_REQUESTS', 'Solicitudes de afiliación', 'i-lucide-user-plus',
        'Revisión de las solicitudes de afiliación de la página pública', 25)
ON CONFLICT (code) DO NOTHING;

INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('AFFILIATION_REQUEST_VIEW_ALL', 'Ver todas las solicitudes de afiliación'),
    ('AFFILIATION_REQUEST_VIEW_OWN', 'Ver las solicitudes que llegaron por el QR propio (portal del asesor)'),
    ('AFFILIATION_REQUEST_UPDATE',   'Corregir los datos de una solicitud de afiliación pendiente'),
    ('AFFILIATION_REQUEST_APPROVE',  'Aprobar una solicitud de afiliación (crea el usuario, el afiliado y la membresía)'),
    ('AFFILIATION_REQUEST_REJECT',   'Rechazar una solicitud de afiliación indicando el motivo')
) AS v(name, description)
JOIN permission_domains pd ON pd.code = 'AFFILIATION_REQUESTS'
ON CONFLICT (name) DO NOTHING;

-- Admin side. OPERADOR gets none by default: it is assigned from Roles.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
CROSS JOIN permissions p
WHERE r.name IN ('SYSTEM', 'ADMINISTRADOR')
  AND p.name IN ('AFFILIATION_REQUEST_VIEW_ALL', 'AFFILIATION_REQUEST_VIEW_OWN', 'AFFILIATION_REQUEST_UPDATE',
                 'AFFILIATION_REQUEST_APPROVE', 'AFFILIATION_REQUEST_REJECT')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Advisor portal: read-only list of their own requests.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ASESOR'
  AND p.name = 'AFFILIATION_REQUEST_VIEW_OWN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ─── Fail loudly rather than migrate into a half-applied state ──────────────
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM entity_config WHERE entity_key = 'affiliation_request') THEN
        RAISE EXCEPTION 'V190: entity_config row affiliation_request was not created';
    END IF;

    IF (SELECT COUNT(*) FROM permissions
        WHERE name IN ('AFFILIATION_REQUEST_VIEW_ALL', 'AFFILIATION_REQUEST_VIEW_OWN',
                       'AFFILIATION_REQUEST_UPDATE', 'AFFILIATION_REQUEST_APPROVE',
                       'AFFILIATION_REQUEST_REJECT')) <> 5 THEN
        RAISE EXCEPTION 'V190: affiliation request permissions were not created (AFFILIATION_REQUESTS domain missing?)';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM (VALUES ('SYSTEM'), ('ADMINISTRADOR')) AS want_role(name)
        CROSS JOIN (VALUES ('AFFILIATION_REQUEST_VIEW_ALL'), ('AFFILIATION_REQUEST_VIEW_OWN'),
                           ('AFFILIATION_REQUEST_UPDATE'), ('AFFILIATION_REQUEST_APPROVE'),
                           ('AFFILIATION_REQUEST_REJECT')) AS want_perm(name)
        WHERE NOT EXISTS (
            SELECT 1 FROM role_permissions rp
            JOIN roles r ON r.roles_id = rp.role_id AND r.name = want_role.name
            JOIN permissions p ON p.permissions_id = rp.permission_id AND p.name = want_perm.name
        )
    ) THEN
        RAISE EXCEPTION 'V190: SYSTEM/ADMINISTRADOR did not receive the affiliation request permissions';
    END IF;

    IF EXISTS (SELECT 1 FROM roles WHERE name = 'ASESOR') AND NOT EXISTS (
        SELECT 1 FROM role_permissions rp
        JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'ASESOR'
        JOIN permissions p ON p.permissions_id = rp.permission_id AND p.name = 'AFFILIATION_REQUEST_VIEW_OWN'
    ) THEN
        RAISE EXCEPTION 'V190: ASESOR did not receive AFFILIATION_REQUEST_VIEW_OWN';
    END IF;

    IF EXISTS (
        SELECT 1 FROM role_permissions rp
        JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'OPERADOR'
        JOIN permissions p ON p.permissions_id = rp.permission_id AND p.name LIKE 'AFFILIATION_REQUEST\_%'
    ) THEN
        RAISE EXCEPTION 'V190: OPERADOR must not receive affiliation request permissions by default';
    END IF;
END $$;
