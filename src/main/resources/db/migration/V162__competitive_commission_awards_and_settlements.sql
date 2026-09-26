SET search_path TO app, public;

-- ============================================================================
-- V162: Fase 2b (hub plan competitive-commission-rules) — awards and their
-- settlement cuts. A rule's evaluation produces an award (the final right to
-- a prize for one position, one period); a cut ledger tracks what's actually
-- been paid toward it (D14: PARTIAL/RETROACTIVE/FINAL, same cumulative-netting
-- shape as commission_retroactive_topup_cuts, V150).
--
-- `selection_source` and (nullable, no FK yet) `manual_decision_id` are seeded
-- here so Fase 2c's manual-decision tables (empates, redirect, disqualify —
-- D16) only need an ALTER TABLE ADD CONSTRAINT, not a column add on live data.
-- ============================================================================

CREATE TABLE competitive_commission_awards
(
    competitive_commission_awards_id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                                     UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    competitive_commission_rule_id          BIGINT NOT NULL REFERENCES competitive_commission_rules,
    competitive_commission_rule_position_id BIGINT NOT NULL REFERENCES competitive_commission_rule_positions,
    promoter_id                             BIGINT NOT NULL REFERENCES promoters (promoters_id),

    period_start                            DATE NOT NULL,
    period_end                              DATE NOT NULL,
    award_position                          INT  NOT NULL CHECK (award_position >= 1),
    tie_group_size                          SMALLINT NOT NULL DEFAULT 1 CHECK (tie_group_size >= 1),

    metric_value                            NUMERIC(14, 2) NOT NULL,
    metric_transaction_count                INT NOT NULL DEFAULT 0,
    achieved_at                             TIMESTAMPTZ,
    awarded_at                              TIMESTAMPTZ NOT NULL DEFAULT now(),
    confirmed_at                            TIMESTAMPTZ,

    reward_type                             VARCHAR(20) NOT NULL,
    flat_amount                             NUMERIC(10, 2),
    reward_pct                              NUMERIC(5, 2),
    basis_amount                            NUMERIC(14, 2),
    amount                                  NUMERIC(12, 2) NOT NULL CHECK (amount >= 0),
    currency_id                             BIGINT NOT NULL REFERENCES currencies (currencies_id),
    exchange_rate_at_award                  NUMERIC(18, 8),
    award_rate_date                         DATE,

    paid_at                                 TIMESTAMPTZ,
    payout_reference                        VARCHAR(120),
    payout_payment_id                       BIGINT REFERENCES payments (payments_id),
    exchange_rate_at_paid                   NUMERIC(18, 8),
    paid_rate_date                          DATE,

    voided_at                               TIMESTAMPTZ,
    void_reason                             TEXT,
    admin_notes                             TEXT,

    rule_name_snapshot                      VARCHAR(150) NOT NULL,
    snapshot_json                           JSONB NOT NULL,

    -- D16 (Fase 2c): AUTO until a manual decision (tie resolution, redirect)
    -- pins this award instead — the FK to competitive_commission_manual_decisions
    -- is added once that table exists (V16x), so this stays a bare nullable id.
    selection_source                        VARCHAR(10) NOT NULL DEFAULT 'AUTO'
                                                 CHECK (selection_source IN ('AUTO', 'MANUAL')),
    manual_decision_id                      BIGINT,

    is_active BOOLEAN NOT NULL DEFAULT true,
    status VARCHAR(50) NOT NULL DEFAULT 'PROVISIONAL'
        CHECK (status IN ('PROVISIONAL', 'PENDING', 'PAID', 'VOIDED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID, updated_by UUID,

    CONSTRAINT chk_cca_period CHECK (period_end >= period_start),
    CONSTRAINT chk_cca_paid CHECK (status <> 'PAID' OR paid_at IS NOT NULL),
    CONSTRAINT chk_cca_voided CHECK (status <> 'VOIDED' OR voided_at IS NOT NULL)
);

CREATE TRIGGER trg_competitive_commission_awards_updated_at
    BEFORE UPDATE ON competitive_commission_awards
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- A promoter wins at most once per rule+period.
CREATE UNIQUE INDEX uq_cca_rule_period_promoter ON competitive_commission_awards
    (competitive_commission_rule_id, period_start, promoter_id) WHERE is_active AND status <> 'VOIDED';
-- A position is unique per rule+period, except when shared by a tie (SHARED_FULL/SPLIT).
CREATE UNIQUE INDEX uq_cca_rule_period_position ON competitive_commission_awards
    (competitive_commission_rule_id, period_start, award_position)
    WHERE is_active AND status <> 'VOIDED' AND tie_group_size = 1;
CREATE INDEX idx_cca_promoter ON competitive_commission_awards (promoter_id, created_at DESC);
CREATE INDEX idx_cca_status ON competitive_commission_awards (status) WHERE status IN ('PROVISIONAL', 'PENDING');

-- ─── Settlement cuts ledger (D14) ───────────────────────────────────────────
-- Keyed by rule (not just award) because an award may not exist yet when a
-- PARTIAL cut runs mid-period (FIRST_TO_REACH not yet confirmed).
CREATE TABLE competitive_commission_award_settlements
(
    competitive_commission_award_settlements_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                                         UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    competitive_commission_rule_id              BIGINT NOT NULL REFERENCES competitive_commission_rules,
    competitive_commission_award_id             BIGINT REFERENCES competitive_commission_awards,
    promoter_id                                 BIGINT NOT NULL REFERENCES promoters (promoters_id),

    period_start                                DATE NOT NULL,
    period_end                                  DATE NOT NULL,
    cut_kind                                    VARCHAR(20) NOT NULL CHECK (cut_kind IN ('PARTIAL', 'RETROACTIVE', 'FINAL')),
    cut_sequence                                INT NOT NULL CHECK (cut_sequence >= 1),
    cut_start                                    DATE NOT NULL,
    cut_end                                      DATE NOT NULL,

    award_position_at_cut                        INT,
    basis_amount_cumulative                      NUMERIC(14, 2),
    entitlement_cumulative                       NUMERIC(12, 2) NOT NULL,
    already_paid_amount                          NUMERIC(12, 2) NOT NULL,
    amount                                        NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    currency_id                                  BIGINT NOT NULL REFERENCES currencies (currencies_id),
    exchange_rate_at_paid                        NUMERIC(18, 8),
    paid_rate_date                               DATE,

    payout_reference                             VARCHAR(120),
    payout_payment_id                            BIGINT REFERENCES payments (payments_id),
    paid_at                                       TIMESTAMPTZ,
    voided_at                                     TIMESTAMPTZ,
    void_reason                                   TEXT,

    is_active BOOLEAN NOT NULL DEFAULT true,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PAID', 'VOIDED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID, updated_by UUID,

    CONSTRAINT uq_ccas_cut UNIQUE (competitive_commission_rule_id, promoter_id, period_start, cut_kind, cut_sequence)
);

CREATE TRIGGER trg_competitive_commission_award_settlements_updated_at
    BEFORE UPDATE ON competitive_commission_award_settlements
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE INDEX idx_ccas_award ON competitive_commission_award_settlements (competitive_commission_award_id);
CREATE INDEX idx_ccas_pending ON competitive_commission_award_settlements (status) WHERE status = 'PENDING';


-- ─── Audit config ────────────────────────────────────────────────────────
INSERT INTO entity_config (entity_key, display_name, table_name) VALUES
    ('competitive_commission_award', 'Premio de comisión competitiva', 'competitive_commission_awards'),
    ('competitive_commission_award_settlement', 'Corte de premio competitivo', 'competitive_commission_award_settlements')
ON CONFLICT (entity_key) DO NOTHING;


-- ─── Fail loudly rather than migrate into a half-applied state ─────────────
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables
                   WHERE table_schema = 'app' AND table_name = 'competitive_commission_awards') THEN
        RAISE EXCEPTION 'V162: competitive_commission_awards was not created';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables
                   WHERE table_schema = 'app' AND table_name = 'competitive_commission_award_settlements') THEN
        RAISE EXCEPTION 'V162: competitive_commission_award_settlements was not created';
    END IF;
END $$;
