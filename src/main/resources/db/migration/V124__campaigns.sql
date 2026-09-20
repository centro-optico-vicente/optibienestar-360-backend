SET search_path TO app, public;

-- ============================================================================
-- V124: campaigns core schema (hub plan ".ai/plans/2026-09-16-commission-
-- payouts-collections-plan.md" + conversation spec "Campaign entity
-- ecosystem"). Ships the tables backing the already-coded
-- modules/campaign/entity classes (Campaign, CampaignPromoter,
-- CampaignTransactionLink, CampaignTransactionException) plus the
-- campaign_id/starts_at/ends_at anchor columns on the three rule tables that
-- already reference them (CommissionTier, CommissionBonusRule,
-- HierarchyOverrideTier). CollectionCommissionTier's anchor + the XOR
-- pct/amount + description + M:N promoter-type/rank scoping columns are
-- tracked separately (not yet coded against).
-- ============================================================================


-- ─── 1. campaigns ───────────────────────────────────────────────────────────
CREATE TABLE campaigns
(
    campaigns_id       BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                UUID         NOT NULL UNIQUE,

    name                VARCHAR(150) NOT NULL,
    description         TEXT,
    starts_at           TIMESTAMPTZ  NOT NULL,
    ends_at             TIMESTAMPTZ  NOT NULL,
    enabled             BOOLEAN      NOT NULL DEFAULT TRUE,

    scope               VARCHAR(20)  NOT NULL DEFAULT 'ALL',
    mode                VARCHAR(20)  NOT NULL DEFAULT 'GENERAL',
    evaluate_only_at_end BOOLEAN     NOT NULL DEFAULT FALSE,
    pay_only_at_end     BOOLEAN      NOT NULL DEFAULT FALSE,

    target_amount       NUMERIC(14, 2),
    target_count        INTEGER,
    exclusivity_group   VARCHAR(80),
    priority            INTEGER,

    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,
    status               VARCHAR(50),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by          UUID,
    updated_by          UUID,

    CONSTRAINT chk_campaigns_dates CHECK (ends_at > starts_at),
    CONSTRAINT chk_campaigns_scope CHECK (scope IN ('ALL', 'INCLUDE', 'EXCLUDE')),
    CONSTRAINT chk_campaigns_mode CHECK (mode IN ('TARGETED', 'GENERAL')),
    -- evaluate_only_at_end forces pay_only_at_end (CampaignService also validates this on write).
    CONSTRAINT chk_campaigns_evaluate_end_implies_pay_end
        CHECK (NOT evaluate_only_at_end OR pay_only_at_end)
);

CREATE TRIGGER trg_campaigns_updated_at
    BEFORE UPDATE ON campaigns
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE INDEX idx_campaigns_active_window ON campaigns (is_active, starts_at, ends_at);
CREATE INDEX idx_campaigns_exclusivity_group ON campaigns (exclusivity_group) WHERE exclusivity_group IS NOT NULL;


-- ─── 2. campaign_promoters ──────────────────────────────────────────────────
CREATE TABLE campaign_promoters
(
    campaign_promoters_id BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                    UUID        NOT NULL UNIQUE,

    campaign_id             BIGINT      NOT NULL REFERENCES campaigns (campaigns_id),
    promoter_id              BIGINT      NOT NULL REFERENCES promoters (promoters_id),

    is_active                BOOLEAN     NOT NULL DEFAULT TRUE,
    status                    VARCHAR(50),
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by               UUID,
    updated_by               UUID,

    CONSTRAINT uq_campaign_promoters_campaign_promoter UNIQUE (campaign_id, promoter_id)
);

CREATE TRIGGER trg_campaign_promoters_updated_at
    BEFORE UPDATE ON campaign_promoters
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE INDEX idx_campaign_promoters_campaign ON campaign_promoters (campaign_id);
CREATE INDEX idx_campaign_promoters_promoter ON campaign_promoters (promoter_id);


-- ─── 3. campaign_transaction_links ──────────────────────────────────────────
CREATE TABLE campaign_transaction_links
(
    campaign_transaction_links_id BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                            UUID        NOT NULL UNIQUE,

    campaign_id                     BIGINT      NOT NULL REFERENCES campaigns (campaigns_id),
    payment_id                       BIGINT      REFERENCES payments (payments_id),
    membership_id                    BIGINT      REFERENCES memberships (memberships_id),

    source                            VARCHAR(20) NOT NULL DEFAULT 'AUTO',
    resolved_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),

    is_active                         BOOLEAN     NOT NULL DEFAULT TRUE,
    status                             VARCHAR(50),
    created_at                        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                        TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by                        UUID,
    updated_by                        UUID,

    CONSTRAINT chk_campaign_transaction_links_source
        CHECK (source IN ('AUTO', 'EXCEPTION_INCLUDE', 'EXCEPTION_EXCLUDE')),
    CONSTRAINT chk_campaign_transaction_links_one_target
        CHECK ((payment_id IS NOT NULL) <> (membership_id IS NOT NULL))
);

CREATE TRIGGER trg_campaign_transaction_links_updated_at
    BEFORE UPDATE ON campaign_transaction_links
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE INDEX idx_campaign_transaction_links_campaign ON campaign_transaction_links (campaign_id);
CREATE INDEX idx_campaign_transaction_links_payment ON campaign_transaction_links (payment_id) WHERE payment_id IS NOT NULL;
CREATE INDEX idx_campaign_transaction_links_membership ON campaign_transaction_links (membership_id) WHERE membership_id IS NOT NULL;


