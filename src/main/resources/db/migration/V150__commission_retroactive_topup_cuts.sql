SET search_path TO app, public;

-- ============================================================================
-- V150: commission_retroactive_topup_cuts — successor ledger of
-- commission_retroactive_topups (V105), adding support for MULTIPLE
-- retroactive cuts inside the same accrual period (Fase A, hub plan
-- commission-frequency-currency-unification, retroactive settlement axis).
--
-- commission_retroactive_topups (V105) stays frozen as historical data —
-- one row per whole settlement period, overwritten in place on re-run. It
-- cannot represent "corte de retroactivo de la quincena 1" and "corte de
-- retroactivo de la quincena 2" of the same month as two distinct payout
-- events. This table can: one row per (promoter, ledger_type, accrual
-- period, cut_sequence), where cut_sequence is the 1-based order of the
-- retroactive cut inside the accrual window (PeriodCutCalculator, sliced by
-- each rule's own retroactive_settlement_period_strategy/anchor axis).
--
-- Netting: a later cut's target_amount_cumulative/already_paid_amount are
-- both cumulative from accrual_period_start through that cut's own cut_end
-- (not just that cut's slice); already_paid_amount additionally nets out
-- any earlier cut's own retro_amount already PAID, so crossing into a
-- higher band on a later cut pays only the incremental gap — never a
-- double top-up of what an earlier cut's retroactive already covered.
-- ============================================================================

CREATE TABLE commission_retroactive_topup_cuts
(
    commission_retroactive_topup_cuts_id BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                                  UUID        NOT NULL UNIQUE,

    -- The beneficiary — a promoter earning DIRECT_*, or a Supervisor/Coordinador
    -- earning HIERARCHY_OVERRIDE_*.
    promoter_id                           BIGINT      NOT NULL REFERENCES promoters (promoters_id),
    ledger_type                           VARCHAR(40) NOT NULL,

    accrual_period_start                  DATE        NOT NULL,
    accrual_period_end                    DATE        NOT NULL,

    cut_sequence                          INTEGER     NOT NULL,
    cut_start                             DATE        NOT NULL,
    cut_end                               DATE        NOT NULL,

    basis_amount_cumulative               NUMERIC(14, 2) NOT NULL,
    target_amount_cumulative              NUMERIC(14, 2) NOT NULL,
    already_paid_amount                   NUMERIC(14, 2) NOT NULL,
    retro_amount                          NUMERIC(14, 2) NOT NULL,
    currency_id                           BIGINT      NOT NULL REFERENCES currencies (currencies_id),

    tier_id                               BIGINT,
    tier_name_snapshot                    VARCHAR(80),

    payout_payment_id                     BIGINT      REFERENCES payments (payments_id),
    payout_reference                      VARCHAR(120),
    paid_at                               TIMESTAMPTZ,
    voided_at                             TIMESTAMPTZ,
    void_reason                           TEXT,

    is_active                             BOOLEAN     NOT NULL DEFAULT TRUE,
    status                                VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_at                            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                            TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by                            UUID,
    updated_by                            UUID,

    CONSTRAINT chk_commission_retroactive_topup_cuts_ledger_type
        CHECK (ledger_type IN (
            'DIRECT_INSCRIPTION', 'DIRECT_COLLECTION',
            'HIERARCHY_OVERRIDE_INSCRIPTION', 'HIERARCHY_OVERRIDE_COLLECTION'
        )),
    CONSTRAINT chk_commission_retroactive_topup_cuts_status
        CHECK (status IN ('PENDING', 'PAID', 'VOIDED')),
    CONSTRAINT chk_commission_retroactive_topup_cuts_sequence
        CHECK (cut_sequence >= 1),
    -- Idempotency: re-running the same cut for the same (beneficiary, ledger,
    -- accrual period, sequence) never double-inserts — the service upserts by
    -- this key instead (and a PAID row is never recomputed — see service).
    CONSTRAINT uq_commission_retroactive_topup_cuts_key
        UNIQUE (promoter_id, ledger_type, accrual_period_start, accrual_period_end, cut_sequence)
);

CREATE TRIGGER trg_commission_retroactive_topup_cuts_updated_at
    BEFORE UPDATE ON commission_retroactive_topup_cuts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE INDEX idx_commission_retroactive_topup_cuts_promoter_period
    ON commission_retroactive_topup_cuts (promoter_id, ledger_type, accrual_period_start, accrual_period_end);


-- ─── Audit config ────────────────────────────────────────────────────────
INSERT INTO entity_config (entity_key, display_name, table_name) VALUES
    ('commission_retroactive_topup_cut', 'Corte de retroactivo de comisiones', 'commission_retroactive_topup_cuts')
ON CONFLICT (entity_key) DO NOTHING;
