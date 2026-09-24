SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V156: rename the `medical_specialties` catalog to `professions`. Allies are
-- not limited to medical staff (pharmacies, labs, etc.), so "profession" is
-- the correct generic name for this M:N catalog. Pure rename — cardinality
-- unchanged (still M:N via the ally_professions pivot, ex ally_specialties).
-- ────────────────────────────────────────────────────────────────────────────

-- ─── medical_specialties -> professions ─────────────────────────────────────
ALTER TABLE medical_specialties RENAME TO professions;
ALTER TABLE professions RENAME COLUMN medical_specialties_id TO professions_id;

ALTER INDEX idx_medical_specialties_is_active RENAME TO idx_professions_is_active;

ALTER TRIGGER trg_medical_specialties_updated_at ON professions RENAME TO trg_professions_updated_at;

-- ─── ally_specialties -> ally_professions ───────────────────────────────────
ALTER TABLE ally_specialties RENAME TO ally_professions;
ALTER TABLE ally_professions RENAME COLUMN medical_specialty_id TO profession_id;
ALTER TABLE ally_professions RENAME CONSTRAINT ally_specialties_ally_id_medical_specialty_id_key TO ally_professions_ally_id_profession_id_key;

ALTER INDEX idx_ally_specialties_specialty RENAME TO idx_ally_professions_profession;

-- ─── Permissions: MEDICAL_SPECIALTY -> PROFESSION ───────────────────────────
-- role_permissions references permissions.permissions_id (surrogate PK), so
-- renaming permissions.name here does not orphan any existing role grants.
UPDATE permissions SET name = REPLACE(name, 'MEDICAL_SPECIALTY', 'PROFESSION')
WHERE name LIKE '%MEDICAL_SPECIALTY%';

UPDATE permission_domains
SET code        = 'CATALOG_PROFESSION',
    name        = 'Profesiones',
    description = 'Catálogo de profesiones',
    icon        = 'i-lucide-briefcase-medical'
WHERE code = 'CATALOG_MEDICAL_SPECIALTY';
