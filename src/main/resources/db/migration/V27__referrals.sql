SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V27: referrals — affiliate-to-affiliate referral program.
--
-- Two distinct concepts share the term "referral code" in the platform —
-- this migration is about the AFFILIATE-side one:
--
--   - Promoter referral code (V25): a sales-team member's code. New
--     enrollments resolve to a real promoter via this code and earn
--     COMMISSIONS for that promoter (V26).
--   - Affiliate referral code (this migration): a regular member's
--     personal code. Friends who enroll using it land here as REFERRALS,
--     and the original affiliate becomes eligible for a REWARD (typically
--     a discount on their next monthly payment).
--
-- The two flows are independent: a single enrollment can resolve to one
-- promoter (commission to the sales team) AND one affiliate referrer
-- (reward to the existing affiliate). They live in different tables so
-- their lifecycles don't tangle.
--
-- Lifecycle (referrals.status):
--
--   PENDING_ENROLLMENT  →  REGISTERED   (referred actually enrolls)
--                       →  EXPIRED      (no enrollment within TTL)
--   REGISTERED           →  REWARD_GRANTED  (reward materialized into a
--                                           payment discount)
--                        →  VOIDED      (admin reversal — fraud, refund)
--
-- The reward value can be expressed as percentage (off the referrer's
-- next monthly fee) or flat (subtracted from the next payment). Mirrors
-- commissions.commission_pct / flat_amount XOR pattern.
--
-- members.referral_code is added at the end of this migration so every
-- affiliate gets a personal code on enrollment (the service generates a
-- short UPPER alphanumeric tag, kept unique via the UNIQUE index).
-- ────────────────────────────────────────────────────────────────────────────


