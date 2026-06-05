SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V11: directorio de aliados comerciales y médicos.
-- Tablas: allies, ally_specialties, ally_services, ally_agreements,
--         ally_service_review_log (este último por adicional v2 — workflow
--         de aprobación de servicios propuestos por el aliado).
-- Permiso: ALLY_SERVICE_APPROVE (v2) al final, asignado a SYSTEM + ADMINISTRADOR.
--
-- Notas de diseño:
--   - allies es entidad organizacional, NO de persona — no enlaza a `persons`
--     (V15). Los usuarios que trabajan en el aliado se enganchan vía la
--     próxima V12__ally_users.sql (esos sí tienen User → Person).
--   - tax_document_type/number = RIF (J/V/E/G/P). Mismos prefijos que persons
--     pero independientes — UNIQUE parcial cuando ambos presentes.
--   - ally_services.review_status arranca v2 desde el día 1:
--       PROPOSED → IN_REVIEW → APPROVED → REMOVED
--                              ↘  ↑
--                                REJECTED (resubmittable → IN_REVIEW)
--     Sólo APPROVED visible en el directorio público. REMOVED es post-aprobación
--     (admin o aliado retiran un servicio que ya estaba publicado). review_reason
--     aplica tanto a REJECTED como a REMOVED — texto contextual al estado.
--   - ally_service_review_log es inmutable (no triggers de update; sólo INSERT).
-- ────────────────────────────────────────────────────────────────────────────

-- ─── allies ─────────────────────────────────────────────────────────────────
CREATE TABLE allies
(
    allies_id           BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    name                VARCHAR(200) NOT NULL,
    ally_type_id        BIGINT       NOT NULL REFERENCES ally_types (ally_types_id),

    -- Identidad fiscal (RIF venezolano — opcional, hay aliados informales)
    tax_document_type   VARCHAR(1)   CHECK (tax_document_type IN ('J', 'V', 'E', 'G', 'P')),
    tax_document_number VARCHAR(20),

    -- Contacto
    email               CITEXT,
    phone               VARCHAR(30),
    website             VARCHAR(255),

    -- Dirección (city resuelve estado y país vía catálogo V8)
    address             TEXT,
    city_id             BIGINT       REFERENCES cities (cities_id),

    -- Branding / portal público
    logo_url            VARCHAR(500),  -- key en R2 (StorageService)
    description         TEXT,

    -- Cuenta de usuario que gestiona este aliado (null = gestionado central por admin)
    manager_user_id     BIGINT       REFERENCES users (users_id),
    joined_at           DATE,

    -- Audit + soft-delete
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,
    status              VARCHAR(50),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,

    CONSTRAINT chk_allies_tax_document CHECK (
        (tax_document_type IS NULL AND tax_document_number IS NULL) OR
        (tax_document_type IS NOT NULL AND tax_document_number IS NOT NULL)
    )
);

-- Un RIF identifica un aliado de manera única cuando está presente
CREATE UNIQUE INDEX uniq_allies_tax_document
    ON allies (tax_document_type, tax_document_number)
    WHERE tax_document_type IS NOT NULL AND tax_document_number IS NOT NULL;

CREATE INDEX idx_allies_ally_type ON allies (ally_type_id);
CREATE INDEX idx_allies_city      ON allies (city_id) WHERE city_id IS NOT NULL;
CREATE INDEX idx_allies_manager   ON allies (manager_user_id) WHERE manager_user_id IS NOT NULL;
CREATE INDEX idx_allies_name_unaccent
    ON allies USING gin (unaccent(lower(name)) gin_trgm_ops);

CREATE TRIGGER trg_allies_updated_at
    BEFORE UPDATE ON allies
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ─── ally_specialties (pivot ally × medical_specialty) ──────────────────────
CREATE TABLE ally_specialties
(
    ally_specialties_id     BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ally_id                 BIGINT       NOT NULL REFERENCES allies (allies_id) ON DELETE CASCADE,
    medical_specialty_id    BIGINT       NOT NULL REFERENCES medical_specialties (medical_specialties_id),

    is_active               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by              UUID,

    UNIQUE (ally_id, medical_specialty_id)
);

CREATE INDEX idx_ally_specialties_specialty ON ally_specialties (medical_specialty_id);


