SET search_path TO app, public;

-- ============================================================================
-- V165: D16 (hub plan competitive-commission-rules, Fase 2c) — open ties that
-- need a coordinator's call (only ever created when tie_policy = MANUAL, the
-- default for new rules) and every manual decision (tie resolution, redirect,
-- disqualify). The engine reads non-reverted decisions as pins/exclusions on
-- every run (see CompetitiveCommissionEvaluationService), so the job is
-- idempotent and never overrides a human's call.
-- ============================================================================

CREATE TABLE competitive_commission_ties
(
    competitive_commission_ties_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                            UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    competitive_commission_rule_id  BIGINT NOT NULL REFERENCES competitive_commission_rules,
    period_start                    DATE NOT NULL,
    period_end                      DATE NOT NULL,
    position_from                   INT  NOT NULL CHECK (position_from >= 1),
    slots                           INT  NOT NULL CHECK (slots >= 1),
    resolved_by                     UUID,
    resolved_at                     TIMESTAMPTZ,
    reason                          TEXT,
    is_active BOOLEAN NOT NULL DEFAULT true,
    status VARCHAR(50) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'RESOLVED', 'STALE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID, updated_by UUID,
    CONSTRAINT chk_cct_resolved CHECK (status <> 'RESOLVED' OR (resolved_at IS NOT NULL AND length(trim(reason)) >= 10))
);
CREATE TRIGGER trg_competitive_commission_ties_updated_at
    BEFORE UPDATE ON competitive_commission_ties
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
-- At most one still-open tie per (rule, period, position_from) — the engine updates it in place.
CREATE UNIQUE INDEX uq_cct_open ON competitive_commission_ties (competitive_commission_rule_id, period_start, position_from)
    WHERE is_active AND status IN ('OPEN', 'STALE');

CREATE TABLE competitive_commission_tie_candidates
(
    competitive_commission_tie_id BIGINT NOT NULL REFERENCES competitive_commission_ties ON DELETE CASCADE,
    promoter_id                   BIGINT NOT NULL REFERENCES promoters (promoters_id),
    metric_value                  NUMERIC(14, 2) NOT NULL,
    achieved_at                   TIMESTAMPTZ,
    metric_transaction_count      INT NOT NULL,
    selected                      BOOLEAN NOT NULL DEFAULT false,
    PRIMARY KEY (competitive_commission_tie_id, promoter_id)
);

-- Every manual decision (tie resolution, redirect, disqualification).
CREATE TABLE competitive_commission_manual_decisions
(
    competitive_commission_manual_decisions_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                            UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    competitive_commission_rule_id  BIGINT NOT NULL REFERENCES competitive_commission_rules,
    competitive_commission_tie_id   BIGINT REFERENCES competitive_commission_ties, -- TIE_RESOLUTION only
    period_start                    DATE NOT NULL,
    kind                            VARCHAR(20) NOT NULL CHECK (kind IN ('TIE_RESOLUTION', 'REDIRECT', 'DISQUALIFY')),
    award_position                  INT CHECK (award_position >= 1),
    -- The pinned winner (TIE_RESOLUTION/REDIRECT), or the disqualified promoter (DISQUALIFY).
    promoter_id                     BIGINT NOT NULL REFERENCES promoters (promoters_id),
    -- REDIRECT only: who loses the position.
    replaced_promoter_id            BIGINT REFERENCES promoters (promoters_id),
    -- DISQUALIFY only: also excluded from lower-priority rules of the same competition_group.
    exclude_from_group              BOOLEAN NOT NULL DEFAULT false,
    reason_category                 VARCHAR(30) NOT NULL DEFAULT 'OTHER'
        CHECK (reason_category IN ('TIE_BREAK', 'UNSPORTSMANLIKE_CONDUCT', 'DATA_ERROR', 'POLICY', 'OTHER')),
    reason                          TEXT NOT NULL CHECK (length(trim(reason)) >= 10),
    decided_by                      UUID NOT NULL,
    decided_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    reverted_at                     TIMESTAMPTZ,
    reverted_by                     UUID,
    revert_reason                   TEXT,
    is_active BOOLEAN NOT NULL DEFAULT true,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'STALE', 'REVERTED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID, updated_by UUID,
    CONSTRAINT chk_ccmd_revert CHECK (status <> 'REVERTED' OR (reverted_at IS NOT NULL AND length(trim(revert_reason)) >= 10)),
    CONSTRAINT chk_ccmd_redirect CHECK (kind <> 'REDIRECT' OR (replaced_promoter_id IS NOT NULL AND award_position IS NOT NULL))
);
CREATE TRIGGER trg_competitive_commission_manual_decisions_updated_at
    BEFORE UPDATE ON competitive_commission_manual_decisions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE INDEX idx_ccmd_rule_period ON competitive_commission_manual_decisions (competitive_commission_rule_id, period_start)
    WHERE is_active AND status = 'ACTIVE';

ALTER TABLE competitive_commission_awards
    ADD CONSTRAINT fk_cca_manual_decision FOREIGN KEY (manual_decision_id)
        REFERENCES competitive_commission_manual_decisions (competitive_commission_manual_decisions_id);


-- ─── Audit config ────────────────────────────────────────────────────────
INSERT INTO entity_config (entity_key, display_name, table_name) VALUES
    ('competitive_commission_tie', 'Empate de comisión competitiva', 'competitive_commission_ties'),
    ('competitive_commission_manual_decision', 'Decisión manual de comisión competitiva', 'competitive_commission_manual_decisions')
ON CONFLICT (entity_key) DO NOTHING;


-- ─── Fail loudly rather than migrate into a half-applied state ─────────────
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables
                   WHERE table_schema = 'app' AND table_name = 'competitive_commission_ties') THEN
        RAISE EXCEPTION 'V165: competitive_commission_ties was not created';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables
                   WHERE table_schema = 'app' AND table_name = 'competitive_commission_manual_decisions') THEN
        RAISE EXCEPTION 'V165: competitive_commission_manual_decisions was not created';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                   WHERE constraint_name = 'fk_cca_manual_decision') THEN
        RAISE EXCEPTION 'V165: fk_cca_manual_decision was not created';
    END IF;
END $$;
