SET search_path TO app, public;

-- ============================================================
-- V90: promoter_member_contacts.promised_amount had no currency
-- at all (ADR 0015 follow-up). Nullable — mirrors promised_amount
-- itself, which only PAYMENT_PROMISE rows carry (V36 CHECK
-- promoter_member_contacts_promise_coherence). Existing promises
-- are all USD in practice (program pricing per ADR 0008).
-- ============================================================

ALTER TABLE promoter_member_contacts
    ADD COLUMN promised_currency_id BIGINT REFERENCES currencies (currencies_id);

UPDATE promoter_member_contacts c
SET promised_currency_id = (SELECT currencies_id FROM currencies WHERE code = 'USD')
WHERE c.promised_amount IS NOT NULL;

ALTER TABLE promoter_member_contacts
    ADD CONSTRAINT chk_promoter_member_contacts_promised_currency_paired CHECK (
        (promised_amount IS NULL AND promised_currency_id IS NULL)
        OR
        (promised_amount IS NOT NULL AND promised_currency_id IS NOT NULL)
    );
