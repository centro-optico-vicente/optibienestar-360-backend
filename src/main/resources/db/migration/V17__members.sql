SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V17: members — the program subscribers (titulares de afiliación).
--
-- Members are 1:1 with `persons` (the identity hub from V15). All demographic
-- data — names, cédula, RIF, contact, address, birth date, gender, marital
-- status — lives in persons and is never duplicated here. This table only
-- carries fields specific to BEING a program subscriber (enrollment date,
-- occupation snapshot, notes, etc.).
--
-- Beneficiaries (V18, planned) are also 1:1 with persons but are children of
-- a parent Member, not Members themselves — every Member is a titular by
-- definition, so no `is_holder` flag is needed.
--
-- `member_documents` carries uploaded files (cédula scans, proof of address,
-- member photo for the digital card, income proof for subsidy requests, etc.).
-- Files live in Cloudflare R2; `file_url` stores the R2 key.
-- ────────────────────────────────────────────────────────────────────────────

CREATE TABLE members
(
    members_id      BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid            UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    -- Identity hub link — 1:1 with persons. UNIQUE strict (not partial):
    -- readmission is implemented as reactivating the existing row, not as
    -- inserting a second row, so the same person can never have two
    -- member rows even one inactive.
    person_id       BIGINT       NOT NULL UNIQUE REFERENCES persons (persons_id),

    -- Occupation at affiliation time (catalog V9). Person doesn't have an
    -- occupation column because the role-table-specific snapshot semantics
    -- live here: if a member changes jobs after affiliation, this stays at
    -- their occupation when they joined (relevant for marketing segmentation
    -- and any future occupation-based pricing).
    occupation_id   BIGINT       REFERENCES occupations (occupations_id),

    enrolled_at     DATE         NOT NULL DEFAULT CURRENT_DATE,
    notes           TEXT,

    -- Audit + soft-delete (BaseEntity-style)
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    status          VARCHAR(50),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID
);

-- person_id already has a UNIQUE index from the constraint above.
CREATE INDEX idx_members_occupation  ON members (occupation_id) WHERE occupation_id IS NOT NULL;
CREATE INDEX idx_members_enrolled_at ON members (enrolled_at);

CREATE TRIGGER trg_members_updated_at
    BEFORE UPDATE ON members
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ─── member_documents ──────────────────────────────────────────────────────
CREATE TABLE member_documents
(
    member_documents_id  BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                 UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    member_id            BIGINT       NOT NULL REFERENCES members (members_id) ON DELETE CASCADE,

    -- Document classification — drives the upload-once-per-type guard at the
    -- service layer. CHECK keeps the enum small enough that the frontend can
    -- show a dropdown without a catalog table; new types added via migration.
    document_type        VARCHAR(50)  NOT NULL
                            CHECK (document_type IN (
                                'ID_FRONT', 'ID_BACK', 'PROOF_OF_RESIDENCE',
                                'MEDICAL_HISTORY', 'MEMBER_PHOTO', 'INCOME_PROOF', 'OTHER'
                            )),

    -- File storage — R2 key in `file_url`. Original name + size + mime kept
    -- for the download surface and for content-type validation.
    file_url             VARCHAR(500) NOT NULL,
    file_name            VARCHAR(255) NOT NULL,
    file_size_bytes      BIGINT       NOT NULL CHECK (file_size_bytes > 0),
    mime_type            VARCHAR(100) NOT NULL,

    uploaded_by          BIGINT       REFERENCES users (users_id),

    -- Audit + soft-delete
    is_active            BOOLEAN      NOT NULL DEFAULT TRUE,
    status               VARCHAR(50),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by           UUID,
    updated_by           UUID
);

CREATE INDEX idx_member_documents_member       ON member_documents (member_id);
CREATE INDEX idx_member_documents_member_type  ON member_documents (member_id, document_type);

CREATE TRIGGER trg_member_documents_updated_at
    BEFORE UPDATE ON member_documents
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
