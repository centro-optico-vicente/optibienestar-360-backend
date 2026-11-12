SET search_path TO app, public;

-- ============================================================================
-- V241: index the fast-growing business tables for the queries they serve.
--
-- Same approach as V240 (login_audit_log): no volume or metrics exist yet,
-- so every index below is matched to a query that runs today, and indexes no
-- query uses are dropped (each one taxes every write). Tables: persons +
-- members (afiliados), memberships, payments, promoters (asesores),
-- commissions, membership_charges, affiliation_requests, users.
--
-- Two facts drive most of it:
--   * Every admin listing defaults to `ORDER BY created_at DESC`
--     (core/util/SortOrder.java). Without an index, each page sorts the whole
--     filtered table; with one, the first page is read in order and stops.
--   * `?q=` searches build `immutable_unaccent(lower(col)) LIKE '%q%'` OR'ed
--     across several columns (core/util/SearchSpecifications.java). The planner
--     needs a trigram index on EVERY column of the OR to combine them
--     (BitmapOr); one missing column means a full scan. The expressions below
--     are written exactly as that class emits them.
-- ============================================================================

-- immutable_unaccent: schema-qualify its body (prerequisite) -----------------
-- V11 defined it as `SELECT unaccent('unaccent', $1)`, resolved through the
-- caller's search_path. Sessions of the app roles have `app, public`, but any
-- maintenance session does not: autovacuum/autoanalyze, ANALYZE run as
-- postgres, pg_dump/pg_restore (empty search_path). For them every index built
-- on the function fails ("function unaccent(unknown, text) does not exist"), so
-- persons/allies/products are never auto-analyzed and a plain pg_restore cannot
-- rebuild those indexes. Qualifying with the extension's actual schema fixes
-- it without changing the result, so existing indexes stay valid. The body
-- stays a plain SQL expression, so the planner still inlines it and matches
-- the indexes.
DO $$
DECLARE
    ext_schema text;
BEGIN
    SELECT n.nspname INTO ext_schema
      FROM pg_extension e
      JOIN pg_namespace n ON n.oid = e.extnamespace
     WHERE e.extname = 'unaccent';

    EXECUTE format(
        'CREATE OR REPLACE FUNCTION app.immutable_unaccent(text) RETURNS text '
        'LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT '
        'AS $f$ SELECT %1$I.unaccent(%2$L::regdictionary, $1) $f$',
        ext_schema, ext_schema || '.unaccent');
END $$;

-- persons — free-text search of members/users/payments ---------------------
-- MembersService/UserService/PaymentsService search full_name (trigram since
-- V15) + document_number + email + phone.
CREATE INDEX idx_persons_document_number_trgm
    ON persons USING gin (immutable_unaccent(lower(document_number)) gin_trgm_ops);

CREATE INDEX idx_persons_email_trgm
    ON persons USING gin (immutable_unaccent(lower(email)) gin_trgm_ops);

CREATE INDEX idx_persons_phone_trgm
    ON persons USING gin (immutable_unaccent(lower(phone)) gin_trgm_ops);

-- members ------------------------------------------------------------------
-- GET /v1/admin/members: is_active, default sort created_at DESC.
CREATE INDEX idx_members_active_created
    ON members (created_at DESC)
    WHERE is_active;

-- memberships --------------------------------------------------------------
-- GET /v1/admin/memberships defaults to next_due_date ASC with no status/active
-- filter; idx_memberships_status_next_due (status, …) WHERE is_active only
-- serves the status jobs.
CREATE INDEX idx_memberships_next_due
    ON memberships (next_due_date);

-- payments -----------------------------------------------------------------
-- GET /v1/admin/payments: is_active AND direction = ?, default sort created_at DESC.
CREATE INDEX idx_payments_active_created
    ON payments (created_at DESC)
    WHERE is_active;

-- Per-advisor windows: bonus evaluation/settlement
-- (findApprovedInForPromoter(s)InWindow: promoter_id + payment_date range) and
-- the advisor portal (promoter_id + payment_date range). Also covers the
-- promoters FK, so it replaces the single-column index.
DROP INDEX IF EXISTS idx_payments_promoter;
CREATE INDEX idx_payments_promoter_payment_date
    ON payments (promoter_id, payment_date DESC);

-- Competitive commission evaluation (every 15 min and in real time after each
-- approval): status = 'APPROVED' AND direction = 'IN' AND (payment_date BETWEEN
-- … OR received_at BETWEEN … OR reviewed_at BETWEEN …). An OR needs one index
-- per branch (BitmapOr); idx_payments_status_received is partial on is_active,
-- which these queries do not filter, so it cannot serve the received_at branch.
CREATE INDEX idx_payments_approved_in_payment_date
    ON payments (payment_date)
    WHERE status = 'APPROVED' AND direction = 'IN';

