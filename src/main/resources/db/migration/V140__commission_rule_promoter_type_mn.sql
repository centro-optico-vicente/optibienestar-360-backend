-- Part F (hub plan 2026-09-22): scope commission_tiers / commission_bonus_rules /
-- collection_commission_tiers to MULTIPLE promoter types instead of a single
-- optional promoter_type_id FK. Empty scope keeps meaning "applies to all"
-- (no rows in the pivot == no scoping), same semantics as the single-FK
-- null before this migration. Pattern mirrors ally_specialties (V11).
--
-- NOTE: hierarchy_override_tiers is intentionally left untouched — its
-- rank_id is NOT NULL and is the tier's primary bucketing key (one
-- independent band ladder per rank), not an optional scope like the other
-- three entities' promoter_type_id. Converting it to M:N would change its
-- core identity semantics, not just widen an optional filter, so it is
-- out of scope for this migration (see hub plan Part F implementation notes).

-- ─── commission_tier_promoter_types ─────────────────────────────────────────
CREATE TABLE commission_tier_promoter_types
(
    commission_tier_promoter_types_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    commission_tier_id                BIGINT      NOT NULL REFERENCES commission_tiers (commission_tiers_id) ON DELETE CASCADE,
    promoter_type_id                  BIGINT      NOT NULL REFERENCES promoter_types (promoter_types_id),

    created_at                        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by                        UUID,

    UNIQUE (commission_tier_id, promoter_type_id)
);

CREATE INDEX idx_commission_tier_promoter_types_type ON commission_tier_promoter_types (promoter_type_id);

INSERT INTO commission_tier_promoter_types (commission_tier_id, promoter_type_id)
SELECT commission_tiers_id, promoter_type_id
FROM commission_tiers
WHERE promoter_type_id IS NOT NULL;

ALTER TABLE commission_tiers DROP COLUMN promoter_type_id;

-- ─── commission_bonus_rule_promoter_types ───────────────────────────────────
CREATE TABLE commission_bonus_rule_promoter_types
(
    commission_bonus_rule_promoter_types_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    commission_bonus_rule_id                BIGINT      NOT NULL REFERENCES commission_bonus_rules (commission_bonus_rules_id) ON DELETE CASCADE,
    promoter_type_id                        BIGINT      NOT NULL REFERENCES promoter_types (promoter_types_id),

    created_at                              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by                              UUID,

    UNIQUE (commission_bonus_rule_id, promoter_type_id)
);

CREATE INDEX idx_commission_bonus_rule_promoter_types_type ON commission_bonus_rule_promoter_types (promoter_type_id);

INSERT INTO commission_bonus_rule_promoter_types (commission_bonus_rule_id, promoter_type_id)
SELECT commission_bonus_rules_id, promoter_type_id
FROM commission_bonus_rules
WHERE promoter_type_id IS NOT NULL;

ALTER TABLE commission_bonus_rules DROP COLUMN promoter_type_id;

-- ─── collection_commission_tier_promoter_types ──────────────────────────────
CREATE TABLE collection_commission_tier_promoter_types
(
    collection_commission_tier_promoter_types_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    collection_commission_tier_id                BIGINT      NOT NULL REFERENCES collection_commission_tiers (collection_commission_tiers_id) ON DELETE CASCADE,
    promoter_type_id                             BIGINT      NOT NULL REFERENCES promoter_types (promoter_types_id),

    created_at                                   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by                                    UUID,

    UNIQUE (collection_commission_tier_id, promoter_type_id)
);

CREATE INDEX idx_collection_commission_tier_promoter_types_type ON collection_commission_tier_promoter_types (promoter_type_id);

INSERT INTO collection_commission_tier_promoter_types (collection_commission_tier_id, promoter_type_id)
SELECT collection_commission_tiers_id, promoter_type_id
FROM collection_commission_tiers
WHERE promoter_type_id IS NOT NULL;

ALTER TABLE collection_commission_tiers DROP COLUMN promoter_type_id;
