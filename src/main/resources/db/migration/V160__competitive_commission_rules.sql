SET search_path TO app, public;

-- ============================================================================
-- V160: Fase 1 (hub plan competitive-commission-rules) — the base schema for
-- competitive commission rules (FIRST_TO_REACH / RANKING by position), a new
-- model that sits alongside the 4 threshold-based rule tables without
-- touching them (D1). "Top N" and "escalonado" are just several position
-- ranges on the same rule (D2) — max_winners is derived from
-- max(position_to), never persisted (D3).
--
-- The 4 frequency axes follow the same naming/contract as V146-V149
-- (Fase A), plus END_DATE ("pay at the rule's own ends_at") on all 4 axes —
-- new code, no legacy behavior to preserve, unlike the 4 pre-existing rule
-- tables (Fase A deliberately left END_DATE out of those). D15's retroactive
-- rule and D14's RANKING-close-only matrix are enforced in
-- CompetitiveCommissionRulesService (core/util/SettlementAxes), not in SQL —
-- see the comment on chk_ccr_dates_order below for why the campaign-window
-- check can't be either.
--
-- Awards, settlements, ties and manual decisions (D7, D14, D16) are Fase 2 —
-- this migration is schema + validation only, no evaluation engine yet.
-- ============================================================================

CREATE TABLE competitive_commission_rules
(
    competitive_commission_rules_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                             UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    name                             VARCHAR(150) NOT NULL,
    description                      TEXT,

    metric                          VARCHAR(40)  NOT NULL CHECK (metric IN (
                                       'NEW_SUBSCRIBERS', 'ACTIVE_SUBSCRIBERS', 'SALES_COUNT', 'SALES_AMOUNT',
                                       'COLLECTION_COUNT', 'COLLECTION_AMOUNT', 'ADVANCE_COUNT', 'ADVANCE_AMOUNT',
                                       'COMMISSION_EARNED')),   -- Fase 5 adds OVERDUE_SETTLED_*
    competition_type                VARCHAR(20)  NOT NULL CHECK (competition_type IN ('FIRST_TO_REACH', 'RANKING')),

    threshold_count                 INT            CHECK (threshold_count > 0),
    threshold_amount                NUMERIC(14, 2) CHECK (threshold_amount > 0),
    threshold_currency_id           BIGINT REFERENCES currencies (currencies_id),

    achievement_date_basis          VARCHAR(20)  NOT NULL DEFAULT 'APPROVED_AT'
                                       CHECK (achievement_date_basis IN ('PAYMENT_DATE', 'REGISTERED_AT', 'APPROVED_AT')),
    tie_policy                      VARCHAR(20)  NOT NULL DEFAULT 'MANUAL'
                                       CHECK (tie_policy IN ('STRICT', 'SHARED_FULL', 'SHARED_SPLIT', 'MANUAL')),

    -- D16: rules sharing a "type of bonus" — a promoter winning the higher-priority
    -- rule is excluded from the lower-priority ones in the same group/period.
    competition_group               VARCHAR(60),
    group_priority                  SMALLINT CHECK (group_priority >= 1),

    -- 4 frequency axes (D8/D14/D15). Unlike the 4 legacy rule tables, END_DATE
    -- is valid on ALL of them here (accrual included) — brand-new code.
    accrual_period_strategy         VARCHAR(20)  NOT NULL CHECK (accrual_period_strategy IN
                                       ('DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY', 'QUARTERLY', 'SEMIANNUAL', 'ANNUAL', 'END_DATE')),
    partial_settlement_period_strategy     VARCHAR(20) NOT NULL,
    final_settlement_period_strategy       VARCHAR(20) NOT NULL,
    retroactive_settlement_period_strategy VARCHAR(20) NOT NULL,   -- normalized when disabled (D15) or forced (D14, RANKING)

    accrual_period_anchor               SMALLINT CHECK (accrual_period_anchor BETWEEN 1 AND 31),
    partial_settlement_period_anchor     SMALLINT CHECK (partial_settlement_period_anchor BETWEEN 1 AND 31),
    final_settlement_period_anchor       SMALLINT CHECK (final_settlement_period_anchor BETWEEN 1 AND 31),
    retroactive_settlement_period_anchor SMALLINT CHECK (retroactive_settlement_period_anchor BETWEEN 1 AND 31),

    confirmation_delay_days         SMALLINT     NOT NULL DEFAULT 0 CHECK (confirmation_delay_days BETWEEN 0 AND 60),

    campaign_id                     BIGINT REFERENCES campaigns (campaigns_id),
    starts_at                       TIMESTAMPTZ,
    ends_at                         TIMESTAMPTZ,

    include_system_promoters        BOOLEAN      NOT NULL DEFAULT false,

    is_active   BOOLEAN     NOT NULL DEFAULT true,
    status      VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID,

    CONSTRAINT chk_ccr_threshold_xor CHECK (NOT (threshold_count IS NOT NULL AND threshold_amount IS NOT NULL)),
    CONSTRAINT chk_ccr_amount_currency CHECK (threshold_amount IS NULL OR threshold_currency_id IS NOT NULL),
    CONSTRAINT chk_ccr_ftr_threshold CHECK (competition_type <> 'FIRST_TO_REACH'
                                             OR threshold_count IS NOT NULL OR threshold_amount IS NOT NULL),
    CONSTRAINT chk_ccr_end_date_accrual CHECK (accrual_period_strategy <> 'END_DATE'
                                                OR (starts_at IS NOT NULL AND ends_at IS NOT NULL)),
    CONSTRAINT chk_ccr_settlement_axes CHECK (
        partial_settlement_period_strategy IN
        ('DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY', 'QUARTERLY', 'SEMIANNUAL', 'ANNUAL', 'END_DATE')
            AND final_settlement_period_strategy IN
                ('DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY', 'QUARTERLY', 'SEMIANNUAL', 'ANNUAL', 'END_DATE')
            AND retroactive_settlement_period_strategy IN
                ('DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY', 'QUARTERLY', 'SEMIANNUAL', 'ANNUAL', 'END_DATE')),
    -- END_DATE on a settlement axis needs an end date (the service copies the campaign's onto the rule)
    CONSTRAINT chk_ccr_end_date_settlement CHECK (ends_at IS NOT NULL OR 'END_DATE' NOT IN
        (partial_settlement_period_strategy, final_settlement_period_strategy, retroactive_settlement_period_strategy)),
    -- Axis ordering (D15) and the D14 RANKING-close-only matrix are validated
    -- in the service (SettlementAxes) — they aren't expressible as a plain CHECK.
    CONSTRAINT chk_ccr_dates_order CHECK (starts_at IS NULL OR ends_at IS NULL OR ends_at > starts_at)
);

CREATE INDEX idx_ccr_campaign ON competitive_commission_rules (campaign_id) WHERE campaign_id IS NOT NULL;
CREATE INDEX idx_ccr_active_eval ON competitive_commission_rules (accrual_period_strategy) WHERE is_active;
-- Rules sharing a group must also share metric/type/accrual axis/anchor/window — validated in the
-- service (D16), a cross-row invariant no CHECK can express, but the priority slot itself can be.
CREATE UNIQUE INDEX uq_ccr_group_priority ON competitive_commission_rules (competition_group, group_priority)
    WHERE is_active AND competition_group IS NOT NULL;

CREATE TRIGGER trg_competitive_commission_rules_updated_at
    BEFORE UPDATE ON competitive_commission_rules
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


CREATE TABLE competitive_commission_rule_positions
(
    competitive_commission_rule_positions_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                            UUID        NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    competitive_commission_rule_id BIGINT NOT NULL
        REFERENCES competitive_commission_rules ON DELETE CASCADE,

    position_from   INT         NOT NULL CHECK (position_from >= 1),
    position_to     INT         NOT NULL,
    label           VARCHAR(80),

    reward_type     VARCHAR(20) NOT NULL CHECK (reward_type IN ('FLAT', 'PERCENTAGE')),
    flat_amount     NUMERIC(10, 2) CHECK (flat_amount > 0),
    reward_pct      NUMERIC(5, 2)  CHECK (reward_pct > 0 AND reward_pct <= 100),
    reward_currency_id BIGINT NOT NULL REFERENCES currencies (currencies_id),
    reward_min_amount  NUMERIC(10, 2) CHECK (reward_min_amount >= 0),   -- PERCENTAGE only
    reward_max_amount  NUMERIC(10, 2) CHECK (reward_max_amount > 0),    -- PERCENTAGE only

    min_threshold_count  INT            CHECK (min_threshold_count > 0),    -- RANKING only (D12)
    min_threshold_amount NUMERIC(14, 2) CHECK (min_threshold_amount > 0),   -- currency = rule.threshold_currency_id

    is_active   BOOLEAN     NOT NULL DEFAULT true,
    status      VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID,

    CONSTRAINT chk_ccrp_range CHECK (position_to >= position_from),
    CONSTRAINT chk_ccrp_reward CHECK ((reward_type = 'FLAT' AND flat_amount IS NOT NULL AND reward_pct IS NULL)
        OR (reward_type = 'PERCENTAGE' AND reward_pct IS NOT NULL AND flat_amount IS NULL)),
    CONSTRAINT chk_ccrp_minmax CHECK (reward_min_amount IS NULL OR reward_max_amount IS NULL
        OR reward_max_amount >= reward_min_amount)
);

CREATE UNIQUE INDEX uq_ccrp_rule_from ON competitive_commission_rule_positions (competitive_commission_rule_id, position_from);
-- Non-overlap of ranges is validated in the service (an EXCLUDE constraint would need btree_gist).

CREATE TRIGGER trg_competitive_commission_rule_positions_updated_at
    BEFORE UPDATE ON competitive_commission_rule_positions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- Scope pivots (D13), empty = applies to every promoter type / rank — same
-- pattern as commission_tier_promoter_types (V137). This is the first M:N
-- by rank in the codebase (HierarchyOverrideTier.rank stays a single FK: it's
-- an identity field, not an optional scope — hub plan 2026-09-22 F-BE note).
CREATE TABLE competitive_commission_rule_promoter_types
(
    competitive_commission_rule_id BIGINT NOT NULL REFERENCES competitive_commission_rules ON DELETE CASCADE,
    promoter_type_id               BIGINT NOT NULL REFERENCES promoter_types (promoter_types_id),
    PRIMARY KEY (competitive_commission_rule_id, promoter_type_id)
);

CREATE TABLE competitive_commission_rule_ranks
(
    competitive_commission_rule_id BIGINT NOT NULL REFERENCES competitive_commission_rules ON DELETE CASCADE,
    rank_id                        BIGINT NOT NULL REFERENCES promoter_ranks (promoter_ranks_id),
    PRIMARY KEY (competitive_commission_rule_id, rank_id)
);


-- ─── Audit config ───────────────────────────────────────────────────────────
INSERT INTO entity_config (entity_key, display_name, table_name)
VALUES ('competitive_commission_rule', 'Regla de comisión competitiva', 'competitive_commission_rules')
ON CONFLICT (entity_key) DO NOTHING;


-- ─── Fail loudly rather than migrate into a half-applied state ─────────────
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables
                   WHERE table_schema = 'app' AND table_name = 'competitive_commission_rules') THEN
        RAISE EXCEPTION 'V160: competitive_commission_rules was not created';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables
                   WHERE table_schema = 'app' AND table_name = 'competitive_commission_rule_positions') THEN
        RAISE EXCEPTION 'V160: competitive_commission_rule_positions was not created';
    END IF;
END $$;
