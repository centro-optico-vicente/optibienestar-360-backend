SET search_path TO app, public;

-- ============================================================================
-- V175: promotions (hub ADR 0018, plan 2026-10-03-promotions).
--
-- A promotion is a % discount defined inside a campaign and applied to a
-- membership (at enrollment or later). ACQUISITION promotions are for new or
-- up-to-date memberships and are lost on any delay; RECOVERY promotions only
-- apply to delayed memberships (discount on their pending charges) and survive
-- the delay. One ACTIVE promotion per membership; it never stacks with a
-- subsidy (the higher % wins per charge).
--
-- Codes: a promotion may require a code owned by a promoter
-- (promoters.referral_code), a referring member (members.referral_code) or an
-- ally (new allies.referral_code). The code user gets the promotion; a member
-- issuer may get a separate, optional reward (granted as a subsidy).
-- ============================================================================

-- ─── 1. promotions ──────────────────────────────────────────────────────────
CREATE TABLE promotions
(
    promotions_id               BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                        UUID          NOT NULL UNIQUE,
    campaign_id                 BIGINT        NOT NULL REFERENCES campaigns (campaigns_id),

    name                        VARCHAR(150)  NOT NULL,
    description                 TEXT,
    kind                        VARCHAR(20)   NOT NULL,
    discount_pct                NUMERIC(5, 2) NOT NULL,
    applies_to                  VARCHAR(20)   NOT NULL,
    cycles                      INTEGER,
    covers_extra_beneficiaries  BOOLEAN       NOT NULL DEFAULT FALSE,
    max_redemptions             INTEGER,
    redemptions_count           INTEGER       NOT NULL DEFAULT 0,

    requires_code               BOOLEAN       NOT NULL DEFAULT FALSE,
    accepts_promoter_code       BOOLEAN       NOT NULL DEFAULT FALSE,
    accepts_member_code         BOOLEAN       NOT NULL DEFAULT FALSE,
    accepts_ally_code           BOOLEAN       NOT NULL DEFAULT FALSE,

    referrer_reward_pct         NUMERIC(5, 2),
    referrer_reward_cycles      INTEGER,

    is_active                   BOOLEAN       NOT NULL DEFAULT TRUE,
    status                      VARCHAR(50),
    created_at                  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by                  UUID,
    updated_by                  UUID,

    CONSTRAINT chk_promotions_kind CHECK (kind IN ('ACQUISITION', 'RECOVERY')),
    CONSTRAINT chk_promotions_applies_to CHECK (applies_to IN ('INSCRIPTION', 'MONTHLY', 'BOTH')),
    -- Recovery only ever discounts the delayed monthly charges.
    CONSTRAINT chk_promotions_recovery_monthly CHECK (kind <> 'RECOVERY' OR applies_to = 'MONTHLY'),
    CONSTRAINT chk_promotions_discount_pct CHECK (discount_pct > 0 AND discount_pct <= 100),
    CONSTRAINT chk_promotions_cycles CHECK (cycles IS NULL OR cycles >= 1),
    CONSTRAINT chk_promotions_max_redemptions CHECK (max_redemptions IS NULL OR max_redemptions >= 1),
    CONSTRAINT chk_promotions_redemptions_count CHECK (redemptions_count >= 0),
    CONSTRAINT chk_promotions_code_owner
        CHECK (NOT requires_code OR accepts_promoter_code OR accepts_member_code OR accepts_ally_code),
    CONSTRAINT chk_promotions_referrer_reward CHECK (
        (referrer_reward_pct IS NULL AND referrer_reward_cycles IS NULL)
        OR (referrer_reward_pct > 0 AND referrer_reward_pct <= 100 AND referrer_reward_cycles >= 1
            AND accepts_member_code))
);

CREATE TRIGGER trg_promotions_updated_at
    BEFORE UPDATE ON promotions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE INDEX idx_promotions_campaign ON promotions (campaign_id) WHERE is_active;

-- ─── 2. promotion_plans (eligible plans; none = every plan) ────────────────
CREATE TABLE promotion_plans
(
    promotion_id BIGINT NOT NULL REFERENCES promotions (promotions_id) ON DELETE CASCADE,
    plan_id      BIGINT NOT NULL REFERENCES plans (plans_id),
    PRIMARY KEY (promotion_id, plan_id)
);

