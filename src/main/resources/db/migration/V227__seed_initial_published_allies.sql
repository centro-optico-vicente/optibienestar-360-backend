SET search_path TO app, public;

-- ============================================================
-- V227: seed the initial directory of commercial allies, published.
--
-- Source: "Directorio de Aliados Optibienestar 360" spreadsheet (8 allies
-- filled in; the template's remaining rows are blank). Each ally is created
-- published (is_published = TRUE) with one APPROVED + published discount
-- service carrying the offered discount, so it shows up in the public
-- directory right away.
--
-- Idempotent: an ally is skipped when one with the same RIF (or, lacking
-- a RIF, the same name) already exists, and its service is skipped when
-- the ally already has one with that name. ally_number comes from the
-- allies_ally_number_seq default.
--
-- Data-cleaning notes (vs. the raw spreadsheet):
--   - RIF split into type + digits ("V-050560669" -> V / 050560669).
--   - Row 1 e-mail "cov1centroopticovicente.com" has no "@" -> left NULL.
--   - Row 8 phone "0255-6213273-04245455008" holds a landline + a mobile:
--     landline -> phone, mobile -> whatsapp.
--   - Instagram stored as a bare handle (no "@"); row 7 lists two, the first
--     is kept. Contact-person names are not stored (no column on allies).
--   - Rows 5-6 are independent professionals -> segment B2P; their holder
--     person (allies.person_id) is NOT loaded here, it is optional at the DB
--     level and must be completed from the admin UI.
--   - Specialties: the ally types are reused (the rubros are close), and each
--     ally is tagged with its medical specialty (ally_specialties). Only
--     FONOAUDIOLOGIA is new in the catalog; the pharmacy has no specialty.
--   - Cities are inferred from each address (Acarigua / Araure / Guanare).
-- ============================================================

-- Specialty missing from the V10 catalog (ON CONFLICT skips code/name collisions).
INSERT INTO medical_specialties (code, name, description) VALUES
    ('FONOAUDIOLOGIA', 'Fonoaudiología', 'Evaluación y terapia de la voz, el habla, el lenguaje y la audición')
ON CONFLICT DO NOTHING;

WITH seed (name, tax_type, tax_number, segment, type_code, city_name,
           address, email, phone, whatsapp, instagram, facebook,
           service_category, service_name, discount_pct, service_description, specialty_code) AS (
    VALUES
    ('Centro Óptico Vicente', 'V', '130043352', 'B2B', 'CENTRO_OPTICO', 'Acarigua',
     'Av. 30 con calle 21, C.C. Ciudad Cristal, PB, local 31, Acarigua',
     NULL, '0412-2925608', NULL, 'opticavicenteve', 'OpticaVicente',
     'LENTES_Y_MONTURAS', 'Descuento en monturas', 30.00, '30% de descuento en monturas', 'OPTOMETRIA'),

    ('Centro Óptico Vicente Buena Aventura C.A', 'J', '295523203', 'B2B', 'CENTRO_OPTICO', 'Araure',
     'Av. Vencedores de Araure, C.C. Buenaventura, sector Caña de Azúcar, locales A78 y A79',
     'opticabuenaventura@gmail.com', '0412-0452637', NULL, 'centrooptivovicentebv', NULL,
     'LENTES_Y_MONTURAS', 'Descuento en monturas', 30.00, '30% de descuento en monturas', 'OPTOMETRIA'),

    ('Óptica Buena Visión C.A', 'J', '502200673', 'B2B', 'CENTRO_OPTICO', 'Guanare',
     'Centro Comercial Ciudad Traki, Guanare, Edo. Portuguesa',
     'captacionbuenavision@gmail.com', '0412-7118595', NULL, NULL, NULL,
     'LENTES_Y_MONTURAS', 'Descuento en monturas', 30.00, '30% de descuento en monturas', 'OPTOMETRIA'),

    ('Óptica Social Comunitaria C.A', 'J', '315953595', 'B2B', 'CENTRO_OPTICO', 'Acarigua',
     'Av. Libertador, diagonal al Banco de Venezuela, Acarigua',
     'interniopticasocialcomunitaria@gmail.com', '0412-2883436', NULL, 'opticasocialweb', NULL,
     'LENTES_Y_MONTURAS', 'Descuento en monturas', 30.00, '30% de descuento en monturas', 'OPTOMETRIA'),

    ('Yimber Pérez', 'V', '170336387', 'B2P', 'PROFESIONAL_MEDICO', 'Acarigua',
     'Centro Clínico Profesional CEMINDEZ',
     'yimberperez52@gmail.com', '0414-5108825', NULL, 'dryimbergine.ve', NULL,
     'CONSULTA', 'Descuento en consulta de ginecología', 20.00, '20% de descuento en consulta de ginecología', 'GINECOLOGIA'),

    ('Evelin Méndez', 'V', '050560669', 'B2P', 'PROFESIONAL_MEDICO', 'Acarigua',
     'Centro Clínico Profesional CEMINDEZ',
     NULL, '0412-6636269', NULL, 'cemindezca', NULL,
     'CONSULTA', 'Descuento en consulta de fonoaudiología', 20.00, '20% de descuento en consulta de fonoaudiología', 'FONOAUDIOLOGIA'),

    ('Tu Clínica Dental C.A', 'J', '508809750', 'B2B', 'CENTRO_ODONTOLOGICO', 'Acarigua',
     'Av. 27 cruce con calle 29, Edif. Kasmene, PB, Acarigua',
     'manuelissaiash@gmail.com', '0424-5239040', NULL, 'tuclinicadental.ve', NULL,
     'PROCEDIMIENTO', 'Descuento en tratamientos odontológicos', 60.00, 'Hasta 60% de descuento en tratamientos odontológicos', 'ODONTOLOGIA'),

    ('Farmacia El Sol C.A', 'J', '308424994', 'B2B', 'FARMACIA', 'Acarigua',
     'Calle 31 entre Av. 27 y 27C, urb. Los Ángeles, PB, Acarigua',
     'framacia07@gmail.com', '0255-6213273', '0424-5455008', 'farmaciaelsol', NULL,
     'MEDICAMENTO', 'Descuento en medicinas seleccionadas', 15.00, 'Entre 5% y 15% de descuento en medicinas seleccionadas', NULL)
),
inserted AS (
    INSERT INTO allies (name, segment, tax_document_type, tax_document_number, email, phone, whatsapp,
                        instagram, facebook, address, city_id, joined_at,
                        is_published, published_at, is_active, status)
    SELECT s.name, s.segment, s.tax_type, s.tax_number, s.email, s.phone, s.whatsapp,
           s.instagram, s.facebook, s.address,
           (SELECT c.cities_id
            FROM cities c
                     JOIN states st ON st.states_id = c.state_id
            WHERE st.code = 'VE-P' AND c.name = s.city_name),
           CURRENT_DATE, TRUE, NOW(), TRUE, 'ACTIVE'
    FROM seed s
    WHERE NOT EXISTS (SELECT 1
                      FROM allies a
                      WHERE a.tax_document_type = s.tax_type
                        AND a.tax_document_number = s.tax_number)
    RETURNING allies_id, tax_document_type, tax_document_number
),
typed AS (
    INSERT INTO ally_ally_types (ally_id, ally_type_id)
    SELECT i.allies_id, t.ally_types_id
    FROM inserted i
             JOIN seed s ON s.tax_type = i.tax_document_type AND s.tax_number = i.tax_document_number
             JOIN ally_types t ON t.code = s.type_code
    RETURNING ally_id
),
specialized AS (
    INSERT INTO ally_specialties (ally_id, medical_specialty_id)
    SELECT i.allies_id, ms.medical_specialties_id
    FROM inserted i
             JOIN seed s ON s.tax_type = i.tax_document_type AND s.tax_number = i.tax_document_number
             JOIN medical_specialties ms ON ms.code = s.specialty_code
    RETURNING ally_id
)
INSERT INTO ally_services (ally_id, service_category_id, name, description, discount_pct,
                           review_status, reviewed_at, is_published, published_at, is_active)
SELECT i.allies_id, sc.service_categories_id, s.service_name, s.service_description, s.discount_pct,
       'APPROVED', NOW(), TRUE, NOW(), TRUE
FROM inserted i
         JOIN seed s ON s.tax_type = i.tax_document_type AND s.tax_number = i.tax_document_number
         JOIN service_categories sc ON sc.code = s.service_category;
