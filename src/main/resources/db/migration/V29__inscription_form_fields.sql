SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V29: digitize the paper inscription form ("Planilla de Inscripción",
-- Centro Óptico Vicente / OPTIBIENESTAR 360). The form captures a handful of
-- titular fields that had no home in the schema yet. Splitting them the same
-- way V15 split the rest of the identity data:
--
--   persons  — personal/demographic data that travels with the human
--              (shared by titular, beneficiary, user roles).
--   members  — employment snapshot of the titular at affiliation time,
--              alongside the existing occupation_id (V17). Beneficiaries have
--              no employment context, so these live on members, not persons.
--
-- Phones: the form has two slots (Teléfono Fijo + Celular). V15 anticipated
-- this ("promoted to dedicated tables later if multi-phone requirements
-- emerge") but two fixed slots don't justify a person_phones side table — the
-- existing `phone` column stays as the mobile/celular, and we add a single
-- `landline_phone` for the fijo.
--
-- Cónyuge: `spouse_name` is the literal free-text spouse line from the form.
-- If the spouse is actually affiliated they ALSO appear as a Beneficiary with
-- relationship = 'SPOUSE' (V18); this column is just the paper-faithful
-- capture and is not kept in sync with that beneficiary row.
-- ────────────────────────────────────────────────────────────────────────────

-- ─── persons: titular demographics from the form ───────────────────────────
ALTER TABLE persons
    ADD COLUMN birthplace         VARCHAR(120),                 -- "Lugar de Nacimiento" (free text: city/state)
    ADD COLUMN number_of_children INTEGER,                      -- "Cantidad de Hijos"
    ADD COLUMN landline_phone     VARCHAR(30),                  -- "Teléfono Fijo" (phone column = celular)
    ADD COLUMN spouse_name        VARCHAR(210);                 -- "Cónyuge" (paper-faithful; see header note)

ALTER TABLE persons
    ADD CONSTRAINT chk_persons_number_of_children
        CHECK (number_of_children IS NULL OR number_of_children >= 0);

-- ─── members: employment snapshot from the form ────────────────────────────
ALTER TABLE members
    ADD COLUMN employer_name    VARCHAR(150),                   -- "Lugar de Trabajo"
    ADD COLUMN job_position     VARCHAR(100),                   -- "Cargo" (distinct from occupation catalog)
    ADD COLUMN employer_address TEXT;                           -- "Dirección Empresa"
