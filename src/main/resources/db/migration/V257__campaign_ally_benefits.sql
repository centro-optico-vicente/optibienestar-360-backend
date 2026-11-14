SET search_path TO app, public;

-- ============================================================================
-- V257: ally benefits inside campaigns (hub ADR 0038 §7-§8).
--
-- campaigns:
--   ally_id                    set = campaign exclusive to that ally: every
--                              promotion line belongs to it. NULL = several
--                              allies, each ALLY_BENEFIT line names its own.
--   allows_ally_partnership    an ally (role ALIADO) may add its own
--                              ALLY_BENEFIT lines from its portal while the
--                              campaign is ACTIVE (only when ally_id is NULL
--                              or that same ally).
--
-- promotions:
--   benefit_kind               DISCOUNT (V175/V254 discount) or ALLY_BENEFIT
--                              (a voucher for an ally's offer: FREE, a
--                              percentage or a fixed amount off, N units).
--   applies_to                 only meaningful for DISCOUNT now (nullable).
--   created_by_ally            the line was proposed by the ally itself.
--
-- membership_promotions.benefit_kind: a membership holds at most one ACTIVE
-- DISCOUNT promotion and any number of ALLY_BENEFIT ones (one per promotion).
--
-- membership_benefit_grants: the voucher a CONFIRMED ally-benefit slot gives
-- the member (AVAILABLE → REDEEMED, or EXPIRED / CANCELED), redeemed by the
-- ally when it registers the usage (benefit_usages.benefit_grant_id). Its
-- cost is absorbed by the ally and only recorded.
-- ============================================================================

-- ─── 1. campaigns ──────────────────────────────────────────────────────────
ALTER TABLE campaigns
    ADD COLUMN ally_id                 BIGINT  REFERENCES allies (allies_id),
    ADD COLUMN allows_ally_partnership BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_campaigns_ally ON campaigns (ally_id) WHERE ally_id IS NOT NULL;

-- ─── 2. promotions: benefit kind + ally benefit columns ────────────────────
ALTER TABLE promotions
    ADD COLUMN benefit_kind        VARCHAR(20)    NOT NULL DEFAULT 'DISCOUNT',
    ADD COLUMN ally_id             BIGINT         REFERENCES allies (allies_id),
    ADD COLUMN created_by_ally     BOOLEAN        NOT NULL DEFAULT FALSE,
    ADD COLUMN ally_product_id     BIGINT         REFERENCES ally_products (ally_products_id),
    ADD COLUMN benefit_type        VARCHAR(20),
    ADD COLUMN benefit_pct         NUMERIC(5, 2),
    ADD COLUMN benefit_amount      NUMERIC(18, 2),
    ADD COLUMN benefit_currency_id BIGINT         REFERENCES currencies (currencies_id),
    ADD COLUMN benefit_quantity    INTEGER        NOT NULL DEFAULT 1;

ALTER TABLE promotions ALTER COLUMN applies_to DROP NOT NULL;

ALTER TABLE promotions DROP CONSTRAINT chk_promotions_discount_coherence;

