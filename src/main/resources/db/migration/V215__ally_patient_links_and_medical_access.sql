SET search_path TO app, public;

-- ====================================================
-- V215__ally_patient_links_and_medical_access.sql
--
-- Medical allies read their patients' medical history (hub ADR 0032).
--
--   1. ally_patient_links: the doctor–patient link. Opened when a user of a
--      B2P ally of the MEDICAL area records a QR scan of the member's
--      credential; it binds the ally's holder person (the doctor, V211) to
--      the member, so the doctor reads the history from any of their sites.
--      ACTIVE until the member revokes it; scanning again opens a new row
--      (history is kept). At most one ACTIVE link per doctor + member.
--   2. medical_record_access_log: append-only trail of every read.
--   3. system_configs.patient_medical_access_enabled: master switch, OFF by
--      default — legal still has to confirm whether the B2C agreement needs
--      a consent clause before this is used in production (ADR 0032).
--   4. Permissions (one per action):
--        MEMBER_MEDICAL_RECORD_VIEW_PATIENT  ally side: open links, list
--                                            patients, read their history.
--                                            Granted to ALIADO and
--                                            OPERADOR_MEDICO; the service
--                                            narrows it to the ally's holder
--                                            person or an OPERADOR_MEDICO of
--                                            a B2P medical ally.
--        ALLY_PATIENT_LINK_VIEW_OWN          member: who can read my history
--        ALLY_PATIENT_LINK_REVOKE_OWN        member: revoke a link
--        ALLY_PATIENT_LINK_VIEW_ALL          staff audit
-- ====================================================

CREATE TABLE ally_patient_links
(
    ally_patient_links_id BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                  UUID        NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    doctor_person_id      BIGINT      NOT NULL REFERENCES persons (persons_id),
    member_id             BIGINT      NOT NULL REFERENCES members (members_id),
    created_at_ally_id    BIGINT      NOT NULL REFERENCES allies (allies_id),
    created_by_user_id    BIGINT      NOT NULL REFERENCES users (users_id),
    revoked_at            TIMESTAMPTZ,
    revoked_by            UUID,
    is_active             BOOLEAN     NOT NULL DEFAULT TRUE,
    status                VARCHAR(50) NOT NULL DEFAULT 'ACTIVE'
        CONSTRAINT chk_ally_patient_links_status CHECK (status IN ('ACTIVE', 'REVOKED')),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID,
    CONSTRAINT chk_ally_patient_links_revoked
        CHECK ((status = 'REVOKED') = (revoked_at IS NOT NULL))
);

CREATE UNIQUE INDEX uniq_ally_patient_links_active
    ON ally_patient_links (doctor_person_id, member_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_ally_patient_links_member ON ally_patient_links (member_id);

CREATE TRIGGER trg_ally_patient_links_updated_at
    BEFORE UPDATE ON ally_patient_links
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE ally_patient_links IS
    'Vínculo médico–paciente (ADR 0032): el médico (persona titular de un aliado B2P de área médica) puede leer la historia médica del afiliado mientras esté ACTIVE.';

CREATE TABLE medical_record_access_log
(
    medical_record_access_log_id BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                         UUID        NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    link_id                      BIGINT      NOT NULL REFERENCES ally_patient_links (ally_patient_links_id),
    accessed_by_user_id          BIGINT      NOT NULL REFERENCES users (users_id),
    ally_id                      BIGINT      NOT NULL REFERENCES allies (allies_id),
    member_id                    BIGINT      NOT NULL REFERENCES members (members_id),
    accessed_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_medical_record_access_log_member ON medical_record_access_log (member_id, accessed_at DESC);

COMMENT ON TABLE medical_record_access_log IS
    'Bitácora de solo-agregar de cada lectura de historia médica por un aliado médico (ADR 0032). No se purga.';

ALTER TABLE system_configs
    ADD COLUMN patient_medical_access_enabled BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN system_configs.patient_medical_access_enabled IS
    'Activa el acceso de los aliados médicos a la historia de sus pacientes (ADR 0032). Apagado hasta que legal confirme la cláusula del Acuerdo B2C.';

INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('MEMBER_MEDICAL_RECORD_VIEW_PATIENT', 'MEMBERS', 'Ver la historia médica de los pacientes propios (aliado médico con vínculo activo)'),
    ('ALLY_PATIENT_LINK_VIEW_OWN',         'MEMBERS', 'Ver qué médicos aliados tienen acceso a mi historia médica y quién la consultó'),
    ('ALLY_PATIENT_LINK_REVOKE_OWN',       'MEMBERS', 'Revocar el acceso de un médico aliado a mi historia médica'),
    ('ALLY_PATIENT_LINK_VIEW_ALL',         'MEMBERS', 'Auditar los vínculos médico–paciente y las consultas de historia médica')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r CROSS JOIN permissions p
WHERE r.name IN ('ALIADO', 'OPERADOR_MEDICO')
  AND p.name = 'MEMBER_MEDICAL_RECORD_VIEW_PATIENT'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Any portal role can also be an affiliate (same reasoning as V202 / V212).
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r CROSS JOIN permissions p
WHERE r.name IN ('AFILIADO', 'ASESOR', 'ALIADO')
  AND p.name IN ('ALLY_PATIENT_LINK_VIEW_OWN', 'ALLY_PATIENT_LINK_REVOKE_OWN')
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ADMINISTRADOR'
  AND p.name = 'ALLY_PATIENT_LINK_VIEW_ALL'
ON CONFLICT (role_id, permission_id) DO NOTHING;

DO $$
BEGIN
    IF (SELECT COUNT(*) FROM permissions
        WHERE name IN ('MEMBER_MEDICAL_RECORD_VIEW_PATIENT', 'ALLY_PATIENT_LINK_VIEW_OWN',
                       'ALLY_PATIENT_LINK_REVOKE_OWN', 'ALLY_PATIENT_LINK_VIEW_ALL')) <> 4 THEN
        RAISE EXCEPTION 'V215: the four patient-access permissions were not created';
    END IF;
END $$;
