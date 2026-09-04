SET search_path TO app, public;

-- ============================================================
-- V89: memberships — snapshot the plan's currency at enrollment
-- (ADR 0015 follow-up). Same reasoning as inscription_fee /
-- monthly_fee / grace_period_days (V21 comment): pricing is
-- frozen at enrollment so later plan edits don't retroactively
-- change an active membership. Now that plans.currency_id exists
-- (V87), the snapshot is incomplete without it — an amount with
-- no currency is not a fact.
-- ============================================================

ALTER TABLE memberships ADD COLUMN currency_id BIGINT REFERENCES currencies (currencies_id);

UPDATE memberships m
SET currency_id = p.currency_id
FROM plans p
WHERE p.plans_id = m.plan_id;

ALTER TABLE memberships ALTER COLUMN currency_id SET NOT NULL;

CREATE INDEX idx_memberships_currency ON memberships (currency_id);
