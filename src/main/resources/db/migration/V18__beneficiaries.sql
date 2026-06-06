SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V18: beneficiaries — family members covered under a parent Member's plan.
--
-- Each beneficiary is 1:1 with a `persons` row (identity hub from V15), N:1
-- with a `members` row (the titular). The same person can be a beneficiary
-- of multiple titulars in different plans (rare but valid — e.g. a child
-- whose divorced parents each maintain their own subscription) — that's why
-- the uniqueness key is (member_id, person_id) NOT just (person_id).
--
-- Demographic data lives in persons, not duplicated here. This table only
-- holds the relationship (SPOUSE / CHILD / PARENT / SIBLING / OTHER) and the
-- v2 "extra inscription" flag for beneficiaries that exceeded the plan's
-- `included_beneficiaries` cap. The FK to payments will land in V21 when
-- the payments table exists; until then the `inscription_payment_id` is a
-- plain BIGINT (nullable, no FK constraint).
--
-- Removal / substitution semantics (v2 scope addition):
--   - DELETE soft-deletes via is_active=false. The unique (member_id,
--     person_id) intentionally allows readmission via UPDATE active=true on
--     the existing row.
--   - Substitution of beneficiary A → B is a service-level operation: soft-
--     delete the row for A, INSERT a new row for B. Plan max_beneficiaries
--     enforced at the application layer (V20 plans, planned).
-- ────────────────────────────────────────────────────────────────────────────

CREATE TABLE beneficiaries
(
    beneficiaries_id        BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                    UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    -- Parent titular
    member_id               BIGINT       NOT NULL REFERENCES members (members_id) ON DELETE CASCADE,

    -- Identity hub link — same person CAN be beneficiary of more than one
    -- member (across distinct plans), but never twice under the same member.
    person_id               BIGINT       NOT NULL REFERENCES persons (persons_id),

    -- Familial connection. Keep the enum small enough to drive a frontend
    -- dropdown without a catalog table; expand via future migration if
    -- needed (e.g. add IN_LAW, COHABITANT for non-traditional families).
    relationship            VARCHAR(20)  NOT NULL
                                CHECK (relationship IN (
                                    'SPOUSE', 'CHILD', 'PARENT', 'SIBLING', 'OTHER'
                                )),

    -- v2 — extra inscription cost tracking. When a member exceeds the plan's
    -- included_beneficiaries cap, each additional beneficiary triggers a
    -- one-time inscription fee (plan.extra_beneficiary_inscription_fee). The
    -- service layer flips this flag when the fee is collected.
    extra_inscription_paid  BOOLEAN      NOT NULL DEFAULT FALSE,

    -- FK to payments (V21, planned). Plain BIGINT for now — the FK constraint
    -- lands in V21 via ALTER TABLE so V18 can apply without payments
    -- existing yet. NULL while the inscription is included in the plan or
    -- waiting for collection.
    inscription_payment_id  BIGINT,

    -- Audit + soft-delete (BaseEntity-style)
    is_active               BOOLEAN      NOT NULL DEFAULT TRUE,
    status                  VARCHAR(50),
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by              UUID,
    updated_by              UUID,

    -- One person can be the beneficiary of a given member exactly once. The
    -- soft-delete pattern reactivates the existing row instead of inserting
    -- a second one (matches the readmission semantics on members.person_id).
    UNIQUE (member_id, person_id)
);

-- member_id-side lookup is the dominant access pattern: "list all
-- beneficiaries of titular X".  The composite UNIQUE above already provides
-- a usable index leading with member_id, but we add a plain one to keep
-- planner choices simple and to support membership scans with no person
-- filter.
CREATE INDEX idx_beneficiaries_member_active
    ON beneficiaries (member_id)
    WHERE is_active = TRUE;

-- person-side: "where is this person also a beneficiary?" — useful for the
-- digital card / search-by-cedula validator flows.
CREATE INDEX idx_beneficiaries_person ON beneficiaries (person_id);

-- Cobranza scan — beneficiarios cuyo extra está pendiente de pago.
CREATE INDEX idx_beneficiaries_extra_unpaid
    ON beneficiaries (member_id)
    WHERE is_active = TRUE AND extra_inscription_paid = FALSE;

CREATE TRIGGER trg_beneficiaries_updated_at
    BEFORE UPDATE ON beneficiaries
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
