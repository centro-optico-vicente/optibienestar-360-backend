SET search_path TO app, public;

-- ============================================================================
-- V116: banks — bank master catalog (hub plan
-- ".ai/plans/2026-09-17-payments-unification-plan.md").
--
-- Follows the exact pattern of `currencies` (V84): dual identifier (BIGINT PK
-- + uuid per ADR 0006), `code` as the unique natural key other tables' FKs
-- point at via the BIGINT id (never at `code` directly), API resolves
-- bank_Uuid/bank_Display/bank_Code per ADR 0014.
--
-- Motivation: `payment_methods` (V115) has `is_mandatory_bank_account` for
-- BANK_TRANSFER, INTERNATIONAL_TRANSFER, CHECK and BANK_DEPOSIT — today there
-- is nowhere to point that "bank account" data at. A real `banks` catalog is
-- the prerequisite: `payment_lines` (not yet implemented) will need a
-- nullable `bank_id` FK to this table for any line whose method requires it,
-- instead of a free-text bank name.
--
-- Seed: bancos participantes en la Cámara de Compensación Electrónica (SUDEBAN),
-- verificado contra el listado oficial vigente aportado por el dueño del
-- producto (2026-09-18) — el catálogo legado `tglo_BANCO` usado en el primer
-- borrador de este archivo tenía dos errores que este seed corrige:
--   - código 0169 estaba asignado a "Mi Banco" (nombre que no existe en el
--     listado SUDEBAN vigente); el código correcto de esa entidad es R4,
--     Banco Microfinanciero.
--   - "Banco Digital de los Trabajadores" estaba con código 0185 (inexistente
--     en el listado SUDEBAN); su código correcto es 0175.
-- `short_name` es el nombre comercial corto (el que usaría un selector de
-- UI o un recibo, ej. "Banesco" en vez de "Banesco, Banco Universal S.A.C.A.").
--
-- `tax_document_type`/`tax_document_number` (RIF, prefijo + número) siguen el
-- mismo split usado por `persons` (V15) y `allies` (V11) en vez de un único
-- campo `rif` — mismo dominio de dato, misma convención en todo el sistema.
-- ============================================================================

CREATE TABLE banks
(
    banks_id             BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                 UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    code                 VARCHAR(10)  NOT NULL UNIQUE,   -- SUDEBAN bank code (e.g. '0102')
    name                 VARCHAR(120) NOT NULL,          -- legal/full name (razón social)
    short_name           VARCHAR(60)  NOT NULL,          -- commercial/short name, for UI pickers and receipts

    -- RIF, split the same way as persons.tax_document_type/number (V15) and
    -- allies.tax_document_type/number (V11) — banks are always legal entities
    -- (J) except BCV, which SUDEBAN lists under G.
    tax_document_type    VARCHAR(1)   NOT NULL CHECK (tax_document_type IN ('J', 'G')),
    tax_document_number  VARCHAR(20)  NOT NULL,

    is_active            BOOLEAN      NOT NULL DEFAULT TRUE,
    status                VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by              UUID,
    updated_by              UUID,

    UNIQUE (tax_document_type, tax_document_number)
);

CREATE INDEX idx_banks_is_active ON banks (is_active) WHERE is_active;

CREATE TRIGGER trg_banks_updated_at
    BEFORE UPDATE ON banks
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Seed: bancos venezolanos participantes en la Cámara de Compensación Electrónica.
-- RIF tomado del listado oficial SUDEBAN de instituciones participantes (mismo
-- listado usado para corregir los códigos 0169/0175), separado en
-- tax_document_type/tax_document_number.
INSERT INTO banks (code, name, short_name, tax_document_type, tax_document_number) VALUES
    ('0001', 'Banco Central de Venezuela',                                  'BCV',                  'G', '200001100'),
    ('0102', 'Banco de Venezuela S.A.C.A. Banco Universal',                 'Banco de Venezuela',   'G', '200099976'),
    ('0104', 'Venezolano de Crédito, S.A. Banco Universal',                 'Venezolano de Crédito','J', '000029709'),
    ('0105', 'Mercantil Banco, C.A. Banco Universal',                       'Mercantil',            'J', '000029610'),
    ('0108', 'BBVA Provincial, S.A. Banco Universal',                       'BBVA Provincial',      'J', '000029679'),
    ('0114', 'Bancaribe C.A. Banco Universal',                              'Bancaribe',            'J', '000029490'),
    ('0115', 'Banco Exterior C.A. Banco Universal',                        'Banco Exterior',        'J', '000029504'),
    ('0128', 'Banco Caroní C.A. Banco Universal',                          'Banco Caroní',          'J', '095048551'),
    ('0134', 'Banesco, Banco Universal S.A.C.A.',                          'Banesco',               'J', '070133805'),
    ('0137', 'Banco Sofitasa, Banco Universal',                            'Sofitasa',              'J', '090283846'),
    ('0138', 'Banco Plaza, Banco Universal',                               'Banco Plaza',           'J', '002970553'),
    ('0146', 'Bangente C.A',                                                'Bangente',             'J', '301442040'),
    ('0151', 'BFC Banco Fondo Común C.A. Banco Universal',                 'BFC',                   'J', '000723060'),
    ('0156', '100% Banco, Banco Universal C.A.',                           '100% Banco',            'J', '085007768'),
    ('0157', 'DelSur Banco Universal C.A.',                                'DelSur',                'J', '000797234'),
    ('0163', 'Banco del Tesoro, C.A. Banco Universal',                     'Banco del Tesoro',      'G', '200051876'),
    ('0166', 'Banco Agrícola de Venezuela, C.A. Banco Universal',          'Banco Agrícola de Venezuela', 'G', '200057955'),
    ('0168', 'Bancrecer, S.A. Banco Microfinanciero',                      'Bancrecer',             'J', '316374173'),
    ('0169', 'R4, Banco Microfinanciero C.A.',                             'R4',                    'J', '315941023'),
    ('0171', 'Banco Activo, Banco Universal',                              'Banco Activo',          'J', '080066227'),
    ('0172', 'Bancamiga, Banco Universal C.A.',                            'Bancamiga',             'J', '316287599'),
    ('0173', 'Banco Internacional de Desarrollo, C.A. Banco Universal',    'BID',                   'J', '294640109'),
    ('0174', 'Banplus Banco Universal, C.A',                               'Banplus',               'J', '000423032'),
    ('0175', 'Banco Digital de Los Trabajadores',                         'BDT',                    'G', '200091487'),
    ('0177', 'Banco de la Fuerza Armada Nacional Bolivariana, B.U.',       'BANFANB',               'G', '200106573'),
    ('0178', 'N58 Banco Digital, S.A.',                                   'N58',                    'J', '503581107'),
    ('0191', 'Banco Nacional de Crédito, C.A. Banco Universal',           'BNC',                    'J', '309841327'),
    ('0601', 'Instituto Municipal de Crédito Popular',                    'IMC',                    'J', '000145903');
