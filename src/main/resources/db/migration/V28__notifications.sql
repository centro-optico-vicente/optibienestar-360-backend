SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V28: notifications — persistent outbound notification queue.
--
-- The application enqueues a row per notification (welcome email, payment
-- reminder, membership-suspended notice, referral reward, commission
-- payout receipt, etc.). A worker (NotificationService, vertical-9) polls
-- PENDING rows whose scheduled_for has passed, renders the template, and
-- delivers via the channel handler (today: SMTP / EmailService).
--
-- Persistence vs the previous "fire-and-forget @Async" path:
--   - survives JVM restart (no lost queued mail on deploy)
--   - retry policy with exponential backoff visible in the row
--   - audit trail per recipient and per source event
--   - admin queue UI can show "what failed, why, retry it"
--
-- Lifecycle (notifications.status):
--
--   PENDING  →  SENDING   (worker claimed the row)
--   SENDING  →  SENT      (channel ACKed delivery)
--            →  FAILED    (delivery error, next_retry_at set if more
--                          attempts left, else DEAD_LETTER)
--   FAILED   →  SENDING   (retry cycle picks it up again)
--            →  DEAD_LETTER (attempt_count reached max_attempts)
--
-- The 4 CHECKs at the bottom enforce the coherence rules per state.
--
-- Channels: v1 is EMAIL-only; the column is whitelisted so adding SMS /
-- PUSH later is a single ALTER + CHECK update, not a schema rewrite.
--
-- Template codes: open VARCHAR (no CHECK). The application resolves the
-- code to a Thymeleaf template on the classpath; new templates land by
-- adding HTML files, not by altering the schema. Same pattern
-- scheduled_jobs.code uses for runner resolution.
--
-- Deduplication: the (source_module, source_entity_uuid, template_code)
-- partial index supports an idempotent enqueue check ("did we already
-- send 'payment-approved' for this payment uuid?") without needing a
-- dedicated idempotency_key column. If a separate explicit key turns out
-- to be needed (e.g. for cross-source dedup), it can land later.
-- ────────────────────────────────────────────────────────────────────────────


CREATE TABLE notifications
(
    notifications_id        BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                    UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    -- ─── Channel ──────────────────────────────────────────────────────────
    channel                 VARCHAR(20)  NOT NULL DEFAULT 'EMAIL'
                              CHECK (channel IN ('EMAIL')),

    -- ─── Recipient (snapshot — emails can change later) ───────────────────
    -- recipient_email is captured at enqueue time so a user changing their
    -- address afterwards doesn't redirect already-queued mail. Optional
    -- recipient_user_id keeps the navigability link for admin drill-downs.
    recipient_email         CITEXT       NOT NULL,
    recipient_user_id       BIGINT       REFERENCES users (users_id),
    -- 'es' / 'en' — picks the *_es.html / *_en.html template variant
    recipient_locale        VARCHAR(10)  NOT NULL DEFAULT 'es',

    -- ─── Content ──────────────────────────────────────────────────────────
    template_code           VARCHAR(80)  NOT NULL,
    -- Subject is rendered at enqueue time from i18n bundle. Stored
    -- inline so the queue can be replayed without re-resolving message
    -- keys (templates are re-rendered per attempt, but the subject line
    -- is fixed once enqueued — locale changes after enqueue don't shift
    -- already-queued subjects).
    subject                 VARCHAR(200) NOT NULL,
    -- Vars interpolated into the template at render time. JSONB lets
    -- each template carry its own shape (e.g. payment-approved gets
    -- {paymentDate, amount, currency}; commission-payout gets
    -- {periodStart, periodEnd, total, csv}). Hibernate 6 maps via
    -- @JdbcTypeCode(SqlTypes.JSON) on Map<String,Object>.
    template_vars           JSONB        NOT NULL DEFAULT '{}'::jsonb,

    -- ─── Origin / traceability ───────────────────────────────────────────
    -- Free-form module name ('payment' / 'membership' / 'referral' /
    -- 'commission' / 'auth' / etc.). No CHECK whitelist — new sources
    -- land by code change, not schema change. Service convention is
    -- snake_case singular.
    source_module           VARCHAR(40),
    -- UUID of the originating row (payment.uuid, membership.uuid, etc.).
    -- Together with source_module + template_code this powers the
    -- "did we already notify about X?" idempotent enqueue check.
    source_entity_uuid      UUID,

    -- ─── Lifecycle ───────────────────────────────────────────────────────
    status                  VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                              CHECK (status IN (
                                  'PENDING',      -- enqueued, waiting for worker
                                  'SENDING',      -- worker claimed the row
                                  'SENT',         -- channel ACKed
                                  'FAILED',       -- delivery error, may retry
                                  'DEAD_LETTER'   -- attempt_count = max_attempts
                              )),
    -- When the worker should first consider this row. Defaults to NOW
    -- (immediate). Future use: scheduled-at sends, "remind me at 8am".
    scheduled_for           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    -- ─── Retry policy ────────────────────────────────────────────────────
    -- attempt_count increments per try. max_attempts is per-row so a
    -- one-shot critical alert can be max_attempts=1, a routine reminder
    -- can be max_attempts=5. Service default is 5.
    attempt_count           INT          NOT NULL DEFAULT 0  CHECK (attempt_count >= 0),
    max_attempts            INT          NOT NULL DEFAULT 5  CHECK (max_attempts >= 1),
    -- Bookkeeping for the retry decision
    last_attempt_at         TIMESTAMPTZ,
    -- next_retry_at is set by the service when FAILED + more attempts
    -- left, using exponential backoff (60s × 2^(attempt_count-1) clamped
    -- to e.g. 1h). NULL while PENDING / SENDING / SENT / DEAD_LETTER.
    next_retry_at           TIMESTAMPTZ,
    last_error_message      TEXT,

    -- ─── Delivery outcome ────────────────────────────────────────────────
    sent_at                 TIMESTAMPTZ,

    -- ─── Audit (BaseEntity shape) ────────────────────────────────────────
    is_active               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by              UUID,
    updated_by              UUID,

    -- ─── Coherence ───────────────────────────────────────────────────────
    CONSTRAINT chk_notifications_sent_has_sent_at CHECK (
        status <> 'SENT' OR sent_at IS NOT NULL
    ),
    CONSTRAINT chk_notifications_failed_has_error CHECK (
        status NOT IN ('FAILED', 'DEAD_LETTER') OR last_error_message IS NOT NULL
    ),
    CONSTRAINT chk_notifications_attempt_bounds CHECK (
        attempt_count <= max_attempts
    ),
    CONSTRAINT chk_notifications_dead_letter_exhausted CHECK (
        status <> 'DEAD_LETTER' OR attempt_count >= max_attempts
    )
);


-- ─── Indexes ────────────────────────────────────────────────────────────

-- Worker poll: pick the next PENDING batch whose scheduled_for has come.
-- The partial WHERE keeps the index small (SENT + DEAD_LETTER rows are
-- the bulk of the table over time and don't matter to the worker).
CREATE INDEX idx_notifications_pending_due
    ON notifications (scheduled_for)
    WHERE status = 'PENDING' AND is_active;

-- Retry poll: FAILED rows whose next_retry_at has come. Separate index
-- from the PENDING one so the two pollers don't fight for the same scan.
CREATE INDEX idx_notifications_retry_due
    ON notifications (next_retry_at)
    WHERE status = 'FAILED' AND next_retry_at IS NOT NULL AND is_active;

-- Per-recipient history: "show me everything sent to user X, newest first"
CREATE INDEX idx_notifications_recipient_created
    ON notifications (recipient_user_id, created_at DESC)
    WHERE recipient_user_id IS NOT NULL AND is_active;

-- Idempotency lookup: "did we already enqueue 'payment-approved' for
-- payment uuid Y?" Composite (module, entity, template) so callers can
-- pre-check before enqueueing a duplicate.
CREATE INDEX idx_notifications_source
    ON notifications (source_module, source_entity_uuid, template_code)
    WHERE source_entity_uuid IS NOT NULL;

-- Dead-letter admin queue: oldest first so admins triage backlogs.
CREATE INDEX idx_notifications_dead_letter
    ON notifications (created_at)
    WHERE status = 'DEAD_LETTER';


CREATE TRIGGER trg_notifications_updated_at
    BEFORE UPDATE ON notifications
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ─── Permission catalog extension ──────────────────────────────────────────

-- New domain for the admin panel — groups the NOTIFICATION_* perms
INSERT INTO permission_domains (code, name, icon, description, display_order)
VALUES ('NOTIFICATIONS', 'Notificaciones', 'i-lucide-bell',
        'Cola persistente de notificaciones outbound del sistema (email v1)', 120);

-- 3 new permissions — NOTIFICATION_* prefix; same naming pattern as JOB_* / PROMOTER_*
INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('NOTIFICATION_VIEW_ALL', 'NOTIFICATIONS', 'Ver toda la cola de notificaciones y su historial de envíos'),
    ('NOTIFICATION_RESEND',   'NOTIFICATIONS', 'Reintentar manualmente una notificación fallida o en dead-letter'),
    ('NOTIFICATION_VIEW_OWN', 'NOTIFICATIONS', 'Ver notificaciones propias enviadas a la cuenta del usuario')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code;

-- Grant VIEW_ALL + RESEND to SYSTEM + ADMINISTRADOR — operational
-- queue management is admin-only (no operator-level access).
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name IN ('SYSTEM', 'ADMINISTRADOR')
  AND p.name IN ('NOTIFICATION_VIEW_ALL', 'NOTIFICATION_RESEND');

-- Grant VIEW_OWN to every role that has a personal inbox — mirrors the
-- REFERRAL_CODE_VIEW_OWN distribution from V6 (AFILIADO + PROMOTOR +
-- ALIADO + SYSTEM + ADMINISTRADOR). OPERADOR* roles are excluded by
-- omission: they act on behalf of others, they don't receive personal
-- notifications.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name IN ('SYSTEM', 'ADMINISTRADOR', 'AFILIADO', 'PROMOTOR', 'ALIADO')
  AND p.name = 'NOTIFICATION_VIEW_OWN';
