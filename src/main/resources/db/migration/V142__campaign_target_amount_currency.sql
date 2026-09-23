-- Hub plan 2026-09-22, "Parte adicional — Moneda de la meta (goal amount) de Campaign":
-- campaigns.target_amount had no associated currency. Adds the FK, required
-- whenever target_amount is set (mirrors commission_tiers.flat_amount_currency_id,
-- V120/V124 pattern) so the frontend can show "meta esperada: $X <moneda>".

ALTER TABLE campaigns
    ADD COLUMN target_amount_currency_id BIGINT REFERENCES currencies (currencies_id);

-- Pre-existing rows may have target_amount set without a currency, since the
-- column didn't exist yet. Backfill with USD (this codebase's default for
-- retroactively-added currency FKs — see V87/V90/V124) before the CHECK below
-- can enforce "target_amount implies currency" on every row.
UPDATE campaigns
SET target_amount_currency_id = (SELECT currencies_id FROM currencies WHERE code = 'USD')
WHERE target_amount IS NOT NULL
  AND target_amount_currency_id IS NULL;

ALTER TABLE campaigns
    ADD CONSTRAINT campaigns_target_amount_currency_xor CHECK (
        (target_amount IS NULL AND target_amount_currency_id IS NULL)
        OR (target_amount IS NOT NULL AND target_amount_currency_id IS NOT NULL)
    );
