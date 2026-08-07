SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V46: promoter-type scoping for the three commission-rule catalogs
-- (commission_tiers V42, commission_bonus_rules V37, collection_commission_
-- tiers V44). Adds a nullable promoter_type_id FK to promoter_types (V43) on
-- each table so a rule can optionally be scoped to a single promoter type.
-- NULL keeps the existing behaviour: the rule applies to every promoter type.
-- No backfill needed — existing rows stay unscoped.
-- ────────────────────────────────────────────────────────────────────────────

ALTER TABLE commission_tiers
    ADD COLUMN promoter_type_id BIGINT REFERENCES promoter_types (promoter_types_id);

CREATE INDEX idx_commission_tiers_promoter_type_id
    ON commission_tiers (promoter_type_id)
    WHERE promoter_type_id IS NOT NULL;

ALTER TABLE commission_bonus_rules
    ADD COLUMN promoter_type_id BIGINT REFERENCES promoter_types (promoter_types_id);

CREATE INDEX idx_commission_bonus_rules_promoter_type_id
    ON commission_bonus_rules (promoter_type_id)
    WHERE promoter_type_id IS NOT NULL;

ALTER TABLE collection_commission_tiers
    ADD COLUMN promoter_type_id BIGINT REFERENCES promoter_types (promoter_types_id);

CREATE INDEX idx_collection_commission_tiers_promoter_type_id
    ON collection_commission_tiers (promoter_type_id)
    WHERE promoter_type_id IS NOT NULL;
