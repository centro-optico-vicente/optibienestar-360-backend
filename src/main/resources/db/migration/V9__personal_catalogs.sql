SET search_path TO app, public;

-- ============================================================
-- Personal / demographic catalogs — genders, document_types,
-- marital_statuses, occupations. Fixed-set lookups consumed by
-- member-related entities (V15+). Same conventions as V8:
-- BIGINT identity PK + uuid, is_active soft-delete, audit
-- columns, set_updated_at trigger, partial is_active index.
-- ============================================================

-- ------------------------------------------------------------
-- genders: M/F/O — no external FKs
-- ------------------------------------------------------------
CREATE TABLE genders
(
    genders_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid       UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    code       VARCHAR(1)   NOT NULL UNIQUE,             -- 'M' / 'F' / 'O'
    name       VARCHAR(20)  NOT NULL UNIQUE,
    is_active  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

CREATE INDEX idx_genders_is_active ON genders (is_active) WHERE is_active;

CREATE TRIGGER trg_genders_updated_at
    BEFORE UPDATE ON genders
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ------------------------------------------------------------
-- document_types: V/E/J/P — catálogo canónico. Normaliza el
-- CHECK inline de users.document_type (V5, hoy 'V','E') al
-- centralizar el set permitido. users sigue con su columna
-- VARCHAR + CHECK hasta una migración futura que lo cablee
-- como FK al catálogo (members/V15+ ya FK-eará desde el inicio).
-- ------------------------------------------------------------
CREATE TABLE document_types
(
    document_types_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid              UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    code              VARCHAR(3)   NOT NULL UNIQUE,      -- 'V' / 'E' / 'J' / 'P'
    name              VARCHAR(60)  NOT NULL UNIQUE,
    description       VARCHAR(200),
    is_active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID
);

CREATE INDEX idx_document_types_is_active ON document_types (is_active) WHERE is_active;

CREATE TRIGGER trg_document_types_updated_at
    BEFORE UPDATE ON document_types
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ------------------------------------------------------------
-- marital_statuses: estados civiles — no external FKs
-- ------------------------------------------------------------
CREATE TABLE marital_statuses
(
    marital_statuses_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    code                VARCHAR(20)  NOT NULL UNIQUE,
    name                VARCHAR(50)  NOT NULL UNIQUE,
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID
);

CREATE INDEX idx_marital_statuses_is_active ON marital_statuses (is_active) WHERE is_active;

CREATE TRIGGER trg_marital_statuses_updated_at
    BEFORE UPDATE ON marital_statuses
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ------------------------------------------------------------
-- occupations: ocupaciones — lista curada. Sin código natural;
-- el name UNIQUE es suficiente como clave funcional (la lista
-- crece según necesidad operativa). description es opcional —
-- desambigua entradas similares (p.ej. Independiente vs
-- Profesional independiente).
-- ------------------------------------------------------------
CREATE TABLE occupations
(
    occupations_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid           UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    name           VARCHAR(100) NOT NULL UNIQUE,
    description    VARCHAR(200),
    is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID
);

CREATE INDEX idx_occupations_is_active ON occupations (is_active) WHERE is_active;

CREATE TRIGGER trg_occupations_updated_at
    BEFORE UPDATE ON occupations
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ============================================================
-- Seed
-- ============================================================

INSERT INTO genders (code, name) VALUES
    ('M', 'Masculino'),
    ('F', 'Femenino'),
    ('O', 'Otro');

INSERT INTO document_types (code, name, description) VALUES
    ('V', 'Cédula de identidad venezolana',     'Persona natural venezolana (V-XXXXXXXX)'),
    ('E', 'Cédula de identidad de extranjero',  'Persona natural extranjera residente (E-XXXXXXXX)'),
    ('J', 'RIF jurídico',                       'Persona jurídica registrada en el SENIAT (J-XXXXXXXX-X)'),
    ('P', 'Pasaporte',                          'Documento de viaje internacional');

INSERT INTO marital_statuses (code, name) VALUES
    ('SOLTERO',     'Soltero'),
    ('CASADO',      'Casado'),
    ('DIVORCIADO',  'Divorciado'),
    ('VIUDO',       'Viudo'),
    ('CONCUBINATO', 'Concubinato');

-- Lista curada inicial; se amplía según necesidades operativas.
INSERT INTO occupations (name, description) VALUES
    ('Ama de casa',                'Trabajo doméstico no remunerado en el hogar'),
    ('Comerciante',                'Vendedor independiente o dueño de negocio propio'),
    ('Docente',                    'Profesional de la educación en cualquier nivel'),
    ('Empleado público',           'Trabajador del Estado o de entes públicos'),
    ('Empleado privado',           'Trabajador del sector privado en relación de dependencia'),
    ('Estudiante',                 'Cursando estudios formales (cualquier nivel)'),
    ('Independiente',              'Trabajador por cuenta propia sin clasificación específica'),
    ('Jubilado',                   'Persona retirada con pensión'),
    ('Médico',                     'Profesional de la medicina'),
    ('Obrero',                     'Trabajador manual o industrial'),
    ('Oficinista',                 'Personal administrativo de oficina'),
    ('Otro',                       'Ocupación no listada en este catálogo'),
    ('Profesional independiente',  'Profesional universitario por cuenta propia (abogado, contador, ingeniero, etc.)'),
    ('Trabajador agrícola',        'Labores del campo y producción agropecuaria'),
    ('Transportista',              'Conductor de carga o pasajeros');