CREATE INDEX idx_payments_approved_in_received_at
    ON payments (received_at)
    WHERE status = 'APPROVED' AND direction = 'IN';

CREATE INDEX idx_payments_approved_in_reviewed_at
    ON payments (reviewed_at)
    WHERE status = 'APPROVED' AND direction = 'IN';

-- Dashboard (DashboardQueries): monthly approved collections
-- (COALESCE(reviewed_at, received_at) >= :from) and latest payments
-- (ORDER BY COALESCE(reviewed_at, received_at) DESC LIMIT n), both on
-- is_active AND direction = 'IN'.
CREATE INDEX idx_payments_in_event_at
    ON payments ((COALESCE(reviewed_at, received_at)) DESC)
    WHERE is_active AND direction = 'IN';

-- promoters (asesores) -----------------------------------------------------
-- GET /v1/admin/advisors: is_active, default sort created_at DESC.
CREATE INDEX idx_promoters_active_created
    ON promoters (created_at DESC)
    WHERE is_active;

-- commissions --------------------------------------------------------------
-- GET /v1/admin/commissions: is_active, default sort created_at DESC.
CREATE INDEX idx_commissions_active_created
    ON commissions (created_at DESC)
    WHERE is_active;

-- Competitive evaluation, COMMISSION_EARNED metric (findEarnedCandidatesInWindow):
-- is_active AND status IN ('APPROVED','PAID') AND earned_at BETWEEN … — only
-- (member_id, earned_at) existed.
CREATE INDEX idx_commissions_earned_window
    ON commissions (earned_at)
    WHERE is_active AND status IN ('APPROVED', 'PAID');

-- Settlement cuts (findApprovedForPeriod & co.): is_active AND status = 'APPROVED'
-- AND period_start >= … AND period_end <= …; also the PENDING scans. Replaces
-- the partial (status) index, which could only narrow by status.
DROP INDEX IF EXISTS idx_commissions_status_approval;
CREATE INDEX idx_commissions_status_period
    ON commissions (status, period_start, period_end)
    WHERE is_active;

-- membership_charges ---------------------------------------------------------
-- (status, due_date) matches no query in the code.
DROP INDEX IF EXISTS idx_membership_charges_status_due_date;

-- Competitive evaluation joins charges to payments on covered_by_payment_id
-- (findOverdueSettledCandidatesInWindow); also the payments FK, which had no index.
CREATE INDEX idx_membership_charges_covered_by_payment
    ON membership_charges (covered_by_payment_id)
    WHERE covered_by_payment_id IS NOT NULL;

-- affiliation_requests -------------------------------------------------------
-- REGISTRATION_DRAFT_CLEANUP: status = ? AND retain_from_cleanup = false AND
-- updated_at < :cutoff. The status is a bound parameter, so the index keys on
-- it instead of being partial on 'DRAFT'.
CREATE INDEX idx_affiliation_requests_status_updated
    ON affiliation_requests (status, updated_at);

-- users ----------------------------------------------------------------------
-- POST /v1/auth/reset-password and every activation link look users up by the
-- hashed token (UserRepository.findByPasswordResetToken): public endpoints that
-- scanned the whole table. Partial: only users with a pending link are indexed.
CREATE INDEX idx_users_password_reset_token
    ON users (password_reset_token)
    WHERE password_reset_token IS NOT NULL;

-- Indexed a constant (is_active WHERE is_active), and a 3-value status column
-- no query narrows by: both only cost writes.
DROP INDEX IF EXISTS idx_users_is_active;
DROP INDEX IF EXISTS idx_users_status;

-- allies ---------------------------------------------------------------------
-- Indexed a constant (is_published WHERE is_published); the public directory
-- filters is_published together with columns bound as parameters, and the
-- table stays small, so no replacement.
DROP INDEX IF EXISTS idx_allies_published;

-- Storage --------------------------------------------------------------------
-- Rows updated after insert (payment workflow, next_due_date moves, charge
-- coverage, wizard drafts): leave room per page so those updates stay HOT.
ALTER TABLE payments             SET (fillfactor = 90);
ALTER TABLE memberships          SET (fillfactor = 90);
ALTER TABLE membership_charges   SET (fillfactor = 90);
ALTER TABLE affiliation_requests SET (fillfactor = 90);

-- Vacuum/analyze the two largest transactional tables sooner than the 20%/10%
-- defaults, so planner statistics follow their growth.
ALTER TABLE payments    SET (autovacuum_vacuum_scale_factor = 0.05, autovacuum_analyze_scale_factor = 0.02);
ALTER TABLE commissions SET (autovacuum_vacuum_scale_factor = 0.05, autovacuum_analyze_scale_factor = 0.02);
