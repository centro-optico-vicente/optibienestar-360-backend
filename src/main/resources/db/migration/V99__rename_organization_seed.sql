SET search_path TO app, public;

-- ============================================================
-- V99: rename the seeded organization row to the OptiBienestar 360 brand
-- (V86 already ran in every environment, so its INSERT cannot be edited in
-- place — Flyway checksums an applied migration; a correction is always a
-- new migration, same as the rebrand precedent for `optibienestar360_*`
-- roles/db).
-- ============================================================

UPDATE organizations
SET name       = 'OptiBienestar 360',
    legal_name = 'Centro Óptico Vicente / Grupo Médico 11:11'
WHERE name = 'Centro Óptico Vicente';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM organizations WHERE name = 'OptiBienestar 360') THEN
        RAISE EXCEPTION 'V99: organization seed row was not renamed';
    END IF;
END $$;
