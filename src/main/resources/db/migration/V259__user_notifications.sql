SET search_path TO app, public;

-- ============================================================================
-- V259: user_notifications — the in-app notification inbox (hub ADR 0039).
--
-- One row per recipient user. It is NOT the outbound email queue
-- (notifications, V28): that table keeps delivering mail with its own
-- PENDING → SENT / DEAD_LETTER lifecycle and has no read state, link or UI.
-- UserNotificationPublisher writes here and, when a command asks for it,
-- also enqueues the email in the V28 queue.
--
-- Rendering: the frontend builds the title and body from
-- i18n keys notifications.types.<TYPE>.* filled with `params`, so the text
-- follows the UI language. fallback_title / fallback_body are stored for
-- types the frontend does not know yet.
--
-- type: open code (REGISTRATION_APPROVED, PAYMENT_APPROVED, BIRTHDAY_TEAM, …),
-- no CHECK — same pattern as notifications.template_code. A new type lands
-- by code + i18n keys, not by a schema change.
--
-- category: closed list used to group/filter the inbox and, later, for
-- per-user preferences. severity: the visual tone of the notice.
--
-- dedup_key: optional idempotency key per (recipient, type). Publishing the
-- same key twice is a no-op, so scheduled jobs (birthdays, due reminders)
-- can re-run safely.
--
-- expires_at: optional; expired rows disappear from the inbox and from the
-- unread count (e.g. a birthday notice expires the same day).
-- ============================================================================

CREATE TABLE user_notifications
(
    user_notifications_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                  UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    -- ─── Recipient ──────────────────────────────────────────────────────────
    recipient_user_id     BIGINT       NOT NULL REFERENCES users (users_id),

    -- ─── Classification ─────────────────────────────────────────────────────
    type                  VARCHAR(80)  NOT NULL,
    category              VARCHAR(30)  NOT NULL
                            CHECK (category IN ('ACCOUNT', 'PAYMENTS', 'COMMISSIONS',
                                                'MEMBERSHIP', 'NETWORK', 'CELEBRATIONS')),
    severity              VARCHAR(20)  NOT NULL DEFAULT 'INFO'
                            CHECK (severity IN ('INFO', 'SUCCESS', 'WARNING', 'ERROR')),

    -- ─── Content ────────────────────────────────────────────────────────────
    -- Values interpolated by the frontend into the i18n text of the type.
    params                JSONB        NOT NULL DEFAULT '{}'::jsonb,
    fallback_title        VARCHAR(200) NOT NULL,
    fallback_body         TEXT,
    -- Internal frontend route the notice links to (e.g. '/allies').
    action_url            VARCHAR(500),

    -- ─── Origin / traceability ──────────────────────────────────────────────
    -- Free-form module name, snake_case singular (same as notifications).
    source_module         VARCHAR(40),
    source_entity_uuid    UUID,
    dedup_key             VARCHAR(200),

    -- ─── Inbox state ────────────────────────────────────────────────────────
    read_at               TIMESTAMPTZ,
    expires_at            TIMESTAMPTZ,

    -- ─── Audit (BaseEntity shape) ───────────────────────────────────────────
    is_active             BOOLEAN      NOT NULL DEFAULT TRUE,
    status                VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID
);

-- Inbox reads: "my notifications, unread first filter, newest first" and the
-- unread count polled by the frontend. Also covers the recipient FK.
CREATE INDEX idx_user_notifications_inbox
    ON user_notifications (recipient_user_id, read_at, created_at DESC);

-- Idempotent publish: one row per (recipient, type, dedup_key).
CREATE UNIQUE INDEX uq_user_notifications_dedup
    ON user_notifications (recipient_user_id, type, dedup_key)
    WHERE dedup_key IS NOT NULL;

CREATE TRIGGER trg_user_notifications_updated_at
    BEFORE UPDATE ON user_notifications
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE user_notifications IS 'In-app notification inbox, one row per recipient user (hub ADR 0039)';
COMMENT ON COLUMN user_notifications.type IS 'Open notification type code, e.g. REGISTRATION_APPROVED';
COMMENT ON COLUMN user_notifications.params IS 'Values the frontend interpolates into the i18n text of the type';
COMMENT ON COLUMN user_notifications.dedup_key IS 'Idempotency key per recipient and type; NULL = no dedup';
