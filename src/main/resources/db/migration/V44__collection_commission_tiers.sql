SET search_path TO app, public;

-- V44: Collection commission — decreasing % by days-to-collect (ADR 0013 §3).
--
-- New orthogonal axis from commission_tiers (which scopes by monthly new-
-- subscriber volume): collection_commission_tiers scopes by how many days it
-- took to collect the recurring payment. Kept as its own table rather than
-- columns on commission_tiers to avoid mixing the two axes.


-- ─── 1. collection_commission_tiers ──────────────────────────────────────────
CREATE TABLE collection_commission_tiers
(
    collection_commission_tiers_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                            UUID         NOT NULL UNIQUE,

    name                            VARCHAR(80)  NOT NULL,

    -- Bucket ceiling: the tier applies when days-to-collect <= max_days. The
    -- engine picks the smallest qualifying max_days (ADR 0013 §3).
    max_days                        INT          NOT NULL
        CONSTRAINT chk_collection_commission_tiers_max_days CHECK (max_days > 0),

    commission_pct                  NUMERIC(5, 2) NOT NULL
        CONSTRAINT chk_collection_commission_tiers_pct CHECK (commission_pct > 0 AND commission_pct <= 100),

    is_active                       BOOLEAN      NOT NULL DEFAULT TRUE,
    status                          VARCHAR(50),
    created_at                      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by                      UUID,
    updated_by                      UUID
);

CREATE TRIGGER trg_collection_commission_tiers_updated_at
    BEFORE UPDATE ON collection_commission_tiers
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Engine lookup: candidate buckets for a given days-to-collect, smallest max_days first.
CREATE INDEX idx_collection_commission_tiers_lookup
    ON collection_commission_tiers (max_days ASC) WHERE is_active;

-- Seed the business buckets (ADR 0013 §3).
INSERT INTO collection_commission_tiers (uuid, name, max_days, commission_pct)
VALUES
    (gen_random_uuid(), 'Cobranza hasta 5 días',   5,    35.00),
    (gen_random_uuid(), 'Cobranza hasta 10 días',  10,   30.00),
    (gen_random_uuid(), 'Cobranza hasta 15 días',  15,   25.00),
    (gen_random_uuid(), 'Cobranza hasta 20 días',  20,   20.00),
    (gen_random_uuid(), 'Cobranza hasta 25 días',  25,   15.00),
    (gen_random_uuid(), 'Cobranza más de 25 días', 9999, 10.00);


-- ─── 2. Permission (COMMISSIONS domain) ──────────────────────────────────────
-- The V30 trigger auto-grants each to SYSTEM on insert.
INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('COLLECTION_COMMISSION_TIER_MANAGE', 'COMMISSIONS', 'Configurar los tramos de comisión de cobranza por días')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name IN ('SYSTEM', 'ADMINISTRADOR')
  AND p.name IN ('COLLECTION_COMMISSION_TIER_MANAGE')
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- ─── 3. Fail loudly rather than migrate into a half-applied state ────────────
DO $$
BEGIN
    IF (SELECT COUNT(*) FROM collection_commission_tiers) < 6 THEN
        RAISE EXCEPTION 'V44: collection_commission_tiers seed did not land';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM permissions p WHERE p.name = 'COLLECTION_COMMISSION_TIER_MANAGE') THEN
        RAISE EXCEPTION 'V44: COLLECTION_COMMISSION_TIER_MANAGE permission was not created';
    END IF;

    IF EXISTS (
        SELECT 1 FROM permissions p
        WHERE p.name = 'COLLECTION_COMMISSION_TIER_MANAGE'
          AND NOT EXISTS (
            SELECT 1 FROM role_permissions rp
                     JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'SYSTEM'
            WHERE rp.permission_id = p.permissions_id)
    ) THEN
        RAISE EXCEPTION 'V44: SYSTEM did not receive COLLECTION_COMMISSION_TIER_MANAGE (V30 trigger?)';
    END IF;
END $$;
