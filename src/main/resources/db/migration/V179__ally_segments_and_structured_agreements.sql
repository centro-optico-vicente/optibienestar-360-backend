-- ====================================================
-- V179__ally_segments_and_structured_agreements.sql
--
-- Aligns the ally model with the client's "Convenio Corporativo de
-- Integración Comercial" (OB360):
--
--   1. ally_types gains a segment (B2B "Aliado Comercial" / B2P "Aliado
--      Profesional") and, for B2P, a professional area (MEDICAL "Área
--      Médica" / WELLNESS "Bienestar Integral"). New types are seeded for
--      the commercial and professional allies the agreement lists.
--   2. allies gains a sequential ally_number (the "Nº DE ALIADO" half of
--      the agreement file number "[CÓDIGO DE ASESOR]-[Nº DE ALIADO]"), the
--      promoter (advisor) who captured it, and the legal representative /
--      physician who signs the agreement (name + C.I./RIF).
--   3. ally_services gains free-text operational exclusions of the
--      preferential rate ("ofertas existentes, licores, cigarrillos", …).
--   4. ally_agreements links to the standard ALIADO terms version it was
--      signed under and carries the auto-renewal + rescission-notice terms
--      (1 year auto-renewing, 30 days notice by default).
-- ====================================================

SET search_path TO app, public;

-- ─── 1. ally_types: segment + professional area ─────────────────────────────

ALTER TABLE ally_types
    ADD COLUMN segment           VARCHAR(3)  NOT NULL DEFAULT 'B2B',
    ADD COLUMN professional_area VARCHAR(20) NULL,
    ADD CONSTRAINT chk_ally_types_segment
        CHECK (segment IN ('B2B', 'B2P')),
    ADD CONSTRAINT chk_ally_types_professional_area
        CHECK (professional_area IS NULL OR professional_area IN ('MEDICAL', 'WELLNESS')),
    ADD CONSTRAINT chk_ally_types_professional_area_requires_b2p
        CHECK (professional_area IS NULL OR segment = 'B2P');

COMMENT ON COLUMN ally_types.segment IS
    'Segmento del aliado: B2B = Aliado Comercial (persona jurídica), B2P = Aliado Profesional (profesional independiente)';
COMMENT ON COLUMN ally_types.professional_area IS
    'Solo para B2P: MEDICAL = Área Médica, WELLNESS = Bienestar Integral; NULL para B2B';

-- An independent practice is a professional (B2P) ally in the medical area.
UPDATE ally_types
SET segment = 'B2P', professional_area = 'MEDICAL'
WHERE code = 'CONSULTORIO';

-- New types named by the agreement. ON CONFLICT without a target skips a row
-- that collides on either unique key (code OR name) — an admin may already
-- have created one of these by hand.
INSERT INTO ally_types (code, name, description, segment, professional_area) VALUES
    ('AUTOMERCADO',           'Automercado',                 'Supermercado o automercado',                                        'B2B', NULL),
    ('FERRETERIA',            'Ferretería',                  'Ferretería y materiales de construcción',                           'B2B', NULL),
    ('AUTOMOTRIZ',            'Lubricantes y automotriz',    'Lubricantes, repuestos y servicios automotrices',                   'B2B', NULL),
    ('HOTEL',                 'Hotel',                       'Hotelería y hospedaje',                                             'B2B', NULL),
    ('VETERINARIA',           'Veterinaria',                 'Clínica veterinaria y tienda de mascotas',                          'B2B', NULL),
    ('ROPA_CALZADO',          'Ropa y calzado',              'Tienda de ropa, calzado y accesorios',                              'B2B', NULL),
    ('PROFESIONAL_MEDICO',    'Profesional médico',          'Médico independiente (pediatría, cardiología, etc.)',               'B2P', 'MEDICAL'),
    ('PROFESIONAL_BIENESTAR', 'Profesional de bienestar',    'Psicología, nutrición, entrenamiento y terapias',                   'B2P', 'WELLNESS'),
    ('SERVICIOS_LEGALES',     'Servicios legales',           'Asesoría legal independiente',                                      'B2P', 'WELLNESS')
ON CONFLICT DO NOTHING;

-- ─── 2. allies: ally number, capturing promoter, representative ─────────────

CREATE SEQUENCE allies_ally_number_seq AS INT START WITH 1 INCREMENT BY 1;

ALTER TABLE allies
    ADD COLUMN ally_number                    INT          NULL,
    ADD COLUMN captured_by_promoter_id        BIGINT       NULL REFERENCES promoters (promoters_id),
    ADD COLUMN representative_name            VARCHAR(210) NULL,
    ADD COLUMN representative_document_type   VARCHAR(1)   NULL,
    ADD COLUMN representative_document_number VARCHAR(20)  NULL;

-- Backfill existing allies in creation order (ties broken by PK).
UPDATE allies a
SET ally_number = numbered.n
FROM (SELECT allies_id, ROW_NUMBER() OVER (ORDER BY created_at, allies_id) AS n
      FROM allies) numbered
WHERE a.allies_id = numbered.allies_id;

-- Continue the sequence after the backfilled numbers (is_called=false when
-- the table is empty, so the first ally gets 1).
SELECT setval('allies_ally_number_seq',
              COALESCE((SELECT MAX(ally_number) FROM allies), 0) + 1,
              false);

ALTER SEQUENCE allies_ally_number_seq OWNED BY allies.ally_number;

ALTER TABLE allies
    ALTER COLUMN ally_number SET DEFAULT nextval('allies_ally_number_seq'),
    ALTER COLUMN ally_number SET NOT NULL,
    ADD CONSTRAINT uq_allies_ally_number UNIQUE (ally_number),
    ADD CONSTRAINT chk_allies_representative_document_type
        CHECK (representative_document_type IS NULL
               OR representative_document_type IN ('J', 'V', 'E', 'G', 'P')),
    ADD CONSTRAINT chk_allies_representative_document CHECK (
        (representative_document_type IS NULL AND representative_document_number IS NULL) OR
        (representative_document_type IS NOT NULL AND representative_document_number IS NOT NULL)
    );

CREATE INDEX idx_allies_captured_by_promoter
    ON allies (captured_by_promoter_id)
    WHERE captured_by_promoter_id IS NOT NULL;

COMMENT ON COLUMN allies.ally_number IS
    'Número secuencial del aliado (Nº DE ALIADO); asignado por la BD, nunca editable';
COMMENT ON COLUMN allies.captured_by_promoter_id IS
    'Asesor (promotor) que captó al aliado; su código de referido forma el expediente [CÓDIGO DE ASESOR]-[Nº DE ALIADO]';
COMMENT ON COLUMN allies.representative_name IS
    'Nombre del representante legal o médico que firma el convenio';
COMMENT ON COLUMN allies.representative_document_type IS
    'Tipo de documento del representante (J/V/E/G/P, mismos prefijos que el RIF del aliado)';
COMMENT ON COLUMN allies.representative_document_number IS
    'Número de C.I./RIF del representante; obligatorio junto con el tipo (ambos o ninguno)';

-- ─── 3. ally_services: operational exclusions ───────────────────────────────

ALTER TABLE ally_services
    ADD COLUMN exclusions TEXT NULL;

COMMENT ON COLUMN ally_services.exclusions IS
    'Exclusiones operativas de la tarifa preferencial (p. ej. ofertas existentes, licores, cigarrillos, insumos de alto costo)';

-- ─── 4. ally_agreements: standard terms version, auto-renewal, notice ───────

ALTER TABLE ally_agreements
    ADD COLUMN terms_version_id  BIGINT  NULL REFERENCES terms_versions (terms_versions_id),
    ADD COLUMN auto_renew        BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN notice_days       INT     NOT NULL DEFAULT 30,
    ADD CONSTRAINT chk_ally_agreements_notice_days CHECK (notice_days >= 0);

CREATE INDEX idx_ally_agreements_terms_version
    ON ally_agreements (terms_version_id)
    WHERE terms_version_id IS NOT NULL;

COMMENT ON COLUMN ally_agreements.terms_version_id IS
    'Versión del convenio estándar (terms_versions con term_type = ALIADO) bajo la que se firmó';
COMMENT ON COLUMN ally_agreements.auto_renew IS
    'Renovación automática al vencer la vigencia (por defecto anual y automática)';
COMMENT ON COLUMN ally_agreements.notice_days IS
    'Días de preaviso requeridos para rescindir el convenio (por defecto 30)';
