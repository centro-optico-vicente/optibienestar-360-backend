-- ====================================================
-- V213__professional_licenses_and_subspecialties.sql
--
-- Professional ally (B2P) data asked by the client's "Convenio Corporativo
-- de Integración Profesional" (hub ADR 0031 §5–6):
--
--   1. licensing_bodies — catalog of the bodies that issue a professional
--      license (M.P.P.S., Colegio de Médicos, …). Only the two bodies the
--      B2P form names are seeded; the rest wait for the client's list.
--   2. person_professional_licenses — the licenses of a PERSON (not of an
--      ally): a doctor who practices at several sites (one ally per site,
--      ADR 0031 §4) registers them once, on the holder person.
--   3. professions.parent_id — a sub-specialty is a child profession
--      (Traumatología → Artroscopia). At most two levels, no cycles: the
--      service enforces it (a CHECK cannot see other rows).
--   4. Non-health professions, now that the ally network is multisector.
--   5. entity_config + the standard permission sets (V119 shape).
-- ====================================================

-- ─── 1. licensing_bodies ─────────────────────────────────────────────────────

CREATE TABLE licensing_bodies
(
    licensing_bodies_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    code                VARCHAR(40)  NOT NULL UNIQUE,
    name                VARCHAR(150) NOT NULL UNIQUE,
    description         VARCHAR(200),
    -- Optional hint for the form: which B2P area usually needs it (V179 values).
    professional_area   VARCHAR(20),
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,

    CONSTRAINT ck_licensing_bodies_professional_area
        CHECK (professional_area IS NULL OR professional_area IN ('MEDICAL', 'WELLNESS'))
);

CREATE INDEX idx_licensing_bodies_is_active ON licensing_bodies (is_active) WHERE is_active;

INSERT INTO licensing_bodies (code, name, description, professional_area) VALUES
    ('MPPS',            'Ministerio del Poder Popular para la Salud (M.P.P.S.)', 'Registro sanitario del profesional de la salud', 'MEDICAL'),
    ('COLEGIO_MEDICOS', 'Colegio de Médicos',                                    'Colegiatura del médico',                          'MEDICAL')
ON CONFLICT DO NOTHING;

-- ─── 2. person_professional_licenses ────────────────────────────────────────

