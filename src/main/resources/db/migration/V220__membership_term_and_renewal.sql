SET search_path TO app, public;

-- ============================================================================
-- V220: membership validity (term) inherited from the plan, with
-- re-inscription and automatic renewal (hub ADR 0034).
--
--   1. plans: re-inscription flag + the expiry rule (FIXED_DATE or PERIOD).
--   2. memberships: snapshot of the rule + term_started_at + inscription_due_since.
--   3. affiliation_requests: optional overrides set by the advisor / reviewer.
--   4. Permission MEMBERSHIP_TERM_UPDATE and the daily MEMBERSHIP_TERM_RENEWAL job.
--
-- term_fixed_day is limited to 1-28 so the date exists in every month (no
-- clamping needed, same rule as memberships.billing_start_day, V47).
-- ============================================================================

-- ─── 1. plans ───────────────────────────────────────────────────────────────
ALTER TABLE plans
    ADD COLUMN reinscription_required BOOLEAN  NOT NULL DEFAULT FALSE,
    ADD COLUMN term_mode              VARCHAR(20),
    ADD COLUMN term_fixed_month       SMALLINT,
    ADD COLUMN term_fixed_day         SMALLINT,
    ADD COLUMN term_period_months     SMALLINT,
    ADD CONSTRAINT chk_plans_term_mode CHECK (term_mode IS NULL OR term_mode IN ('FIXED_DATE', 'PERIOD')),
    ADD CONSTRAINT chk_plans_term_month CHECK (term_fixed_month IS NULL OR term_fixed_month BETWEEN 1 AND 12),
    ADD CONSTRAINT chk_plans_term_day CHECK (term_fixed_day IS NULL OR term_fixed_day BETWEEN 1 AND 28),
    ADD CONSTRAINT chk_plans_term_period CHECK (term_period_months IS NULL OR term_period_months > 0),
    ADD CONSTRAINT chk_plans_term_coherence CHECK (
        (reinscription_required = FALSE
            AND term_mode IS NULL AND term_fixed_month IS NULL AND term_fixed_day IS NULL AND term_period_months IS NULL)
        OR (reinscription_required = TRUE AND term_mode = 'FIXED_DATE'
            AND term_fixed_month IS NOT NULL AND term_fixed_day IS NOT NULL AND term_period_months IS NULL)
        OR (reinscription_required = TRUE AND term_mode = 'PERIOD'
            AND term_period_months IS NOT NULL AND term_fixed_month IS NULL AND term_fixed_day IS NULL)
    );

COMMENT ON COLUMN plans.reinscription_required IS
    'El plan exige reinscripción en cada vigencia; sin esto la membresía queda abierta (sin vencimiento).';
COMMENT ON COLUMN plans.term_mode IS 'FIXED_DATE (fecha anual fija) o PERIOD (N meses desde la afiliación).';

-- ─── 2. memberships (existing rows: no re-inscription, open-ended) ──────────
ALTER TABLE memberships
    ADD COLUMN reinscription_required BOOLEAN  NOT NULL DEFAULT FALSE,
    ADD COLUMN term_mode              VARCHAR(20),
    ADD COLUMN term_fixed_month       SMALLINT,
    ADD COLUMN term_fixed_day         SMALLINT,
    ADD COLUMN term_period_months     SMALLINT,
    ADD COLUMN term_started_at        DATE,
    ADD COLUMN inscription_due_since  DATE,
    ADD CONSTRAINT chk_memberships_term_mode CHECK (term_mode IS NULL OR term_mode IN ('FIXED_DATE', 'PERIOD')),
    ADD CONSTRAINT chk_memberships_term_month CHECK (term_fixed_month IS NULL OR term_fixed_month BETWEEN 1 AND 12),
    ADD CONSTRAINT chk_memberships_term_day CHECK (term_fixed_day IS NULL OR term_fixed_day BETWEEN 1 AND 28),
    ADD CONSTRAINT chk_memberships_term_period CHECK (term_period_months IS NULL OR term_period_months > 0),
    ADD CONSTRAINT chk_memberships_term_coherence CHECK (
        (reinscription_required = FALSE
            AND term_mode IS NULL AND term_fixed_month IS NULL AND term_fixed_day IS NULL AND term_period_months IS NULL)
        OR (reinscription_required = TRUE AND term_mode = 'FIXED_DATE'
            AND term_fixed_month IS NOT NULL AND term_fixed_day IS NOT NULL AND term_period_months IS NULL)
        OR (reinscription_required = TRUE AND term_mode = 'PERIOD'
            AND term_period_months IS NOT NULL AND term_fixed_month IS NULL AND term_fixed_day IS NULL)
    ),
    ADD CONSTRAINT chk_memberships_inscription_due CHECK (inscription_due_since IS NULL OR reinscription_required = TRUE);

-- Renewal job candidates: live memberships with re-inscription whose term ended.
CREATE INDEX idx_memberships_term_expiry ON memberships (expires_at)
    WHERE is_active = TRUE AND reinscription_required = TRUE;

COMMENT ON COLUMN memberships.reinscription_required IS 'Snapshot del plan (editable por admin): exige reinscripción en cada vigencia.';
COMMENT ON COLUMN memberships.term_started_at IS 'Inicio de la vigencia actual; expires_at es su fin.';
COMMENT ON COLUMN memberships.inscription_due_since IS
    'Fecha desde la que hay una reinscripción pendiente; la membresía queda SUSPENDED hasta aplicar un pago de inscripción aprobado.';

-- ─── 3. affiliation_requests: overrides chosen at request time ──────────────
ALTER TABLE affiliation_requests
    ADD COLUMN expires_at_override             DATE,
    ADD COLUMN reinscription_required_override BOOLEAN;

-- ─── 4. Permission + daily job ──────────────────────────────────────────────
INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('MEMBERSHIP_TERM_UPDATE', 'MEMBERSHIPS', 'Editar la vigencia (vencimiento y reinscripción) de una membresía')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r CROSS JOIN permissions p
WHERE r.name IN ('SYSTEM', 'ADMINISTRADOR') AND p.name = 'MEMBERSHIP_TERM_UPDATE'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- 01:30, before charge generation (02:00).
INSERT INTO scheduled_jobs (code, display_name, description, cron_expression, timezone, parameters)
VALUES (
    'MEMBERSHIP_TERM_RENEWAL',
    'Renovación de vigencia de membresías',
    'Renueva automáticamente la vigencia de las membresías vencidas; si el plan exige reinscripción, deja la membresía SUSPENDED hasta que se aplique un pago de inscripción aprobado.',
    '0 30 1 * * *',
    'America/Caracas',
    '{}'::jsonb
);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'MEMBERSHIP_TERM_UPDATE') THEN
        RAISE EXCEPTION 'V220: MEMBERSHIP_TERM_UPDATE was not created';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM scheduled_jobs WHERE code = 'MEMBERSHIP_TERM_RENEWAL') THEN
        RAISE EXCEPTION 'V220: MEMBERSHIP_TERM_RENEWAL was not seeded';
    END IF;
END $$;
