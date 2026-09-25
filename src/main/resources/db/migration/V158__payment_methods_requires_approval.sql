SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V158: `payment_methods.requires_approval` (hub plan
-- "payment-method-auto-approval") — a catalog flag distinguishing
-- direct-receipt methods (cash collected in person: nothing to verify
-- against a bank/third-party statement) from methods that still need
-- admin/promoter review before the money can be trusted as collected
-- (transfer, Zelle, pago móvil, ...). A payment whose every line uses only
-- a `requires_approval = false` method skips PENDING entirely and lands
-- straight at APPROVED on registration (see PaymentsService#registerInternal).
--
-- Default TRUE preserves today's behavior for every existing/new method
-- unless explicitly opted out — the seeded CASH/Efectivo method (V115) is
-- the only one flipped here; every other method keeps requiring review.
-- ────────────────────────────────────────────────────────────────────────────

ALTER TABLE payment_methods ADD COLUMN requires_approval BOOLEAN NOT NULL DEFAULT true;

UPDATE payment_methods SET requires_approval = false WHERE code = 'CASH';
