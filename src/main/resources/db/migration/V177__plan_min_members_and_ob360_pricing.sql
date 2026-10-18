SET search_path TO app, public;

-- ============================================================================
-- V177: plan minimum members + OptiBienestar 360 pricing refresh.
--
-- The OptiBienestar 360 client documents (newest; they override the older
-- OptiSalud+ flyer used by V14) define:
--     Plan Único        $10 — one holder (renamed from "Plan Individual")
--     Plan Familiar     $25 — up to 5 people = holder + 4 beneficiaries,
--                       plus up to 2 extra paid slots (extra price not yet
--                       defined by the client → keep the current $5)
--     Plan Corporativo  $5 per person, MINIMUM of 20 people
--
-- 1. New nullable column `min_members`: minimum number of people a plan
--    requires (enforced today on corporate contracts' expected member count).
--    NULL = no minimum.
-- 2. Seed refresh. V14 already ran in every environment and admins may have
--    edited plans through the UI since, so every UPDATE is CONDITIONAL: it
--    only touches a row that still carries the exact original V14 values.
--    Admin-edited rows are left untouched. `updated_at` is maintained by the
--    trg_plans_updated_at trigger.
-- ============================================================================

-- ─── 1. min_members ─────────────────────────────────────────────────────────
ALTER TABLE plans
    ADD COLUMN min_members INT NULL
        CONSTRAINT ck_plans_min_members CHECK (min_members IS NULL OR min_members >= 1);

COMMENT ON COLUMN plans.min_members IS
    'Minimum number of people the plan requires (e.g. 20 for CORPORATIVO); NULL = no minimum';

-- ─── 2. FAMILIAR: $25, holder + 4 included, up to 2 extra paid slots ─────────
UPDATE plans
SET inscription_fee        = 25.00,
    included_beneficiaries = 4,
    max_beneficiaries      = 6
WHERE code = 'FAMILIAR'
  AND inscription_fee = 20.00
  AND included_beneficiaries = 3
  AND max_beneficiaries = 5;

UPDATE plans
SET description = 'Cobertura familiar — titular + 4 beneficiarios incluidos (hasta 5 personas). Permite hasta 2 cupos adicionales; cada adicional paga $5 USD de inscripción una sola vez. Mensualidad fija independiente del número de beneficiarios.'
WHERE code = 'FAMILIAR'
  AND description = 'Cobertura familiar — titular + hasta 3 beneficiarios incluidos sin costo extra. Permite hasta 5 beneficiarios en total; cada adicional paga $5 USD de inscripción una sola vez. Mensualidad fija independiente del número de beneficiarios.';

-- ─── 3. INDIVIDUAL: display name "Plan Único" (code stays INDIVIDUAL) ────────
UPDATE plans
SET name = 'Plan Único'
WHERE code = 'INDIVIDUAL'
  AND name = 'Plan Individual';

-- ─── 4. CORPORATIVO: minimum of 20 people ────────────────────────────────────
UPDATE plans
SET min_members = 20
WHERE code = 'CORPORATIVO'
  AND min_members IS NULL;
