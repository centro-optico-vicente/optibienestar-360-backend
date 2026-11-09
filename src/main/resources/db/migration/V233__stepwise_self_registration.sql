SET search_path TO app, public;

-- ============================================================
-- V233: step-by-step self-registration (hub ADR 0037 §2-3).
--
--   1. affiliation_requests gains the DRAFT status: step 1 of /afiliarse
--      creates the person, the login (role AFILIADO_PENDIENTE) and a DRAFT
--      request linked to that user; later steps fill the rest and "submit"
--      moves it to PENDING. Everything that now arrives in later steps is
--      nullable while DRAFT and mandatory from PENDING on (CHECK below).
--      `family` keeps the relatives typed in the family step until approval
--      creates the beneficiaries; `submitted_at` is when it left DRAFT.
--   2. allies gains registration_submitted_at: when the applicant sent the
--      request to review, so staff tell "in progress" from "submitted".
--      (Type, RIF/profession and city were already nullable on allies.)
--   3. Role AFILIADO_PENDIENTE and its permissions: view and edit its own
--      registration. SYSTEM receives them through the V30 trigger.
-- ============================================================

-- ─── 1. affiliation_requests: DRAFT ─────────────────────────────────────────

ALTER TABLE affiliation_requests DROP CONSTRAINT ck_affiliation_requests_status;
ALTER TABLE affiliation_requests
    ADD CONSTRAINT ck_affiliation_requests_status
        CHECK (status IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED'));

ALTER TABLE affiliation_requests
    ALTER COLUMN birth_date             DROP NOT NULL,
    ALTER COLUMN plan_id                DROP NOT NULL,
    ALTER COLUMN terms_versions_id      DROP NOT NULL,
    ALTER COLUMN terms_rendered_content DROP NOT NULL,
    ALTER COLUMN terms_content_sha256   DROP NOT NULL,
    ALTER COLUMN terms_accepted_at      DROP NOT NULL;

ALTER TABLE affiliation_requests
    ADD COLUMN family       JSONB,
    ADD COLUMN submitted_at TIMESTAMPTZ;

UPDATE affiliation_requests SET submitted_at = created_at;

ALTER TABLE affiliation_requests
    ADD CONSTRAINT ck_affiliation_requests_complete_after_draft
        CHECK (status = 'DRAFT'
            OR (birth_date IS NOT NULL AND plan_id IS NOT NULL AND terms_versions_id IS NOT NULL
                AND terms_rendered_content IS NOT NULL AND terms_content_sha256 IS NOT NULL
                AND terms_accepted_at IS NOT NULL));

-- A self-service request needs a password hash unless it is assisted or tied to
-- an existing login (the DRAFT's user, whose password lives on users).
ALTER TABLE affiliation_requests DROP CONSTRAINT ck_affiliation_requests_password_by_channel;
ALTER TABLE affiliation_requests
    ADD CONSTRAINT ck_affiliation_requests_password_by_channel
        CHECK (channel = 'ASSISTED' OR password_hash IS NOT NULL OR user_id IS NOT NULL);

-- One open (DRAFT or PENDING) request per login.
CREATE UNIQUE INDEX uq_affiliation_requests_open_user
    ON affiliation_requests (user_id)
    WHERE user_id IS NOT NULL AND status IN ('DRAFT', 'PENDING');

ALTER TABLE affiliation_request_logs DROP CONSTRAINT ck_affiliation_request_logs_action;
ALTER TABLE affiliation_request_logs
    ADD CONSTRAINT ck_affiliation_request_logs_action
        CHECK (action IN ('CREATED', 'UPDATED', 'SUBMITTED', 'APPROVED', 'REJECTED'));

COMMENT ON COLUMN affiliation_requests.status IS
    'Estado: DRAFT (registro en progreso, aún no enviado), PENDING (por revisar), APPROVED o REJECTED';
COMMENT ON COLUMN affiliation_requests.family IS
    'Familiares capturados en el paso "familia" (lista de {firstName, lastName, documentType, documentNumber, birthDate, relationship, phone, email}); se convierten en beneficiarios al aprobar';
COMMENT ON COLUMN affiliation_requests.submitted_at IS
    'Momento en que la persona envió el registro a revisión (salió de DRAFT)';

-- ─── 2. allies: submitted marker ────────────────────────────────────────────

ALTER TABLE allies ADD COLUMN registration_submitted_at TIMESTAMPTZ;

-- Requests that already exist were sent in one step.
UPDATE allies SET registration_submitted_at = created_at WHERE registration_channel <> 'ADMIN';

COMMENT ON COLUMN allies.registration_submitted_at IS
    'Momento en que el aliado envió su solicitud a revisión; vacío mientras la completa por pasos';

-- ─── 3. Role AFILIADO_PENDIENTE ─────────────────────────────────────────────

INSERT INTO roles (name, description)
VALUES ('AFILIADO_PENDIENTE', 'Afiliado con registro en curso o en revisión — solo ve y completa su registro')
ON CONFLICT (name) DO NOTHING;

INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('AFFILIATION_REGISTRATION_VIEW_OWN',   'Ver el registro de afiliación propio y su progreso'),
    ('AFFILIATION_REGISTRATION_UPDATE_OWN', 'Completar por pasos el registro de afiliación propio'),
    ('AFFILIATION_REGISTRATION_SUBMIT_OWN', 'Enviar a revisión el registro de afiliación propio')
) AS v(name, description)
JOIN permission_domains pd ON pd.code = 'AFFILIATION_REQUESTS'
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
CROSS JOIN permissions p
WHERE r.name IN ('AFILIADO_PENDIENTE', 'SYSTEM')
  AND p.name IN ('AFFILIATION_REGISTRATION_VIEW_OWN', 'AFFILIATION_REGISTRATION_UPDATE_OWN',
                 'AFFILIATION_REGISTRATION_SUBMIT_OWN')
ON CONFLICT (role_id, permission_id) DO NOTHING;

DO $$
BEGIN
    IF (SELECT COUNT(*) FROM permissions
        WHERE name IN ('AFFILIATION_REGISTRATION_VIEW_OWN', 'AFFILIATION_REGISTRATION_UPDATE_OWN',
                       'AFFILIATION_REGISTRATION_SUBMIT_OWN')) <> 3 THEN
        RAISE EXCEPTION 'V233: affiliation registration permissions were not created';
    END IF;
    IF (SELECT COUNT(*) FROM role_permissions rp
        JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'AFILIADO_PENDIENTE') <> 3 THEN
        RAISE EXCEPTION 'V233: AFILIADO_PENDIENTE must hold exactly the three registration permissions';
    END IF;
END $$;
