SET search_path TO app, public;

-- ============================================================================
-- V174: attribute unattributed collections to the INSTITUCION system promoter.
-- PaymentsService used to copy members.promoter_id verbatim, so a payment of a
-- member without a promoter landed with promoter_id NULL — invisible to the
-- competitive metric queries (which filter promoter_id IS NOT NULL) even though
-- its commission was already attributed to INSTITUCION. New payments now
-- resolve the same way commissions do (PromoterResolver#resolveForAttribution);
-- this backfills the existing APPROVED/PENDING collections. Commission payouts
-- (direction OUT) carry their own promoter and are left untouched.
-- ============================================================================

UPDATE payments
SET promoter_id = (SELECT promoters_id FROM promoters WHERE referral_code = 'INSTITUCION')
WHERE promoter_id IS NULL
  AND direction = 'IN'
  AND status IN ('APPROVED', 'PENDING')
  AND EXISTS (SELECT 1 FROM promoters WHERE referral_code = 'INSTITUCION');

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM promoters WHERE referral_code = 'INSTITUCION')
       AND EXISTS (
           SELECT 1 FROM payments
           WHERE promoter_id IS NULL AND direction = 'IN' AND status IN ('APPROVED', 'PENDING')
       ) THEN
        RAISE EXCEPTION 'V174: unattributed APPROVED/PENDING collections remain after backfill';
    END IF;
END $$;
