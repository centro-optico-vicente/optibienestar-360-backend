SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V25: promoters — sales network.
--
-- A promoter is the human (or institutional placeholder) the platform pays a
-- commission to for an affiliate enrollment. The flow per vertical-8 + the
-- v2 scope additions:
--
--   1. Promoter generates a short, unique `referral_code` once (6 chars
--      typical, but stored at VARCHAR(20) so future formats fit).
--   2. New affiliate ties to the promoter via that referral_code at
--      enrollment time (Member.promoter_id is set then; permanent link
--      per PDF 2.a — only `POST /v1/admin/members/{uuid}/assign-promoter`
--      reassigns).
--   3. Daily commission engine (V26 + v2 commission_tiers) attributes
--      revenue to the resolved promoter.
--
-- v2 columns baked in from day 1 (no ALTER chain): referral_code UNIQUE,
-- is_system flag, display_name + description for admin UI, contact
-- pair (email + phone) so a non-user system promoter (INSTITUCION) still
-- carries identifying contact info.
--
-- Two well-known consumers:
--   - Human promoters carry user_id (the User who logs into the promoter
--     portal) and a person_id for the civic identity.
--   - System promoter INSTITUCION has neither (is_system=TRUE) — exists
--     to anchor commissions when an enrollment came in through the
--     counter without a referral_code (per v2 PDF #5, the 100% utility
--     gets attributed to the central administration via INSTITUCION).
--
-- At the end: members.promoter_id is added as a NULLABLE FK to promoters.
-- The service layer enforces NOT NULL behavior at enrollment time (every
-- new member must resolve to either a real promoter or the INSTITUCION
-- fallback). NULLABLE at the schema level so legacy / test data and
-- back-fill scripts can land without a hard barrier.
-- ────────────────────────────────────────────────────────────────────────────


CREATE TABLE promoters
(
    promoters_id           BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                   UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    -- Identity links (both NULL on the INSTITUCION system row)
    user_id                BIGINT       REFERENCES users (users_id),
    person_id              BIGINT       REFERENCES persons (persons_id),

    -- Display
    -- For human promoters this defaults to `persons.full_name` (set by the
    -- service at create-time); the column exists separately because the
    -- system promoter (INSTITUCION) has no Person row to pull from.
    display_name           VARCHAR(120) NOT NULL,
    description            TEXT,

    -- Referral tracking (v2 PDF #4). Short code the promoter shares with
    -- prospective affiliates; persists for the life of the promoter.
    referral_code          VARCHAR(20)  NOT NULL UNIQUE,

    -- System flag (v2 PDF #5). When TRUE, this row anchors commissions for
    -- enrollments that came through the counter without a referral_code.
    -- Excluded from leaderboards.
    is_system              BOOLEAN      NOT NULL DEFAULT FALSE,

    -- Contact (used by both human + system rows). Email is informational,
    -- not a login credential — the User row attached via user_id holds the
    -- login email.
    email                  CITEXT,
    phone                  VARCHAR(30),

    -- Snapshot counters for the dashboard. Updated by the commission engine
    -- at period close — they are NOT the source of truth (commissions /
    -- referrals tables are) but they save the leaderboard from recomputing
    -- on every page load.
    total_referrals        INT          NOT NULL DEFAULT 0 CHECK (total_referrals >= 0),
    total_commission_paid  NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (total_commission_paid >= 0),

    -- Audit + soft-delete (BaseEntity columns)
    is_active              BOOLEAN      NOT NULL DEFAULT TRUE,
    status                 VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE'
                              CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED')),
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by             UUID,
    updated_by             UUID,

    -- A human promoter must carry both user + person; the INSTITUCION
    -- system row has neither. No "one or the other" allowed.
    CONSTRAINT chk_promoters_system_or_user CHECK (
        is_system = TRUE
        OR (user_id IS NOT NULL AND person_id IS NOT NULL)
    ),
    -- At most ONE system row platform-wide — enforced by the partial unique
    -- index below.
    -- referral_code shape: UPPER ALPHANUM, dash allowed, 4–20 chars.
    CONSTRAINT chk_promoters_referral_code_format CHECK (
        referral_code ~ '^[A-Z0-9-]{4,20}$'
    )
);

-- At most one row with is_system=TRUE
CREATE UNIQUE INDEX uniq_promoters_system_row
    ON promoters ((TRUE))
    WHERE is_system = TRUE;

-- User → promoter lookup (auth pipeline resolves the promoter of the
-- logged-in user). Partial so legacy rows without user_id don't bloat it.
CREATE UNIQUE INDEX uniq_promoters_user_id
    ON promoters (user_id)
    WHERE user_id IS NOT NULL AND is_active = TRUE;

-- Leaderboard filter — real promoters only, newest first.
CREATE INDEX idx_promoters_real_leaderboard
    ON promoters (total_commission_paid DESC)
    WHERE is_active = TRUE AND is_system = FALSE;

CREATE TRIGGER trg_promoters_updated_at
    BEFORE UPDATE ON promoters
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ─── Seed INSTITUCION system promoter ──────────────────────────────────────
--
-- Anchors commissions for enrollments without a referral_code. Excluded
-- from leaderboards (is_system=TRUE) but its commissions still compute
-- normally — the payout is contabilized to the institution rather than
-- paid to a human.
INSERT INTO promoters (
    display_name, description, referral_code, is_system, email
)
VALUES (
    'Centro Óptico Vicente — Administración',
    'Promotor del sistema. Atribuye al área de administración central las afiliaciones registradas por taquilla o sin código de referido. No participa en el ranking público de promotores; sus comisiones se contabilizan a favor de la institución y no se pagan a una persona física.',
    'INSTITUCION',
    TRUE,
    NULL
);


-- ─── members.promoter_id (deferred FK from V17 design intent) ─────────────
--
-- Add the column NULLABLE so legacy rows + back-fill scripts can land
-- without a hard barrier. The service layer enforces NOT NULL behavior:
-- every new enrollment resolves to either a real promoter (by
-- referral_code) or the INSTITUCION fallback (always available, seeded
-- above). The column is set ONCE at enrollment and is the permanent
-- attribution link (PDF 2.a) — only the explicit
-- `POST /v1/admin/members/{uuid}/assign-promoter` endpoint changes it.

ALTER TABLE members
    ADD COLUMN promoter_id BIGINT REFERENCES promoters (promoters_id);

-- Lookup index for "every member of promoter X" (promoter portfolio /
-- dashboard view).
CREATE INDEX idx_members_promoter_id
    ON members (promoter_id)
    WHERE promoter_id IS NOT NULL;