CREATE TABLE person_professional_licenses
(
    person_professional_licenses_id BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                            UUID        NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    person_id                       BIGINT      NOT NULL REFERENCES persons (persons_id),
    licensing_body_id               BIGINT      NOT NULL REFERENCES licensing_bodies (licensing_bodies_id),
    license_number                  VARCHAR(40) NOT NULL,

    verification_status             VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    verified_by                     UUID,
    verified_at                     TIMESTAMPTZ,

    is_active                       BOOLEAN     NOT NULL DEFAULT TRUE,
    status                          VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by                      UUID,
    updated_by                      UUID,

    CONSTRAINT ck_person_professional_licenses_verification
        CHECK (verification_status IN ('PENDING', 'VERIFIED', 'REJECTED')),
    -- Who and when are stamped only once someone reviewed the license.
    CONSTRAINT ck_person_professional_licenses_verified_stamp
        CHECK ((verification_status = 'PENDING' AND verified_at IS NULL)
            OR (verification_status <> 'PENDING' AND verified_at IS NOT NULL)),
    CONSTRAINT ck_person_professional_licenses_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

-- A license number is unique per issuing body among the live rows (a removed
-- row must not block re-registering the same number).
CREATE UNIQUE INDEX uniq_person_professional_licenses_body_number
    ON person_professional_licenses (licensing_body_id, license_number) WHERE is_active;
CREATE INDEX idx_person_professional_licenses_person ON person_professional_licenses (person_id);

-- ─── 3. professions.parent_id ───────────────────────────────────────────────

ALTER TABLE professions
    ADD COLUMN parent_id BIGINT REFERENCES professions (professions_id);

ALTER TABLE professions
    ADD CONSTRAINT ck_professions_not_own_parent CHECK (parent_id IS NULL OR parent_id <> professions_id);

CREATE INDEX idx_professions_parent ON professions (parent_id);

COMMENT ON COLUMN professions.parent_id IS 'Profesión general de una sub-especialidad (máximo dos niveles)';

-- ─── 4. Non-health professions ──────────────────────────────────────────────

INSERT INTO professions (code, name, description) VALUES
    ('DERECHO',            'Derecho',            'Abogado: asesoría y representación legal'),
    ('INGENIERIA_CIVIL',   'Ingeniería civil',   'Proyectos, cálculo y supervisión de obras'),
    ('CONTADURIA_PUBLICA', 'Contaduría pública', 'Contabilidad, auditoría y asesoría tributaria'),
    ('ARQUITECTURA',       'Arquitectura',       'Diseño y proyectos arquitectónicos')
ON CONFLICT DO NOTHING;

-- Psicología clínica is a sub-specialty of Psicología (V10), when both exist.
INSERT INTO professions (code, name, description, parent_id)
SELECT 'PSICOLOGIA_CLINICA', 'Psicología clínica', 'Evaluación y tratamiento psicológico', p.professions_id
FROM professions p
WHERE p.code = 'PSICOLOGIA'
ON CONFLICT DO NOTHING;

-- ─── 5. entity_config + permissions ─────────────────────────────────────────

INSERT INTO entity_config (entity_key, display_name, table_name) VALUES
    ('licensing_body',               'Entes emisores de colegiaturas', 'licensing_bodies'),
    ('person_professional_license',  'Colegiaturas',                   'person_professional_licenses')
ON CONFLICT (entity_key) DO NOTHING;

INSERT INTO permission_domains (code, name, icon, description, display_order)
VALUES ('CATALOG_LICENSING_BODY', 'Entes emisores de colegiaturas', 'i-lucide-badge-check',
        'Catálogo de entes que emiten colegiaturas profesionales', 124),
       ('PERSON_LICENSES', 'Colegiaturas', 'i-lucide-id-card',
        'Colegiaturas profesionales de la persona titular de un aliado profesional (B2P)', 125)
ON CONFLICT (code) DO NOTHING;

INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('LICENSING_BODY_VIEW_ALL',          'CATALOG_LICENSING_BODY', 'Ver el catálogo de entes emisores de colegiaturas'),
    ('LICENSING_BODY_CREATE',            'CATALOG_LICENSING_BODY', 'Crear un ente emisor de colegiaturas'),
    ('LICENSING_BODY_UPDATE',            'CATALOG_LICENSING_BODY', 'Actualizar un ente emisor de colegiaturas'),
    ('LICENSING_BODY_DELETE',            'CATALOG_LICENSING_BODY', 'Desactivar un ente emisor de colegiaturas'),
    ('LICENSING_BODY_RECORD_AUDIT_VIEW', 'CATALOG_LICENSING_BODY', 'Ver el historial de cambios de un ente emisor'),
    ('LICENSING_BODY_REPORT_AUDIT_VIEW', 'CATALOG_LICENSING_BODY', 'Ver el historial de reportes generados de entes emisores'),

    ('PERSON_LICENSE_VIEW_ALL',          'PERSON_LICENSES', 'Ver las colegiaturas de los aliados profesionales'),
    ('PERSON_LICENSE_CREATE',            'PERSON_LICENSES', 'Registrar una colegiatura'),
    ('PERSON_LICENSE_UPDATE',            'PERSON_LICENSES', 'Editar una colegiatura'),
    ('PERSON_LICENSE_DELETE',            'PERSON_LICENSES', 'Eliminar una colegiatura'),
    ('PERSON_LICENSE_VERIFY',            'PERSON_LICENSES', 'Verificar o rechazar una colegiatura')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r CROSS JOIN permissions p
WHERE r.name IN ('SYSTEM', 'ADMINISTRADOR')
  AND (p.name LIKE 'LICENSING_BODY_%' OR p.name LIKE 'PERSON_LICENSE_%')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- No default grant to OPERADOR: like every other catalog (V33/V73/V119), it is
-- assigned from Roles when needed.

DO $$
BEGIN
    IF (SELECT COUNT(*) FROM permissions
        WHERE name LIKE 'LICENSING_BODY_%' OR name LIKE 'PERSON_LICENSE_%') <> 11 THEN
        RAISE EXCEPTION 'V213: the 11 LICENSING_BODY_* / PERSON_LICENSE_* permissions were not created';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM licensing_bodies WHERE code IN ('MPPS', 'COLEGIO_MEDICOS') HAVING COUNT(*) = 2) THEN
        RAISE EXCEPTION 'V213: the MPPS / COLEGIO_MEDICOS licensing bodies were not seeded';
    END IF;
END $$;