-- ─── ally_services (con workflow de aprobación v2) ──────────────────────────
CREATE TABLE ally_services
(
    ally_services_id       BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                   UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    ally_id                BIGINT       NOT NULL REFERENCES allies (allies_id) ON DELETE CASCADE,
    service_category_id    BIGINT       NOT NULL REFERENCES service_categories (service_categories_id),

    name                   VARCHAR(200) NOT NULL,
    description            TEXT,
    price_usd              NUMERIC(10, 2),
    discount_pct           NUMERIC(5, 2) CHECK (discount_pct IS NULL OR (discount_pct BETWEEN 0 AND 100)),
    requires_appointment   BOOLEAN      NOT NULL DEFAULT FALSE,

    -- v2 workflow — el aliado propone, admin aprueba antes de publicar.
    -- REMOVED es post-APPROVED (admin o aliado retiran el servicio).
    review_status          VARCHAR(20)  NOT NULL DEFAULT 'PROPOSED'
                              CHECK (review_status IN ('PROPOSED', 'IN_REVIEW', 'APPROVED', 'REJECTED', 'REMOVED')),
    reviewed_by            BIGINT       REFERENCES users (users_id),
    reviewed_at            TIMESTAMPTZ,
    review_reason          TEXT,         -- aplica a REJECTED y REMOVED — texto contextual

    -- Audit + soft-delete
    is_active              BOOLEAN      NOT NULL DEFAULT TRUE,
    status                 VARCHAR(50),
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by             UUID,
    updated_by             UUID,

    -- Consistency: estados terminales negativos (REJECTED / REMOVED) requieren
    -- motivo y revisor que tomó la acción
    CONSTRAINT chk_ally_services_review_reason CHECK (
        review_status NOT IN ('REJECTED', 'REMOVED')
        OR (review_reason IS NOT NULL AND reviewed_by IS NOT NULL)
    )
);

CREATE INDEX idx_ally_services_ally          ON ally_services (ally_id);
CREATE INDEX idx_ally_services_category      ON ally_services (service_category_id);
CREATE INDEX idx_ally_services_review_status ON ally_services (review_status);
-- Cola de revisión rápida (PROPOSED + IN_REVIEW solamente)
CREATE INDEX idx_ally_services_pending_queue
    ON ally_services (created_at)
    WHERE review_status IN ('PROPOSED', 'IN_REVIEW');

CREATE TRIGGER trg_ally_services_updated_at
    BEFORE UPDATE ON ally_services
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ─── ally_agreements ────────────────────────────────────────────────────────
CREATE TABLE ally_agreements
(
    ally_agreements_id    BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                  UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    ally_id               BIGINT       NOT NULL REFERENCES allies (allies_id),
    agreement_type        VARCHAR(50)  NOT NULL
                              CHECK (agreement_type IN ('COMMERCIAL', 'MEDICAL', 'EXCLUSIVITY', 'SUPPLY')),
    start_date            DATE         NOT NULL,
    end_date              DATE,
    terms                 TEXT,
    signed_pdf_url        VARCHAR(500),  -- key en R2

    -- Audit + estado del contrato (no confundir con BaseEntity.is_active)
    is_active             BOOLEAN      NOT NULL DEFAULT TRUE,
    status                VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE'
                              CHECK (status IN ('DRAFT', 'ACTIVE', 'EXPIRED', 'TERMINATED')),
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID,

    CONSTRAINT chk_agreement_dates CHECK (end_date IS NULL OR end_date >= start_date)
);

CREATE INDEX idx_ally_agreements_ally_status ON ally_agreements (ally_id, status);

CREATE TRIGGER trg_ally_agreements_updated_at
    BEFORE UPDATE ON ally_agreements
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ─── ally_service_review_log (v2 — audit inmutable del workflow) ────────────
CREATE TABLE ally_service_review_log
(
    ally_service_review_log_id    BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ally_service_id               BIGINT       NOT NULL REFERENCES ally_services (ally_services_id) ON DELETE CASCADE,
    from_status                   VARCHAR(20),
    to_status                     VARCHAR(20)  NOT NULL
                                      CHECK (to_status IN ('PROPOSED', 'IN_REVIEW', 'APPROVED', 'REJECTED', 'REMOVED')),
    actor_user_id                 BIGINT       REFERENCES users (users_id),
    action_at                     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    comment                       TEXT
);

CREATE INDEX idx_ally_service_review_log_service
    ON ally_service_review_log (ally_service_id, action_at DESC);


-- ─── v2 — Permiso ALLY_SERVICE_APPROVE ──────────────────────────────────────
-- Asignado por defecto a SYSTEM + ADMINISTRADOR. El aliado nunca aprueba
-- sus propios servicios — siempre pasa por revisión administrativa.
INSERT INTO permissions (name, domain_id, description)
SELECT 'ALLY_SERVICE_APPROVE',
       pd.permission_domains_id,
       'Aprobar o rechazar servicios propuestos por aliados'
FROM permission_domains pd
WHERE pd.code = 'ALLIES';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
CROSS JOIN permissions p
WHERE r.name IN ('SYSTEM', 'ADMINISTRADOR')
  AND p.name = 'ALLY_SERVICE_APPROVE';