ALTER TABLE promotions
    ADD CONSTRAINT chk_promotions_benefit_kind CHECK (benefit_kind IN ('DISCOUNT', 'ALLY_BENEFIT')),
    ADD CONSTRAINT chk_promotions_benefit_type
        CHECK (benefit_type IS NULL OR benefit_type IN ('FREE', 'PERCENTAGE', 'FLAT')),
    ADD CONSTRAINT chk_promotions_benefit_quantity CHECK (benefit_quantity >= 1),
    -- DISCOUNT keeps the V254 discount coherence and needs applies_to;
    -- ALLY_BENEFIT carries an ally offer and its own benefit, never a discount.
    ADD CONSTRAINT chk_promotions_benefit_coherence CHECK (
        (benefit_kind = 'DISCOUNT'
            AND applies_to IS NOT NULL
            AND ally_product_id IS NULL AND benefit_type IS NULL
            AND benefit_pct IS NULL AND benefit_amount IS NULL AND benefit_currency_id IS NULL
            AND (
                (discount_type = 'PERCENTAGE' AND discount_pct IS NOT NULL AND discount_pct > 0 AND discount_pct <= 100
                                              AND discount_amount IS NULL AND discount_currency_id IS NULL) OR
                (discount_type = 'FLAT'       AND discount_amount IS NOT NULL AND discount_amount > 0
                                              AND discount_currency_id IS NOT NULL AND discount_pct IS NULL)
            ))
        OR
        (benefit_kind = 'ALLY_BENEFIT'
            AND kind = 'ACQUISITION'
            AND ally_id IS NOT NULL AND ally_product_id IS NOT NULL AND benefit_type IS NOT NULL
            AND discount_pct IS NULL AND discount_amount IS NULL AND discount_currency_id IS NULL
            AND (
                (benefit_type = 'FREE'       AND benefit_pct IS NULL AND benefit_amount IS NULL
                                             AND benefit_currency_id IS NULL) OR
                (benefit_type = 'PERCENTAGE' AND benefit_pct IS NOT NULL AND benefit_pct > 0 AND benefit_pct <= 100
                                             AND benefit_amount IS NULL AND benefit_currency_id IS NULL) OR
                (benefit_type = 'FLAT'       AND benefit_amount IS NOT NULL AND benefit_amount > 0
                                             AND benefit_currency_id IS NOT NULL AND benefit_pct IS NULL)
            ))
    );

CREATE INDEX idx_promotions_ally ON promotions (ally_id) WHERE ally_id IS NOT NULL;

COMMENT ON COLUMN promotions.benefit_kind IS 'DISCOUNT (descuento sobre inscripción/mensualidad) o ALLY_BENEFIT (vale de un aliado)';
COMMENT ON COLUMN promotions.created_by_ally IS 'La línea la propuso el aliado desde su portal (campaña con alianza)';

-- ─── 3. membership_promotions: one discount + several ally benefits ────────
ALTER TABLE membership_promotions
    ADD COLUMN benefit_kind VARCHAR(20) NOT NULL DEFAULT 'DISCOUNT',
    ADD CONSTRAINT chk_membership_promotions_benefit_kind CHECK (benefit_kind IN ('DISCOUNT', 'ALLY_BENEFIT'));

DROP INDEX uq_membership_promotions_one_active;

CREATE UNIQUE INDEX uq_membership_promotions_one_active_discount
    ON membership_promotions (membership_id)
    WHERE status = 'ACTIVE' AND is_active AND benefit_kind = 'DISCOUNT';
CREATE UNIQUE INDEX uq_membership_promotions_active_promotion
    ON membership_promotions (membership_id, promotion_id)
    WHERE status = 'ACTIVE' AND is_active;

-- ─── 4. membership_benefit_grants (ally vouchers) ──────────────────────────
CREATE TABLE membership_benefit_grants
(
    membership_benefit_grants_id BIGINT         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                         UUID           NOT NULL UNIQUE,
    membership_promotion_id      BIGINT         NOT NULL UNIQUE
                                                REFERENCES membership_promotions (membership_promotions_id),
    membership_id                BIGINT         NOT NULL REFERENCES memberships (memberships_id),
    promotion_id                 BIGINT         NOT NULL REFERENCES promotions (promotions_id),
    ally_id                      BIGINT         NOT NULL REFERENCES allies (allies_id),
    ally_product_id              BIGINT         NOT NULL REFERENCES ally_products (ally_products_id),

    benefit_type                 VARCHAR(20)    NOT NULL,
    benefit_pct                  NUMERIC(5, 2),
    benefit_amount               NUMERIC(18, 2),
    benefit_currency_id          BIGINT         REFERENCES currencies (currencies_id),
    quantity                     INTEGER        NOT NULL,
    remaining_qty                INTEGER        NOT NULL,

    grant_status                 VARCHAR(20)    NOT NULL,
    granted_at                   TIMESTAMPTZ    NOT NULL,
    expires_at                   TIMESTAMPTZ,
    redeemed_at                  TIMESTAMPTZ,

    is_active                    BOOLEAN        NOT NULL DEFAULT TRUE,
    status                       VARCHAR(50),
    created_at                   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at                   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by                   UUID,
    updated_by                   UUID,

    CONSTRAINT chk_membership_benefit_grants_type CHECK (benefit_type IN ('FREE', 'PERCENTAGE', 'FLAT')),
    CONSTRAINT chk_membership_benefit_grants_qty CHECK (quantity >= 1 AND remaining_qty >= 0 AND remaining_qty <= quantity),
    CONSTRAINT chk_membership_benefit_grants_status
        CHECK (grant_status IN ('AVAILABLE', 'REDEEMED', 'EXPIRED', 'CANCELED')),
    CONSTRAINT chk_membership_benefit_grants_redeemed
        CHECK (grant_status <> 'REDEEMED' OR (redeemed_at IS NOT NULL AND remaining_qty = 0))
);

