SET search_path TO app, public;

-- persons: single source of truth for civic / contact identity across the
-- platform. Every human (a User, a Member titular, a Beneficiary, a Promoter,
-- an Ally contact) corresponds to exactly one row here. Role tables
-- (users, members, beneficiaries, etc.) reference persons via FK instead of
-- duplicating full_name / document / phone / etc. — see ADR 0012.
--
-- Name parts: split following LATAM standard (alineado con proyecto-iv-mh
-- tglo_PERSONA_m: pri_nombre / seg_nombre / pri_apellido / seg_apellido).
-- The composed full_name is a GENERATED STORED column for query + indexing
-- convenience; the app never sets it directly.
--
-- Document: cédula VE (V/E) is required identity. RIF (tax document) is
-- optional and modeled as a separate type+number pair because the two are
-- not always derivable from each other (a person can have a J-prefixed RIF
-- from a single-owner company while their cédula is V-prefixed).
--
-- Contact + address fields are inline (no person_contacts / person_addresses
-- side tables) — promoted to dedicated tables later if multi-phone /
-- multi-address requirements emerge.
CREATE TABLE persons
(
    persons_id          BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    -- Civic identity (4-part LATAM convention)
    first_name          VARCHAR(50)  NOT NULL,
    middle_name         VARCHAR(50),
    last_name           VARCHAR(50)  NOT NULL,
    second_last_name    VARCHAR(50),
    -- Derived display name; never set by the app.
    full_name           VARCHAR(210) GENERATED ALWAYS AS (
        TRIM(REGEXP_REPLACE(
            COALESCE(first_name, '')        || ' ' ||
            COALESCE(middle_name, '')       || ' ' ||
            COALESCE(last_name, '')         || ' ' ||
            COALESCE(second_last_name, ''),
            '\s+', ' ', 'g'))
    ) STORED,

    -- Personal document (cédula VE)
    document_type       VARCHAR(2)   NOT NULL CHECK (document_type IN ('V', 'E')),
    document_number     VARCHAR(20)  NOT NULL,

    -- Tax document (RIF VE) — optional
    tax_document_type   VARCHAR(1)   CHECK (tax_document_type IN ('J', 'V', 'E', 'G', 'P')),
    tax_document_number VARCHAR(20),

    birth_date          DATE,
    gender_id           BIGINT       REFERENCES genders (genders_id),
    marital_status_id   BIGINT       REFERENCES marital_statuses (marital_statuses_id),

    -- Canonical contact (nullable for minors w/o their own phone/email)
    phone               VARCHAR(30),
    email               CITEXT,
    -- BCP47 locale preference travels with the person, not with the auth
    -- account — a non-User Member still has a preferred language for
    -- notifications.
    locale              VARCHAR(10),

    -- Address (optional; city FK references the V8 catalog, also optional)
    address             TEXT,
    city_id             BIGINT       REFERENCES cities (cities_id),

    -- Audit + soft-delete (mirrors BaseEntity in Java)
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,
    status              VARCHAR(50),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,

    -- One cédula = one person, platform-wide
    UNIQUE (document_type, document_number),
    -- chk: tax document type+number both NULL or both set
    CONSTRAINT chk_persons_tax_document CHECK (
        (tax_document_type IS NULL AND tax_document_number IS NULL) OR
        (tax_document_type IS NOT NULL AND tax_document_number IS NOT NULL)
    )
);

-- RIF unique when present — partial unique index allows many NULL pairs
CREATE UNIQUE INDEX uniq_persons_tax_document
    ON persons (tax_document_type, tax_document_number)
    WHERE tax_document_type IS NOT NULL AND tax_document_number IS NOT NULL;

CREATE INDEX idx_persons_email     ON persons (email)     WHERE email     IS NOT NULL;
CREATE INDEX idx_persons_city_id   ON persons (city_id)   WHERE city_id   IS NOT NULL;
CREATE INDEX idx_persons_last_name ON persons (last_name);

-- Full-text search on the derived full_name with unaccent so "Mérida" matches
-- "merida". Reused by Member/Beneficiary listings via JOIN to persons.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_persons_full_name_unaccent
    ON persons USING gin (unaccent(lower(full_name)) gin_trgm_ops);

CREATE TRIGGER trg_persons_updated_at
    BEFORE UPDATE ON persons
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
