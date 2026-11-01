SET search_path TO app, public;

-- ============================================================================
-- V222: leave the FAMILIAR plan (the OptiBienestar 360 single program) with the
-- same pricing in every environment.
--
-- V177 only touched rows that still carried the original V14 values, so an
-- environment where the plan was edited by hand kept its own numbers. There is
-- no production yet, so this one is UNCONDITIONAL and overwrites those edits:
--     inscription $25, monthly $5, holder + 4 included (5 people),
--     up to 2 extra paid slots (6 in total), $5 inscription per extra slot.
--
-- Name, description, grace period and the term / re-inscription rule (V220) are
-- NOT touched: the term is still pending a client decision and is configured
-- in Planes.
-- ============================================================================
UPDATE plans
SET inscription_fee                   = 25.00,
    monthly_fee                       = 5.00,
    included_beneficiaries            = 4,
    max_beneficiaries                 = 6,
    extra_beneficiary_inscription_fee = 5.00
WHERE code = 'FAMILIAR';

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM plans WHERE code = 'FAMILIAR'
               AND (inscription_fee <> 25.00 OR monthly_fee <> 5.00 OR included_beneficiaries <> 4
                    OR max_beneficiaries <> 6 OR extra_beneficiary_inscription_fee <> 5.00)) THEN
        RAISE EXCEPTION 'V222: FAMILIAR plan pricing was not applied';
    END IF;
END $$;
