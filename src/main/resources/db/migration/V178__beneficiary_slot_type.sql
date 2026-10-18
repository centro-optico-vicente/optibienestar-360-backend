SET search_path TO app, public;

-- ============================================================================
-- V178: family nucleus slots (OB360 Membership Agreement). Each beneficiary
-- occupies either a BASE slot ("Núcleo Familiar Base", up to
-- plan.included_beneficiaries) or an EXTRA slot ("Extensión de Blindaje", up to
-- plan.max_beneficiaries - plan.included_beneficiaries, which carries the
-- one-time plan.extra_beneficiary_inscription_fee). The slot is assigned by the
-- service when the beneficiary is added or reactivated; exceeding
-- plan.max_beneficiaries is rejected (another membership is required).
--
-- Backfill: rows whose extra inscription was settled (extra_inscription_paid)
-- or charged (inscription_payment_id set, still PENDING) landed beyond the
-- included cap, so they are EXTRA; everything else stays BASE.
-- ============================================================================

ALTER TABLE beneficiaries
    ADD COLUMN slot_type VARCHAR(10) NOT NULL DEFAULT 'BASE'
        CONSTRAINT chk_beneficiaries_slot_type CHECK (slot_type IN ('BASE', 'EXTRA'));

COMMENT ON COLUMN beneficiaries.slot_type IS
    'Cupo que ocupa el beneficiario: BASE (núcleo familiar base, incluido en el plan) o EXTRA (extensión de blindaje, paga inscripción adicional)';

UPDATE beneficiaries
SET slot_type = 'EXTRA'
WHERE extra_inscription_paid = TRUE
   OR inscription_payment_id IS NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'app' AND table_name = 'beneficiaries' AND column_name = 'slot_type'
    ) THEN
        RAISE EXCEPTION 'V178: beneficiaries.slot_type was not created';
    END IF;
END $$;
