SET search_path TO app, public;

-- ============================================================
-- V87: retrofit every monetary field to a real currency_id FK
-- (ADR 0015 §1). Two kinds of fix here:
--
--   (a) plans — has NO currency column at all today (the USD
--       assumption was only a code comment). Adds currency_id
--       NOT NULL, backfilled to USD.
--   (b) payments / commissions / commission_bonus_rules /
--       promoter_bonus_awards / leaderboard_prizes /
--       leaderboard_prize_awards / referrals / benefit_usages —
--       each has its own free `VARCHAR(3) currency` column with
--       no FK. Converts each to a `currency_id BIGINT` FK against
--       `currencies`, preserving NOT NULL / nullable per the
--       original column, then drops the VARCHAR column.
--
-- All existing data is 'USD' (payments/commissions/etc. default
-- 'USD'; nullable ones with a value are also 'USD' in practice per
-- the flyer pricing) or NULL, so the backfill is a straight JOIN
-- against currencies.code — no ambiguous conversion involved.
-- ============================================================

-- ─── (a) plans — column didn't exist before ──────────────────────────────────
ALTER TABLE plans ADD COLUMN currency_id BIGINT REFERENCES currencies (currencies_id);
UPDATE plans SET currency_id = (SELECT currencies_id FROM currencies WHERE code = 'USD');
ALTER TABLE plans ALTER COLUMN currency_id SET NOT NULL;
CREATE INDEX idx_plans_currency ON plans (currency_id);


-- ─── (b) payments.currency → payments.currency_id (NOT NULL) ────────────────
ALTER TABLE payments ADD COLUMN currency_id BIGINT REFERENCES currencies (currencies_id);
UPDATE payments p SET currency_id = c.currencies_id FROM currencies c WHERE c.code = p.currency;
ALTER TABLE payments ALTER COLUMN currency_id SET NOT NULL;
ALTER TABLE payments DROP COLUMN currency;
CREATE INDEX idx_payments_currency ON payments (currency_id);

-- ─── commissions.currency → commissions.currency_id (NOT NULL) ──────────────
-- commission_period_summary (V42) SELECTs commissions.currency directly —
-- drop the dependent view first, recreate it against currency_id after.
DROP VIEW commission_period_summary;

ALTER TABLE commissions ADD COLUMN currency_id BIGINT REFERENCES currencies (currencies_id);
UPDATE commissions cm SET currency_id = c.currencies_id FROM currencies c WHERE c.code = cm.currency;
ALTER TABLE commissions ALTER COLUMN currency_id SET NOT NULL;
ALTER TABLE commissions DROP COLUMN currency;
CREATE INDEX idx_commissions_currency ON commissions (currency_id);

CREATE VIEW commission_period_summary AS
SELECT c.promoter_id,
       c.period_strategy,
       c.period_start,
       c.period_end,
       COUNT(*)          AS commission_count,
       SUM(c.amount)     AS total_amount,
       MIN(c.currency_id) AS currency_id
FROM commissions c
WHERE c.is_active = TRUE
  AND c.status <> 'VOIDED'
GROUP BY c.promoter_id, c.period_strategy, c.period_start, c.period_end;

-- ─── commission_bonus_rules.reward_currency → reward_currency_id (NOT NULL) ─
ALTER TABLE commission_bonus_rules ADD COLUMN reward_currency_id BIGINT REFERENCES currencies (currencies_id);
UPDATE commission_bonus_rules r SET reward_currency_id = c.currencies_id FROM currencies c WHERE c.code = r.reward_currency;
ALTER TABLE commission_bonus_rules ALTER COLUMN reward_currency_id SET NOT NULL;
ALTER TABLE commission_bonus_rules DROP COLUMN reward_currency;

-- ─── promoter_bonus_awards.reward_currency → reward_currency_id (NOT NULL) ──
ALTER TABLE promoter_bonus_awards ADD COLUMN reward_currency_id BIGINT REFERENCES currencies (currencies_id);
UPDATE promoter_bonus_awards a SET reward_currency_id = c.currencies_id FROM currencies c WHERE c.code = a.reward_currency;
ALTER TABLE promoter_bonus_awards ALTER COLUMN reward_currency_id SET NOT NULL;
ALTER TABLE promoter_bonus_awards DROP COLUMN reward_currency;

-- ─── leaderboard_prizes.prize_currency → prize_currency_id (NOT NULL) ───────
ALTER TABLE leaderboard_prizes ADD COLUMN prize_currency_id BIGINT REFERENCES currencies (currencies_id);
UPDATE leaderboard_prizes p SET prize_currency_id = c.currencies_id FROM currencies c WHERE c.code = p.prize_currency;
ALTER TABLE leaderboard_prizes ALTER COLUMN prize_currency_id SET NOT NULL;
ALTER TABLE leaderboard_prizes DROP COLUMN prize_currency;

-- ─── leaderboard_prize_awards.prize_currency → prize_currency_id (NOT NULL) ─
ALTER TABLE leaderboard_prize_awards ADD COLUMN prize_currency_id BIGINT REFERENCES currencies (currencies_id);
UPDATE leaderboard_prize_awards a SET prize_currency_id = c.currencies_id FROM currencies c WHERE c.code = a.prize_currency;
ALTER TABLE leaderboard_prize_awards ALTER COLUMN prize_currency_id SET NOT NULL;
ALTER TABLE leaderboard_prize_awards DROP COLUMN prize_currency;

-- ─── referrals.reward_currency → reward_currency_id (nullable) ──────────────
-- Paired with reward_flat_amount via chk_referrals_reward_currency_paired —
-- drop + recreate against the new column name.
ALTER TABLE referrals DROP CONSTRAINT chk_referrals_reward_currency_paired;
ALTER TABLE referrals ADD COLUMN reward_currency_id BIGINT REFERENCES currencies (currencies_id);
UPDATE referrals r SET reward_currency_id = c.currencies_id FROM currencies c WHERE c.code = r.reward_currency;
ALTER TABLE referrals DROP COLUMN reward_currency;
ALTER TABLE referrals ADD CONSTRAINT chk_referrals_reward_currency_paired
    CHECK (reward_flat_amount IS NULL OR reward_currency_id IS NOT NULL);

-- ─── benefit_usages.copay_currency → copay_currency_id (nullable) ───────────
-- Paired with copay_amount via chk_benefit_usages_copay_paired — same drop +
-- recreate treatment.
ALTER TABLE benefit_usages DROP CONSTRAINT chk_benefit_usages_copay_paired;
ALTER TABLE benefit_usages ADD COLUMN copay_currency_id BIGINT REFERENCES currencies (currencies_id);
UPDATE benefit_usages b SET copay_currency_id = c.currencies_id FROM currencies c WHERE c.code = b.copay_currency;
ALTER TABLE benefit_usages DROP COLUMN copay_currency;
ALTER TABLE benefit_usages ADD CONSTRAINT chk_benefit_usages_copay_paired
    CHECK (
        (copay_amount IS NULL AND copay_currency_id IS NULL)
        OR
        (copay_amount IS NOT NULL AND copay_amount >= 0 AND copay_currency_id IS NOT NULL)
    );
