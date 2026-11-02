SET search_path TO app, public;

-- ============================================================================
-- V203: contract number on memberships + beneficiary reference on payments
-- (hub plan ".ai/plans/2026-10-29-payments-without-affiliation.md").
--
--   1. Every membership gets a human contract number ("OB-000123"): what a payer
--      quotes, and what a future bank reconciliation matches against. Assigned by
--      a BEFORE INSERT trigger from a sequence; existing rows are backfilled by
--      enrollment order.
--   2. A payment can say WHO it credits when the payer is not the holder (a
--      relative paying): the beneficiary person plus a snapshot of their document
--      and of the contract number at payment time, kept even if either changes.
--      The payer stays payments.person_id (the origin of the money).
-- ============================================================================

CREATE SEQUENCE membership_contract_seq;

ALTER TABLE memberships ADD COLUMN contract_number VARCHAR(20);

WITH ordered AS (
    SELECT memberships_id, row_number() OVER (ORDER BY enrolled_at, memberships_id) AS rn
    FROM memberships
)
UPDATE memberships m
SET contract_number = 'OB-' || lpad(o.rn::text, 6, '0')
FROM ordered o
WHERE m.memberships_id = o.memberships_id;

SELECT setval('membership_contract_seq', (SELECT count(*) FROM memberships) + 1, false);

ALTER TABLE memberships ALTER COLUMN contract_number SET NOT NULL;
CREATE UNIQUE INDEX uq_memberships_contract_number ON memberships (contract_number);

CREATE FUNCTION set_membership_contract_number() RETURNS trigger AS $$
BEGIN
    IF NEW.contract_number IS NULL THEN
        NEW.contract_number := 'OB-' || lpad(nextval('membership_contract_seq')::text, 6, '0');
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_memberships_contract_number
    BEFORE INSERT ON memberships
    FOR EACH ROW EXECUTE FUNCTION set_membership_contract_number();

COMMENT ON COLUMN memberships.contract_number IS
    'Número de contrato legible (OB-000123), asignado al inscribir; es lo que cita quien paga.';

ALTER TABLE payments
    ADD COLUMN beneficiary_person_id          BIGINT REFERENCES persons (persons_id),
    ADD COLUMN beneficiary_document           VARCHAR(30),
    ADD COLUMN beneficiary_contract_number    VARCHAR(20);

CREATE INDEX idx_payments_beneficiary_person ON payments (beneficiary_person_id) WHERE beneficiary_person_id IS NOT NULL;

COMMENT ON COLUMN payments.beneficiary_person_id IS
    'Persona a quien se acredita el pago cuando quien paga no es el titular; NULL = se acredita a quien paga.';
COMMENT ON COLUMN payments.beneficiary_document IS
    'Documento del beneficiario al momento del pago (referencia para cotejar con el banco).';
COMMENT ON COLUMN payments.beneficiary_contract_number IS
    'Número de contrato de la membresía indicada al momento del pago.';
