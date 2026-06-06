SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V19: medical_records — 1:1 with persons. One row holds the persistent
-- medical profile of a person (blood type, allergies, chronic conditions,
-- current medications, emergency contact) regardless of which program role
-- they hold — same record covers them whether they're a Member titular or
-- a Beneficiary in another titular's plan.
--
-- This table is the canonical EMR-lite for v1. Individual consultation /
-- visit entries (a real EMR) are out of scope; if they're needed later, a
-- `medical_record_entries` child table can be added without touching this
-- row. The `notes` column carries free-text history for now.
--
-- PRIVACY: ally users (clinics, pharmacies) must NEVER see medical_records
-- via API. The query-anonymization rule is enforced at the application /
-- @PreAuthorize layer (vertical-4 bullet: "Anonimización en queries de
-- aliados — no expone MedicalRecord").
-- ────────────────────────────────────────────────────────────────────────────

CREATE TABLE medical_records
(
    medical_records_id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                            UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    -- 1:1 with persons (UNIQUE strict). A person has either zero or one
    -- medical record — captured at affiliation or filled in later.
    person_id                       BIGINT       NOT NULL UNIQUE REFERENCES persons (persons_id),

    -- Blood type stored as the canonical short form ('A+', 'O-', 'AB+', …).
    -- Kept as a small VARCHAR + CHECK rather than enum so the column can be
    -- queried with plain string equality from any client.
    blood_type                      VARCHAR(5)
                                        CHECK (blood_type IN ('A+','A-','B+','B-','AB+','AB-','O+','O-')),

    -- Free-text fields — keep concise so the digital card / emergency view
    -- can show them inline.
    allergies                       TEXT,
    chronic_conditions              TEXT,
    current_medications             TEXT,

    -- Emergency contact — single contact, structured. Add a separate
    -- medical_record_contacts table later if multi-contact is needed.
    emergency_contact_name          VARCHAR(200),
    emergency_contact_phone         VARCHAR(30),
    emergency_contact_relationship  VARCHAR(20)
                                        CHECK (emergency_contact_relationship IN (
                                            'SPOUSE', 'CHILD', 'PARENT', 'SIBLING', 'FRIEND', 'OTHER'
                                        )),

    notes                           TEXT,

    -- Audit + soft-delete (BaseEntity-style)
    is_active                       BOOLEAN      NOT NULL DEFAULT TRUE,
    status                          VARCHAR(50),
    created_at                      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by                      UUID,
    updated_by                      UUID
);

-- person_id already has the UNIQUE index from the constraint. No extra
-- index needed: medical_records is small (one row per person), queried by
-- person_id, never paginated or full-text-searched.

CREATE TRIGGER trg_medical_records_updated_at
    BEFORE UPDATE ON medical_records
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
