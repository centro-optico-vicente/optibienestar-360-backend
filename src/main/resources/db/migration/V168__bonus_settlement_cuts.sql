SET search_path TO app, public;

-- ============================================================================
-- V168: Fase B (hub plan competitive-commission-rules §12) — connects bonus
-- rules' partial_/final_/retroactive_settlement_period_strategy axes (V146,
-- saved since Fase A but never read by anything) to a real settlement-cut
-- engine, mirroring competitive_commission_award_settlements (V162) but as
-- columns on promoter_bonus_awards itself (no separate settlements table —
-- one row per (rule, promoter, window, cut) already IS the ledger row here).
--
-- idx_bonus_awards_dedup (V37) was a plain, non-unique index — dedup was only
-- ever a service-layer guarantee (H12). Replaced by a real UNIQUE constraint
-- now that BonusSettlementCutService upserts by natural key instead of
-- blind-inserting.
-- ============================================================================

ALTER TABLE promoter_bonus_awards
    ADD COLUMN cut_kind     VARCHAR(20),
    ADD COLUMN cut_sequence SMALLINT,
    ADD COLUMN cut_start    DATE,
    ADD COLUMN cut_end      DATE;

-- Backfill: every historical row is a single whole-window FINAL/1 cut.
UPDATE promoter_bonus_awards
SET cut_kind = 'FINAL', cut_sequence = 1, cut_start = window_start, cut_end = window_end
WHERE cut_kind IS NULL;

ALTER TABLE promoter_bonus_awards
    ALTER COLUMN cut_kind SET NOT NULL,
    ALTER COLUMN cut_sequence SET NOT NULL,
    ALTER COLUMN cut_start SET NOT NULL,
    ALTER COLUMN cut_end SET NOT NULL,
    ADD CONSTRAINT chk_promoter_bonus_awards_cut_kind
        CHECK (cut_kind IN ('PARTIAL', 'RETROACTIVE', 'FINAL'));

DROP INDEX IF EXISTS idx_bonus_awards_dedup;

-- Natural key exactly as spec'd (window_end deliberately excluded — a LIFETIME
-- rule's window_start never changes but window_end keeps advancing to "today"
-- on every evaluation, so it can't be part of the row's identity).
CREATE UNIQUE INDEX uq_promoter_bonus_awards_cut
    ON promoter_bonus_awards (bonus_rule_id, promoter_id, window_start, cut_kind, cut_sequence);

-- New daily job — absorbs BONUS_EVALUATION (disabled below, not deleted, same
-- retirement pattern V167 used for LEADERBOARD_PRIZE_AWARD): the settlement-cut
-- engine now handles the final cut too, on whatever cadence the rule's own
-- final_settlement_period_strategy resolves to, instead of a fixed monthly cron.
INSERT INTO scheduled_jobs (code, display_name, description, cron_expression, timezone)
VALUES (
    'BONUS_SETTLEMENT_CUT',
    'Corte de liquidación de bonos',
    'Recorre las reglas de bono activas y, para cada corte de liquidación (parcial, retroactivo o final) que vence hoy, calcula el derecho acumulado, lo neta contra lo ya otorgado y registra/actualiza el award PENDING correspondiente.',
    '0 40 3 * * *',
    'America/Caracas'
);

UPDATE scheduled_jobs SET enabled = false WHERE code = 'BONUS_EVALUATION';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM scheduled_jobs WHERE code = 'BONUS_SETTLEMENT_CUT') THEN
        RAISE EXCEPTION 'V168: BONUS_SETTLEMENT_CUT scheduled_jobs row was not created';
    END IF;
    IF EXISTS (SELECT 1 FROM promoter_bonus_awards WHERE cut_kind IS NULL) THEN
        RAISE EXCEPTION 'V168: promoter_bonus_awards backfill left NULL cut_kind rows';
    END IF;
END $$;