CREATE TRIGGER trg_membership_benefit_grants_updated_at
    BEFORE UPDATE ON membership_benefit_grants
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE INDEX idx_membership_benefit_grants_membership_status ON membership_benefit_grants (membership_id, grant_status);
CREATE INDEX idx_membership_benefit_grants_ally_status ON membership_benefit_grants (ally_id, grant_status);
CREATE INDEX idx_membership_benefit_grants_promotion ON membership_benefit_grants (promotion_id);

COMMENT ON TABLE membership_benefit_grants IS 'Vale de un aliado que recibe el afiliado al confirmarse su cupo en una campaña';

-- ─── 5. benefit_usages: the voucher a usage redeemed ───────────────────────
ALTER TABLE benefit_usages
    ADD COLUMN benefit_grant_id BIGINT REFERENCES membership_benefit_grants (membership_benefit_grants_id);

CREATE INDEX idx_benefit_usages_benefit_grant ON benefit_usages (benefit_grant_id) WHERE benefit_grant_id IS NOT NULL;

-- ─── 6. permissions (ally portal partnership) ──────────────────────────────
INSERT INTO permissions (name, domain_id, description)
SELECT v.name, d.permission_domains_id, v.description
FROM (VALUES
    ('ALLY_CAMPAIGN_VIEW',             'Ver las campañas abiertas a alianza desde el portal del aliado'),
    ('ALLY_CAMPAIGN_PROMOTION_CREATE', 'Agregar beneficios propios a una campaña abierta a alianza'),
    ('ALLY_CAMPAIGN_PROMOTION_UPDATE', 'Editar los beneficios propios en una campaña'),
    ('ALLY_CAMPAIGN_PROMOTION_DELETE', 'Eliminar los beneficios propios de una campaña')
) AS v(name, description)
CROSS JOIN permission_domains d
WHERE d.code = 'ALLIES'
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
CROSS JOIN permissions p
WHERE r.name IN ('SYSTEM', 'ALIADO')
  AND p.name IN ('ALLY_CAMPAIGN_VIEW', 'ALLY_CAMPAIGN_PROMOTION_CREATE',
                 'ALLY_CAMPAIGN_PROMOTION_UPDATE', 'ALLY_CAMPAIGN_PROMOTION_DELETE')
ON CONFLICT (role_id, permission_id) DO NOTHING;

UPDATE scheduled_jobs
   SET description = 'Cierra las campañas cuya fecha de fin ya pasó, vence los cupos pendientes sin confirmar (liberando cupo y reactivando campañas agotadas), termina las promociones cuya ventana de uso acabó y vence los vales de aliados sin canjear.'
 WHERE code = 'CAMPAIGN_LIFECYCLE';

DO $$
BEGIN
    IF (SELECT COUNT(*) FROM permissions WHERE name LIKE 'ALLY\_CAMPAIGN\_%') < 4 THEN
        RAISE EXCEPTION 'V257: ALLY_CAMPAIGN_* permissions were not created';
    END IF;
END $$;