-- ─── 4. campaign_transaction_exceptions ─────────────────────────────────────
CREATE TABLE campaign_transaction_exceptions
(
    campaign_transaction_exceptions_id BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                                 UUID        NOT NULL UNIQUE,

    campaign_id                          BIGINT      NOT NULL REFERENCES campaigns (campaigns_id),
    payment_id                            BIGINT      REFERENCES payments (payments_id),
    membership_id                         BIGINT      REFERENCES memberships (memberships_id),

    action                                 VARCHAR(10) NOT NULL,
    reason                                  TEXT,

    is_active                              BOOLEAN     NOT NULL DEFAULT TRUE,
    status                                  VARCHAR(50),
    created_at                             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                             TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by                             UUID,
    updated_by                             UUID,

    CONSTRAINT chk_campaign_transaction_exceptions_action CHECK (action IN ('INCLUDE', 'EXCLUDE')),
    CONSTRAINT chk_campaign_transaction_exceptions_one_target
        CHECK ((payment_id IS NOT NULL) <> (membership_id IS NOT NULL))
);

CREATE TRIGGER trg_campaign_transaction_exceptions_updated_at
    BEFORE UPDATE ON campaign_transaction_exceptions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE INDEX idx_campaign_transaction_exceptions_campaign ON campaign_transaction_exceptions (campaign_id);


-- ─── 5. new granular permissions ────────────────────────────────────────────
-- Follows the existing permissions seed pattern (see V121). `name` is the
-- stable key the JWT/backoffice checks (permissions.name is UNIQUE);
-- `permission_domains` gets a new CAMPAIGN domain for the permissions UI.
INSERT INTO permission_domains (code, name, icon, description, display_order)
VALUES ('CAMPAIGN', 'Campañas', 'i-lucide-megaphone', 'Campañas de comisiones/incentivos', 85)
ON CONFLICT (code) DO NOTHING;

INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('CAMPAIGN_VIEW_ALL',        'CAMPAIGN', 'Ver todas las campañas de comisiones/incentivos'),
    ('CAMPAIGN_CREATE',          'CAMPAIGN', 'Crear campañas de comisiones/incentivos'),
    ('CAMPAIGN_UPDATE',          'CAMPAIGN', 'Editar campañas de comisiones/incentivos'),
    ('CAMPAIGN_DELETE',          'CAMPAIGN', 'Eliminar (desactivar) campañas de comisiones/incentivos'),
    ('CAMPAIGN_EXCEPTION_CREATE','CAMPAIGN', 'Adjuntar manualmente una transacción a una campaña ya expirada'),
    ('CAMPAIGN_EXCEPTION_DELETE','CAMPAIGN', 'Quitar manualmente una transacción de una campaña')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code;


-- ─── 6. campaign_id + starts_at/ends_at anchors on rule tables ─────────────
ALTER TABLE commission_tiers
    ADD COLUMN campaign_id             BIGINT REFERENCES campaigns (campaigns_id),
    ADD COLUMN starts_at               TIMESTAMPTZ,
    ADD COLUMN ends_at                 TIMESTAMPTZ,
    ADD COLUMN flat_amount_currency_id BIGINT REFERENCES currencies (currencies_id);

ALTER TABLE commission_tiers
    ADD CONSTRAINT chk_commission_tiers_flat_needs_currency
        CHECK (flat_amount IS NULL OR flat_amount_currency_id IS NOT NULL);

CREATE INDEX idx_commission_tiers_campaign ON commission_tiers (campaign_id) WHERE campaign_id IS NOT NULL;

ALTER TABLE commission_bonus_rules
    ADD COLUMN campaign_id BIGINT REFERENCES campaigns (campaigns_id),
    ADD COLUMN starts_at   TIMESTAMPTZ,
    ADD COLUMN ends_at     TIMESTAMPTZ;

CREATE INDEX idx_commission_bonus_rules_campaign ON commission_bonus_rules (campaign_id) WHERE campaign_id IS NOT NULL;

-- V37's campaign_start/campaign_end were `date`; the entity now maps them as
-- OffsetDateTime — widen in place (midnight UTC for existing rows, matching
-- the entity javadoc's documented migration behavior).
ALTER TABLE commission_bonus_rules
    ALTER COLUMN campaign_start TYPE TIMESTAMPTZ USING (campaign_start::timestamptz),
    ALTER COLUMN campaign_end TYPE TIMESTAMPTZ USING (campaign_end::timestamptz);

ALTER TABLE hierarchy_override_tiers
    ADD COLUMN campaign_id BIGINT REFERENCES campaigns (campaigns_id),
    ADD COLUMN starts_at   TIMESTAMPTZ,
    ADD COLUMN ends_at     TIMESTAMPTZ;

CREATE INDEX idx_hierarchy_override_tiers_campaign ON hierarchy_override_tiers (campaign_id) WHERE campaign_id IS NOT NULL;


-- ─── 7. campaign_id on payments/memberships (simple reporting mirror) ──────
ALTER TABLE payments
    ADD COLUMN campaign_id BIGINT REFERENCES campaigns (campaigns_id);
CREATE INDEX idx_payments_campaign ON payments (campaign_id) WHERE campaign_id IS NOT NULL;

ALTER TABLE memberships
    ADD COLUMN campaign_id BIGINT REFERENCES campaigns (campaigns_id);
CREATE INDEX idx_memberships_campaign ON memberships (campaign_id) WHERE campaign_id IS NOT NULL;
