SET search_path TO app, public;

-- ============================================================================
-- V167: Fase 3 (hub plan competitive-commission-rules) — migrates the legacy
-- leaderboard config/history into the new model before its Java code is
-- deleted in this same PR. One competitive_commission_rule per distinct
-- period_strategy found in active leaderboard_prizes (RANKING, metric =
-- COMMISSION_EARNED, accrual = final = partial = that strategy, retroactive
-- disabled — D14: RANKING pays only at close), one FLAT position per rank,
-- tie_policy = STRICT (matches the legacy leaderboard's own tie-break: no
-- manual step existed, so a migrated rule must not suddenly start blocking on
-- ties it never used to). Every leaderboard_prize_award becomes a
-- competitive_commission_award on the matching rule/position, preserving its
-- pay/void history verbatim.
--
-- Verified empty in optibienestar360_test (V158 seed: 0 rows in both legacy
-- tables — hub plan Fase 0 note, query 5.3). Written to handle real rows
-- correctly regardless, since production wasn't checked as of this writing;
-- run query 5.3 against production before this migration reaches it. The
-- legacy tables themselves are NOT dropped here (Fase 6, post-production,
-- once this migration's output is verified live).
-- ============================================================================

-- ─── 1. One rule per distinct period_strategy with an active prize ─────────
INSERT INTO competitive_commission_rules (
    name, description, metric, competition_type, achievement_date_basis, tie_policy,
    accrual_period_strategy, partial_settlement_period_strategy,
    final_settlement_period_strategy, retroactive_settlement_period_strategy,
    confirmation_delay_days, include_system_promoters, is_active
)
SELECT DISTINCT
    'Leaderboard ' || lp.period_strategy, 'Migrado del leaderboard legado (Fase 3)',
    'COMMISSION_EARNED', 'RANKING', 'APPROVED_AT', 'STRICT',
    lp.period_strategy, lp.period_strategy, lp.period_strategy, lp.period_strategy,
    0, false, true
FROM leaderboard_prizes lp
WHERE lp.is_active
ON CONFLICT DO NOTHING;

-- ─── 2. One FLAT position per migrated prize row ───────────────────────────
INSERT INTO competitive_commission_rule_positions (
    competitive_commission_rule_id, position_from, position_to, label,
    reward_type, flat_amount, reward_currency_id
)
SELECT ccr.competitive_commission_rules_id, lp.rank, lp.rank, 'Puesto ' || lp.rank,
       'FLAT', lp.prize_amount, lp.prize_currency_id
FROM leaderboard_prizes lp
         JOIN competitive_commission_rules ccr
              ON ccr.name = 'Leaderboard ' || lp.period_strategy
                  AND ccr.metric = 'COMMISSION_EARNED' AND ccr.competition_type = 'RANKING'
WHERE lp.is_active
  AND NOT EXISTS (
      SELECT 1 FROM competitive_commission_rule_positions ccrp
      WHERE ccrp.competitive_commission_rule_id = ccr.competitive_commission_rules_id
        AND ccrp.position_from = lp.rank
  );

-- ─── 3. Every award, verbatim, onto the matching rule + position ───────────
INSERT INTO competitive_commission_awards (
    competitive_commission_rule_id, competitive_commission_rule_position_id, promoter_id,
    period_start, period_end, award_position, metric_value, achieved_at, awarded_at,
    confirmed_at, reward_type, flat_amount, amount, currency_id,
    exchange_rate_at_paid, paid_rate_date, paid_at, payout_reference,
    rule_name_snapshot, snapshot_json, is_active, status
)
SELECT ccr.competitive_commission_rules_id, ccrp.competitive_commission_rule_positions_id, lpa.promoter_id,
       lpa.period_start, lpa.period_end, lpa.rank, lpa.metric_amount, NULL, lpa.awarded_at,
       CASE WHEN lpa.status IN ('PENDING', 'PAID') THEN lpa.awarded_at ELSE NULL END,
       'FLAT', lpa.prize_amount, lpa.prize_amount, lpa.prize_currency_id,
       lpa.exchange_rate_used, lpa.exchange_rate_date, lpa.paid_at, lpa.payout_reference,
       'Leaderboard ' || lpa.period_strategy,
       jsonb_build_object('migratedFrom', 'leaderboard_prize_awards', 'legacyUuid', lpa.uuid),
       lpa.is_active, lpa.status
FROM leaderboard_prize_awards lpa
         JOIN competitive_commission_rules ccr
              ON ccr.name = 'Leaderboard ' || lpa.period_strategy
                  AND ccr.metric = 'COMMISSION_EARNED' AND ccr.competition_type = 'RANKING'
         JOIN competitive_commission_rule_positions ccrp
              ON ccrp.competitive_commission_rule_id = ccr.competitive_commission_rules_id
                  AND ccrp.position_from = lpa.rank
WHERE NOT EXISTS (
    SELECT 1 FROM competitive_commission_awards cca
    WHERE cca.competitive_commission_rule_id = ccr.competitive_commission_rules_id
      AND cca.promoter_id = lpa.promoter_id
      AND cca.period_start = lpa.period_start
      AND cca.award_position = lpa.rank
);

-- ─── Disable (never delete) the legacy job — it has scheduled_job_runs history ───
UPDATE scheduled_jobs SET enabled = false WHERE code = 'LEADERBOARD_PRIZE_AWARD';

-- ─── Fail loudly if a leaderboard award somehow didn't find a home ─────────
DO $$
DECLARE
    legacy_awards  INT;
    migrated_awards INT;
BEGIN
    SELECT COUNT(*) INTO legacy_awards FROM leaderboard_prize_awards;
    SELECT COUNT(*) INTO migrated_awards FROM competitive_commission_awards
        WHERE snapshot_json ->> 'migratedFrom' = 'leaderboard_prize_awards';
    IF migrated_awards <> legacy_awards THEN
        RAISE EXCEPTION 'V167: expected % migrated awards, found % — leaderboard migration incomplete',
            legacy_awards, migrated_awards;
    END IF;
END $$;