-- ─── 3. membership_promotions (a promotion applied to a membership) ────────
CREATE TABLE membership_promotions
(
    membership_promotions_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                     UUID         NOT NULL UNIQUE,
    membership_id            BIGINT       NOT NULL REFERENCES memberships (memberships_id),
    promotion_id             BIGINT       NOT NULL REFERENCES promotions (promotions_id),

    origin                   VARCHAR(20)  NOT NULL,
    code_used                VARCHAR(20),
    code_owner_promoter_id   BIGINT       REFERENCES promoters (promoters_id),
    code_owner_member_id     BIGINT       REFERENCES members (members_id),
    code_owner_ally_id       BIGINT       REFERENCES allies (allies_id),

    cycles_remaining         INTEGER,
    inscription_applied      BOOLEAN      NOT NULL DEFAULT FALSE,
    referrer_reward_granted  BOOLEAN      NOT NULL DEFAULT FALSE,

    assigned_by_user_id      BIGINT       REFERENCES users (users_id),
    assigned_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    ended_at                 TIMESTAMPTZ,
    ended_reason             TEXT,

    is_active                BOOLEAN      NOT NULL DEFAULT TRUE,
    status                   VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by               UUID,
    updated_by               UUID,

    CONSTRAINT chk_membership_promotions_origin CHECK (origin IN ('ENROLLMENT', 'ADMIN')),
    CONSTRAINT chk_membership_promotions_status CHECK (status IN ('ACTIVE', 'CONSUMED', 'CANCELED')),
    CONSTRAINT chk_membership_promotions_cycles CHECK (cycles_remaining IS NULL OR cycles_remaining >= 0),
    CONSTRAINT chk_membership_promotions_single_owner
        CHECK (num_nonnulls(code_owner_promoter_id, code_owner_member_id, code_owner_ally_id) <= 1),
    CONSTRAINT chk_membership_promotions_ended
        CHECK (status = 'ACTIVE' OR ended_at IS NOT NULL)
);

CREATE TRIGGER trg_membership_promotions_updated_at
    BEFORE UPDATE ON membership_promotions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE UNIQUE INDEX uq_membership_promotions_one_active
    ON membership_promotions (membership_id) WHERE status = 'ACTIVE' AND is_active;
CREATE INDEX idx_membership_promotions_promotion ON membership_promotions (promotion_id);

-- ─── 4. membership_charges: gross / discount / net ─────────────────────────
ALTER TABLE membership_charges
    ADD COLUMN gross_amount            NUMERIC(10, 2),
    ADD COLUMN discount_amount         NUMERIC(10, 2) NOT NULL DEFAULT 0,
    ADD COLUMN discount_source         VARCHAR(20),
    ADD COLUMN membership_promotion_id BIGINT REFERENCES membership_promotions (membership_promotions_id);

UPDATE membership_charges SET gross_amount = amount WHERE gross_amount IS NULL;

ALTER TABLE membership_charges
    ALTER COLUMN gross_amount SET NOT NULL,
    ADD CONSTRAINT chk_membership_charges_discount_source
        CHECK (discount_source IS NULL OR discount_source IN ('PROMOTION', 'SUBSIDY')),
    ADD CONSTRAINT chk_membership_charges_net
        CHECK (discount_amount >= 0 AND discount_amount <= gross_amount AND amount = gross_amount - discount_amount);

-- ─── 4b. payments: a promotion's inscription discount is system-applied ────
-- The V41 coherence rule required a human discounted_by; an automatic
-- promotion discount (e.g. an extra-beneficiary inscription billed with no
-- reviewer) has none. Reason and timestamp stay mandatory.
ALTER TABLE payments DROP CONSTRAINT chk_payments_discount_coherence;
ALTER TABLE payments
    ADD CONSTRAINT chk_payments_discount_coherence
        CHECK (discount_amount IS NULL OR (discount_reason IS NOT NULL AND discounted_at IS NOT NULL));

-- ─── 5. allies.referral_code ───────────────────────────────────────────────
ALTER TABLE allies ADD COLUMN referral_code VARCHAR(20);
CREATE UNIQUE INDEX uq_allies_referral_code ON allies (referral_code) WHERE referral_code IS NOT NULL;

-- ─── 6. permissions (CAMPAIGN domain) ──────────────────────────────────────
INSERT INTO permissions (name, domain_id, description)
SELECT v.name, d.permission_domains_id, v.description
FROM (VALUES
    ('PROMOTION_VIEW_ALL', 'Ver las promociones de las campañas'),
    ('PROMOTION_CREATE',   'Crear promociones en una campaña'),
    ('PROMOTION_UPDATE',   'Editar promociones'),
    ('PROMOTION_DELETE',   'Eliminar promociones'),
    ('PROMOTION_ASSIGN',   'Aplicar o cancelar una promoción en una membresía')
) AS v(name, description)
CROSS JOIN permission_domains d
WHERE d.code = 'CAMPAIGN'
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
CROSS JOIN permissions p
WHERE r.name IN ('SYSTEM', 'ADMINISTRADOR', 'OPERADOR')
  AND p.name IN ('PROMOTION_VIEW_ALL', 'PROMOTION_CREATE', 'PROMOTION_UPDATE', 'PROMOTION_DELETE', 'PROMOTION_ASSIGN')
ON CONFLICT (role_id, permission_id) DO NOTHING;

DO $$
BEGIN
    IF (SELECT COUNT(*) FROM permissions WHERE name LIKE 'PROMOTION\_%') < 5 THEN
        RAISE EXCEPTION 'V175: PROMOTION_* permissions were not created';
    END IF;
    IF EXISTS (SELECT 1 FROM membership_charges WHERE gross_amount IS NULL) THEN
        RAISE EXCEPTION 'V175: membership_charges.gross_amount backfill incomplete';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'app' AND table_name = 'allies' AND column_name = 'referral_code'
    ) THEN
        RAISE EXCEPTION 'V175: allies.referral_code was not created';
    END IF;
END $$;