CREATE TABLE referrals
(
    referrals_id           BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                   UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    -- The existing affiliate sharing their code
    referrer_member_id     BIGINT       NOT NULL REFERENCES members (members_id),

    -- The newly-enrolled affiliate. NULL while PENDING_ENROLLMENT (the row
    -- is created from a code-click capture or admin pre-registration);
    -- populated when the friend actually enrolls.
    referred_member_id     BIGINT       REFERENCES members (members_id),

    -- Snapshot of the code used at the moment of registration. Kept
    -- inline so renames of members.referral_code (admin edits, account
    -- merges) don't rewrite history.
    referral_code          VARCHAR(20)  NOT NULL,

    -- When the referred actually enrolled. NULL while PENDING_ENROLLMENT.
    enrolled_at            TIMESTAMPTZ,
    -- When this referral record expires if the referred never enrolls.
    -- NULL means "doesn't expire" (admin override).
    expires_at             TIMESTAMPTZ,

    -- Reward configuration (one of pct / flat, never both — CHECK below).
    -- NULL on both means "no reward yet decided" — typical state during
    -- PENDING_ENROLLMENT before the program rules are applied.
    reward_pct             NUMERIC(5, 2)  CHECK (reward_pct IS NULL OR (reward_pct >= 0 AND reward_pct <= 100)),
    reward_flat_amount     NUMERIC(10, 2) CHECK (reward_flat_amount IS NULL OR reward_flat_amount >= 0),
    reward_currency        VARCHAR(3),

    -- When the reward was materialized into an actual payment discount.
    -- payment_id is the row the discount was applied to.
    reward_payment_id      BIGINT       REFERENCES payments (payments_id),
    reward_granted_at      TIMESTAMPTZ,

    -- Admin notes / void rationale
    admin_notes            TEXT,
    void_reason            TEXT,

    -- Audit + workflow status
    is_active              BOOLEAN      NOT NULL DEFAULT TRUE,
    status                 VARCHAR(50)  NOT NULL DEFAULT 'PENDING_ENROLLMENT'
                              CHECK (status IN (
                                  'PENDING_ENROLLMENT',
                                  'REGISTERED',
                                  'EXPIRED',
                                  'REWARD_GRANTED',
                                  'VOIDED'
                              )),
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by             UUID,
    updated_by             UUID,

    -- Coherence: can't refer yourself
    CONSTRAINT chk_referrals_not_self CHECK (
        referred_member_id IS NULL OR referred_member_id <> referrer_member_id
    ),
    -- Reward: at most one of pct / flat (both NULL is the "not decided
    -- yet" state). XOR with the both-NULL escape because PENDING rows
    -- don't have a reward configured yet.
    CONSTRAINT chk_referrals_reward_pct_xor_flat CHECK (
        (reward_pct IS NULL AND reward_flat_amount IS NULL)
        OR
        (reward_pct IS NOT NULL AND reward_flat_amount IS NULL)
        OR
        (reward_pct IS NULL AND reward_flat_amount IS NOT NULL)
    ),
    -- Flat amount + currency move together
    CONSTRAINT chk_referrals_reward_currency_paired CHECK (
        reward_flat_amount IS NULL OR reward_currency IS NOT NULL
    ),
    -- Status coherence:
    --   PENDING_ENROLLMENT ⇒ referred_member_id IS NULL, enrolled_at IS NULL
    --   REGISTERED|REWARD_GRANTED|VOIDED ⇒ referred + enrolled_at NOT NULL
    --   EXPIRED            ⇒ referred IS NULL (the friend never enrolled)
    CONSTRAINT chk_referrals_pending_no_referred CHECK (
        status <> 'PENDING_ENROLLMENT'
        OR (referred_member_id IS NULL AND enrolled_at IS NULL)
    ),
    CONSTRAINT chk_referrals_registered_has_referred CHECK (
        status NOT IN ('REGISTERED', 'REWARD_GRANTED', 'VOIDED')
        OR (referred_member_id IS NOT NULL AND enrolled_at IS NOT NULL)
    ),
    CONSTRAINT chk_referrals_expired_unreferred CHECK (
        status <> 'EXPIRED' OR referred_member_id IS NULL
    ),
    -- REWARD_GRANTED requires the payment + timestamp
    CONSTRAINT chk_referrals_reward_consistency CHECK (
        status <> 'REWARD_GRANTED'
        OR (reward_payment_id IS NOT NULL AND reward_granted_at IS NOT NULL)
    ),
    -- VOIDED requires a reason (same UX promise as payments / commissions)
    CONSTRAINT chk_referrals_void_has_reason CHECK (
        status <> 'VOIDED' OR void_reason IS NOT NULL
    )
);


-- At most ONE active referral per (referrer, referred) pair. Partial
-- excludes EXPIRED + VOIDED so failed attempts don't block re-tries.
CREATE UNIQUE INDEX uniq_referrals_referrer_referred_active
    ON referrals (referrer_member_id, referred_member_id)
    WHERE referred_member_id IS NOT NULL
      AND status NOT IN ('EXPIRED', 'VOIDED');

-- "My referrals" — referrer history, newest first.
CREATE INDEX idx_referrals_referrer_created
    ON referrals (referrer_member_id, created_at DESC)
    WHERE is_active;

-- Reverse lookup: did this person enroll through a referral?
CREATE INDEX idx_referrals_referred
    ON referrals (referred_member_id)
    WHERE referred_member_id IS NOT NULL;

-- Code → resolve when a new affiliate enrolls with a code.
CREATE INDEX idx_referrals_code
    ON referrals (referral_code);

-- Admin queue: pending enrollments approaching expiration.
CREATE INDEX idx_referrals_pending_expiration
    ON referrals (expires_at)
    WHERE status = 'PENDING_ENROLLMENT' AND expires_at IS NOT NULL;

CREATE TRIGGER trg_referrals_updated_at
    BEFORE UPDATE ON referrals
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ─── members.referral_code ─────────────────────────────────────────────────
--
-- Each affiliate gets a personal code on enrollment. Service generates a
-- short UPPER alphanumeric tag (6-8 chars typical); UNIQUE index enforces
-- platform-wide uniqueness. NULLABLE at schema for back-fill flows; the
-- service enforces NOT NULL behavior at the alta of new members.
--
-- Note: this column is conceptually distinct from promoters.referral_code.
-- The same string COULD be assigned to a member and a promoter, but the
-- service-layer resolver gives precedence to the promoter table (so a
-- code collision routes to the sales-team flow, not the affiliate-reward
-- flow). Cross-table uniqueness is enforced application-side via
-- pre-check, not as a DB constraint.

ALTER TABLE members
    ADD COLUMN referral_code VARCHAR(20);

CREATE UNIQUE INDEX uniq_members_referral_code
    ON members (referral_code)
    WHERE referral_code IS NOT NULL;
