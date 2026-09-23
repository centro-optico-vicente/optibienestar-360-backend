SET search_path TO app, public;

-- ============================================================
-- V139: terms_and_conditions — versioned T&C per role (AFILIADO /
-- PROMOTOR / ALIADO) with per-user acceptance tracking.
--
-- terms_versions follows the same "vigency by timestamp" pattern as
-- exchange_rates (V85): full history, never overwritten, the
-- "current" row for a term_type is the one with the greatest
-- valid_from <= now(). No valid_to — same reasoning as exchange_rates,
-- the next inserted row's valid_from implicitly closes the previous
-- one's window.
--
-- terms_acceptances is a simple per-user, per-version acceptance log
-- (idempotent via the UNIQUE constraint) — this is also the seed for
-- a future notifications system (who accepted what, when), without
-- committing to that design now.
-- ============================================================

CREATE TABLE terms_versions
(
    terms_versions_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid               UUID        NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    term_type          VARCHAR(20) NOT NULL
        CONSTRAINT chk_terms_versions_term_type CHECK (term_type IN ('AFILIADO', 'PROMOTOR', 'ALIADO')),
    title               VARCHAR(200) NOT NULL,
    content_markdown    TEXT         NOT NULL,

    is_public           BOOLEAN      NOT NULL DEFAULT FALSE,
    valid_from           TIMESTAMPTZ NOT NULL,

    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,
    status               VARCHAR(50),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by           UUID,
    updated_by           UUID
);

-- "Current version for a type" lookup: latest valid_from <= :at.
CREATE INDEX idx_terms_versions_type_valid_from
    ON terms_versions (term_type, valid_from DESC);

CREATE TRIGGER trg_terms_versions_updated_at
    BEFORE UPDATE ON terms_versions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE terms_acceptances
(
    terms_acceptances_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                   UUID        NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    user_id                BIGINT      NOT NULL REFERENCES users (users_id),
    terms_versions_id       BIGINT     NOT NULL REFERENCES terms_versions (terms_versions_id),
    accepted_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    is_active              BOOLEAN     NOT NULL DEFAULT TRUE,
    status                  VARCHAR(50),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              UUID,
    updated_by              UUID,

    CONSTRAINT uq_terms_acceptances_user_version UNIQUE (user_id, terms_versions_id)
);

CREATE INDEX idx_terms_acceptances_user ON terms_acceptances (user_id);

CREATE TRIGGER trg_terms_acceptances_updated_at
    BEFORE UPDATE ON terms_acceptances
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ─── entity_config: enables standard data-change audit for both tables ──────
INSERT INTO entity_config (entity_key, display_name, table_name) VALUES
    ('terms_version',    'Términos y condiciones',       'terms_versions'),
    ('terms_acceptance', 'Aceptación de términos',        'terms_acceptances');

-- ─── Permissions ─────────────────────────────────────────────────────────────
INSERT INTO permission_domains (code, name, icon, description, display_order)
VALUES ('TERMS', 'Términos y Condiciones', 'i-lucide-file-text',
        'Gestión de términos y condiciones por rol', 122)
ON CONFLICT (code) DO NOTHING;

INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('TERMS_VIEW_ALL',          'TERMS', 'Ver términos y condiciones'),
    ('TERMS_CREATE',            'TERMS', 'Publicar o programar una nueva versión de términos'),
    ('TERMS_UPDATE',            'TERMS', 'Editar una versión de términos aún no vigente'),
    ('TERMS_RECORD_AUDIT_VIEW', 'TERMS', 'Ver auditoría de cambios de términos y condiciones')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code;

-- SYSTEM / ADMINISTRADOR: all four new permissions.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name IN ('SYSTEM', 'ADMINISTRADOR')
  AND p.name IN ('TERMS_VIEW_ALL', 'TERMS_CREATE', 'TERMS_UPDATE', 'TERMS_RECORD_AUDIT_VIEW')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ─── Fail loudly rather than migrate into a half-applied state ──────────────
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM permission_domains WHERE code = 'TERMS') THEN
        RAISE EXCEPTION 'V139: TERMS permission domain was not created';
    END IF;

    IF EXISTS (
        SELECT 1 FROM (VALUES
            ('TERMS_VIEW_ALL'), ('TERMS_CREATE'), ('TERMS_UPDATE'), ('TERMS_RECORD_AUDIT_VIEW')
        ) AS want(name)
        WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.name = want.name)
    ) THEN
        RAISE EXCEPTION 'V139: one or more new permissions were not created';
    END IF;

    IF EXISTS (
        SELECT 1 FROM permissions p
        WHERE p.name IN ('TERMS_VIEW_ALL', 'TERMS_CREATE', 'TERMS_UPDATE', 'TERMS_RECORD_AUDIT_VIEW')
          AND NOT EXISTS (
            SELECT 1 FROM role_permissions rp
                     JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'SYSTEM'
            WHERE rp.permission_id = p.permissions_id)
    ) THEN
        RAISE EXCEPTION 'V139: SYSTEM did not receive the new permissions';
    END IF;

    IF EXISTS (
        SELECT 1 FROM permissions p
        WHERE p.name IN ('TERMS_VIEW_ALL', 'TERMS_CREATE', 'TERMS_UPDATE', 'TERMS_RECORD_AUDIT_VIEW')
          AND NOT EXISTS (
            SELECT 1 FROM role_permissions rp
                     JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'ADMINISTRADOR'
            WHERE rp.permission_id = p.permissions_id)
    ) THEN
        RAISE EXCEPTION 'V139: ADMINISTRADOR did not receive the new permissions';
    END IF;
END $$;
