SET search_path TO app, public;

-- ============================================================================
-- V159: D15 (hub plan competitive-commission-rules, Fase A) — the retroactive
-- settlement axis is only meaningful when partial_settlement_period_strategy
-- is strictly finer than accrual_period_strategy. Before this migration none
-- of the 4 rule tables enforced that relationship. `core/util/SettlementAxes`
-- is the new single source of truth going forward (wired into
-- CommissionTiersService / HierarchyOverrideTiersService / BonusRulesService
-- / CollectionCommissionTiersService in this same PR). This migration
-- normalizes any existing row that violates it:
--
--   partial >= accrual (both periodic)  -> retroactive := partial, anchor := NULL
--     (a no-op: a single cut at the partial/final cadence — nothing to catch
--     up on separately — matches SettlementAxes.resolve's "disabled" branch)
--   partial <  accrual (both periodic) AND retroactive NOT IN [partial, accrual]
--                                        -> retroactive := partial, anchor := NULL
--     (retroactive was out of range; clip it to the finest legal value rather
--     than reject a live row)
--
-- Rows whose accrual is non-periodic (commission_bonus_rules' legacy CAMPAIGN/
-- LIFETIME) are left untouched — SettlementAxes treats those as "always
-- coarser than any periodic partial", so the retroactive axis stays enabled
-- and independently configurable, exactly as it behaves today.
--
-- Verified against the local optibienestar360_test DB (Fase 0 note,
-- .ai/notes/2026-09-25-competitive-rules-phase0.md, §5.1): every existing
-- rule there already has all 4 axes at MONTHLY, so this migration's UPDATE
-- touches 0 rows (the WHERE clause only matches genuinely un-normalized
-- rows — see the comment above it). This is expected to be a no-op in
-- production too, but is written to handle real data correctly regardless.
-- ============================================================================

-- Rank order: finer -> coarser. Only these 7 values are ever legal on the
-- partial/final/retroactive axes (DB CHECKs on all 4 tables already pin this).
CREATE TEMP TABLE _settlement_axis_rank (strategy VARCHAR(20), rank INT) ON COMMIT DROP;
INSERT INTO _settlement_axis_rank (strategy, rank) VALUES
    ('DAILY', 0), ('WEEKLY', 1), ('BIWEEKLY', 2), ('MONTHLY', 3),
    ('QUARTERLY', 4), ('SEMIANNUAL', 5), ('ANNUAL', 6);

DO $outer$
DECLARE
    tbl TEXT;
    updated INT;
BEGIN
    FOREACH tbl IN ARRAY ARRAY['commission_tiers', 'hierarchy_override_tiers',
                                'commission_bonus_rules', 'collection_commission_tiers']
    LOOP
        -- The WHERE clause below mirrors the verification block's "bad row"
        -- predicate exactly, so the UPDATE only touches rows that are
        -- genuinely not yet normalized — never a no-op rewrite of a row
        -- that's already correct (which would needlessly bump updated_at
        -- for every existing rule, since Fase 0 found all of them already
        -- normalized in the local test DB).
        EXECUTE format($f$
            UPDATE %I t
            SET retroactive_settlement_period_strategy = t.partial_settlement_period_strategy,
                retroactive_settlement_period_anchor = NULL
            FROM _settlement_axis_rank ra, _settlement_axis_rank rp, _settlement_axis_rank rr
            WHERE ra.strategy = t.accrual_period_strategy
              AND rp.strategy = t.partial_settlement_period_strategy
              AND rr.strategy = t.retroactive_settlement_period_strategy
              AND ((rp.rank >= ra.rank
                    AND (t.retroactive_settlement_period_strategy <> t.partial_settlement_period_strategy
                         OR t.retroactive_settlement_period_anchor IS NOT NULL))
                OR (rp.rank < ra.rank AND (rr.rank < rp.rank OR rr.rank > ra.rank)))
        $f$, tbl);
        GET DIAGNOSTICS updated = ROW_COUNT;
        IF updated > 0 THEN
            RAISE NOTICE 'V159: normalized % row(s) in %', updated, tbl;
        END IF;
    END LOOP;
END $outer$;

-- ─── Fail loudly if any row is still inconsistent with D15 afterwards ──────
DO $outer$
DECLARE
    tbl TEXT;
    bad INT;
BEGIN
    FOREACH tbl IN ARRAY ARRAY['commission_tiers', 'hierarchy_override_tiers',
                                'commission_bonus_rules', 'collection_commission_tiers']
    LOOP
        EXECUTE format($f$
            SELECT count(*) FROM %I t
            JOIN _settlement_axis_rank ra ON ra.strategy = t.accrual_period_strategy
            JOIN _settlement_axis_rank rp ON rp.strategy = t.partial_settlement_period_strategy
            JOIN _settlement_axis_rank rr ON rr.strategy = t.retroactive_settlement_period_strategy
            WHERE (rp.rank >= ra.rank
                   AND (t.retroactive_settlement_period_strategy <> t.partial_settlement_period_strategy
                        OR t.retroactive_settlement_period_anchor IS NOT NULL))
               OR (rp.rank < ra.rank AND (rr.rank < rp.rank OR rr.rank > ra.rank))
        $f$, tbl) INTO bad;
        IF bad > 0 THEN
            RAISE EXCEPTION 'V159: % still has % row(s) violating D15 after normalization', tbl, bad;
        END IF;
    END LOOP;
END $outer$;
