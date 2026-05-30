SET search_path TO app, public;

-- ============================================================
-- Health catalogs — medical_specialties, service_categories,
-- ally_types. Referenced by the allies/services model (V11+).
-- Same conventions as V8/V9 (BIGINT identity PK + uuid,
-- is_active soft-delete, audit columns, set_updated_at trigger,
-- partial is_active index, code UNIQUE as natural key).
-- ============================================================

-- ------------------------------------------------------------
-- medical_specialties: especialidades médicas — no external FKs
-- ------------------------------------------------------------
CREATE TABLE medical_specialties
(
    medical_specialties_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                   UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    code                   VARCHAR(40)  NOT NULL UNIQUE,
    name                   VARCHAR(100) NOT NULL UNIQUE,
    description            VARCHAR(200),
    is_active              BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by             UUID,
    updated_by             UUID
);

CREATE INDEX idx_medical_specialties_is_active ON medical_specialties (is_active) WHERE is_active;

CREATE TRIGGER trg_medical_specialties_updated_at
    BEFORE UPDATE ON medical_specialties
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ------------------------------------------------------------
-- service_categories: tipos de servicios de salud — no external FKs
-- ------------------------------------------------------------
CREATE TABLE service_categories
(
    service_categories_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                  UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    code                  VARCHAR(40)  NOT NULL UNIQUE,
    name                  VARCHAR(100) NOT NULL UNIQUE,
    description           VARCHAR(200),
    is_active             BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID
);

CREATE INDEX idx_service_categories_is_active ON service_categories (is_active) WHERE is_active;

CREATE TRIGGER trg_service_categories_updated_at
    BEFORE UPDATE ON service_categories
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ------------------------------------------------------------
-- ally_types: tipos de aliados (clínica, farmacia, óptica, …)
-- ------------------------------------------------------------
CREATE TABLE ally_types
(
    ally_types_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid          UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    code          VARCHAR(40)  NOT NULL UNIQUE,
    name          VARCHAR(100) NOT NULL UNIQUE,
    description   VARCHAR(200),
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by    UUID,
    updated_by    UUID
);

CREATE INDEX idx_ally_types_is_active ON ally_types (is_active) WHERE is_active;

CREATE TRIGGER trg_ally_types_updated_at
    BEFORE UPDATE ON ally_types
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ============================================================
-- Seed
-- ============================================================

-- Especialidades médicas: lista curada con énfasis en oftalmología /
-- optometría (contexto Centro Óptico Vicente). Se amplía a demanda.
INSERT INTO medical_specialties (code, name, description) VALUES
    ('OFTALMOLOGIA',           'Oftalmología',           'Diagnóstico y tratamiento médico-quirúrgico de enfermedades de los ojos'),
    ('OPTOMETRIA',             'Optometría',             'Evaluación visual y prescripción de lentes correctivos'),
    ('MEDICINA_GENERAL',       'Medicina general',       'Atención médica primaria y orientación clínica'),
    ('PEDIATRIA',              'Pediatría',              'Atención médica de niños y adolescentes'),
    ('GINECOLOGIA',            'Ginecología',            'Salud del aparato reproductor femenino'),
    ('CARDIOLOGIA',            'Cardiología',            'Enfermedades del corazón y sistema cardiovascular'),
    ('DERMATOLOGIA',           'Dermatología',           'Enfermedades de la piel, cabello y uñas'),
    ('ODONTOLOGIA',            'Odontología',            'Salud bucal y tratamientos dentales'),
    ('TRAUMATOLOGIA',          'Traumatología',          'Lesiones del sistema musculoesquelético'),
    ('OTORRINOLARINGOLOGIA',   'Otorrinolaringología',   'Oído, nariz y garganta'),
    ('NEUROLOGIA',             'Neurología',             'Enfermedades del sistema nervioso'),
    ('PSICOLOGIA',             'Psicología',             'Atención de la salud mental y emocional'),
    ('NUTRICION',              'Nutrición',              'Evaluación y plan nutricional'),
    ('FISIOTERAPIA',           'Fisioterapia',           'Rehabilitación física y terapia manual'),
    ('URGENCIAS',              'Urgencias',              'Atención de emergencias médicas');

-- Categorías de servicios prestados por los aliados.
INSERT INTO service_categories (code, name, description) VALUES
    ('CONSULTA',           'Consulta',                  'Consulta médica o profesional ambulatoria'),
    ('EXAMEN',             'Examen diagnóstico',        'Pruebas clínicas para diagnóstico'),
    ('PROCEDIMIENTO',      'Procedimiento',             'Procedimiento ambulatorio no quirúrgico'),
    ('CIRUGIA',            'Cirugía',                   'Intervención quirúrgica'),
    ('IMAGEN_DIAGNOSTICA', 'Imagen diagnóstica',        'Radiografías, ecografías, tomografías, resonancias'),
    ('LABORATORIO',        'Laboratorio',               'Análisis clínicos y de laboratorio'),
    ('TERAPIA',            'Terapia',                   'Sesiones de fisioterapia, psicológica u otras'),
    ('MEDICAMENTO',        'Medicamento',               'Dispensación de medicamentos en farmacia'),
    ('LENTES_Y_MONTURAS',  'Lentes y monturas',         'Productos ópticos: lentes graduados, monturas, lentes de contacto'),
    ('HOSPITALIZACION',    'Hospitalización',           'Internación hospitalaria');

-- Tipos de aliados (proveedores) en el programa.
INSERT INTO ally_types (code, name, description) VALUES
    ('CLINICA',             'Clínica',                 'Centro de atención médica privada'),
    ('HOSPITAL',            'Hospital',                'Centro hospitalario con capacidad de internación'),
    ('CENTRO_MEDICO',       'Centro médico',           'Centro ambulatorio multidisciplinario'),
    ('CONSULTORIO',         'Consultorio',             'Consultorio profesional independiente'),
    ('FARMACIA',            'Farmacia',                'Establecimiento de venta de medicamentos'),
    ('LABORATORIO',         'Laboratorio',             'Laboratorio clínico'),
    ('CENTRO_OPTICO',       'Centro óptico',           'Óptica — venta de lentes y servicios de optometría'),
    ('CENTRO_IMAGENOLOGIA', 'Centro de imagenología',  'Estudios de imagen (radiología, eco, TAC, RMN)'),
    ('CENTRO_ODONTOLOGICO', 'Centro odontológico',     'Consultorio o clínica odontológica'),
    ('AMBULANCIA',          'Servicio de ambulancia',  'Traslado y atención prehospitalaria');
