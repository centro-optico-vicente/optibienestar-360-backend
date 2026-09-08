SET search_path TO app, public;

-- ============================================================================
-- V105: commission_retroactive_topups — generic top-up ledger for "corte
-- parcial + retroactivo al cierre" (hub plan
-- ".ai/plans/2026-09-07-hierarchical-commissions-plan.md" §3, PR4).
--
-- Context: a promoter can be paid partial cuts (e.g. weekly) at whatever
-- band their month-to-date volume qualifies for at that moment. If a later
-- cut crosses into a higher band, the earlier cuts — already PAID, and
-- therefore untouched by CommissionReRatingService/HierarchyOverrideReRatingService
-- (which only mutate PENDING rows) — stay underpaid relative to the final
-- month total. This table is the once-per-settlement-close correction: one
-- row per (beneficiary, ledger_type, settlement period), holding the delta
-- between what the final highest band would have paid on the whole period's
-- basis and what was actually already PAID.
--
-- `ledger_type` is deliberately generic (not one table per source) so any
-- future "partial + retro" ledger reuses this same shape without a new
-- migration — the hub plan's explicit ask when it flagged "no querer
-- hardcodear frecuencias".
-- ============================================================================

CREATE TABLE commission_retroactive_topups
(
    commission_retroactive_topups_id BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                              UUID        NOT NULL UNIQUE,

    -- The beneficiary — a promoter earning DIRECT_*, or a Supervisor/Coordinador
    -- earning HIERARCHY_OVERRIDE_*.
    promoter_id                       BIGINT      NOT NULL REFERENCES promoters (promoters_id),
    ledger_type                       VARCHAR(40) NOT NULL,

    period_start                      DATE        NOT NULL,
    period_end                        DATE        NOT NULL,

    basis_amount                      NUMERIC(10, 2) NOT NULL,
    target_amount                     NUMERIC(10, 2) NOT NULL,
    already_paid_amount               NUMERIC(10, 2) NOT NULL,
    retro_amount                      NUMERIC(10, 2) NOT NULL,
    currency_id                       BIGINT      NOT NULL REFERENCES currencies (currencies_id),

    tier_id                           BIGINT,
    tier_name_snapshot                VARCHAR(80),

    payout_reference                  VARCHAR(120),
    paid_at                           TIMESTAMPTZ,
    voided_at                         TIMESTAMPTZ,
    void_reason                       TEXT,

    is_active                         BOOLEAN     NOT NULL DEFAULT TRUE,
    status                            VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_at                        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                        TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by                        UUID,
    updated_by                        UUID,

    CONSTRAINT chk_commission_retroactive_topups_ledger_type
        CHECK (ledger_type IN (
            'DIRECT_INSCRIPTION', 'DIRECT_COLLECTION',
            'HIERARCHY_OVERRIDE_INSCRIPTION', 'HIERARCHY_OVERRIDE_COLLECTION'
        )),
    CONSTRAINT chk_commission_retroactive_topups_status
        CHECK (status IN ('PENDING', 'PAID', 'VOIDED')),
    -- Idempotency: re-running the close for the same (beneficiary, ledger,
    -- period) never double-inserts — the service upserts by this key instead.
    CONSTRAINT uq_commission_retroactive_topups_key
        UNIQUE (promoter_id, ledger_type, period_start, period_end)
);

CREATE TRIGGER trg_commission_retroactive_topups_updated_at
    BEFORE UPDATE ON commission_retroactive_topups
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE INDEX idx_commission_retroactive_topups_promoter_status
    ON commission_retroactive_topups (promoter_id, status, period_start, period_end);


-- ─── Audit config ────────────────────────────────────────────────────────
INSERT INTO entity_config (entity_key, display_name, table_name) VALUES
    ('commission_retroactive_topup', 'Retroactivo de comisiones por corte', 'commission_retroactive_topups')
ON CONFLICT (entity_key) DO NOTHING;
