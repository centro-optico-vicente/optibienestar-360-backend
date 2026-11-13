SET search_path TO app, public;

-- ============================================================================
-- V242: index the write-heavy log tables for the queries they serve.
--
-- These tables take a row per business event and nothing purges them, so
-- every index is paid on every insert, forever. Same approach as V240/V241:
-- keep only what a query uses, shape it like that query, and stop indexing
-- columns that are always NULL (a btree indexes NULLs, so a single-column
-- index on an always-NULL column still grows with every row).
-- ============================================================================

-- data_change_audit_log --------------------------------------------------------
-- One row per @Auditable call (63 annotated services; ~29 methods on payments
-- alone), each with full before/after JSONB: the fastest-growing table.
-- DataChangeAuditWriter never fills entity_id, and the aspect passes NULL for
-- login_audit_log_id (DataChangeAuditAspect: "pending AuthService login hook").
--
-- GET /v1/admin/audit/data-changes: entity_key / entity_uuid / action / actor_id
-- equality + occurred_at range, sorted by occurred_at DESC; the pinned lookup
-- findFirstByEntityKeyAndEntityUuidOrderByOccurredAtAsc reads the same index
-- backwards.
DROP INDEX IF EXISTS idx_data_change_entity;
CREATE INDEX idx_data_change_entity_occurred
    ON data_change_audit_log (entity_key, entity_uuid, occurred_at DESC);

DROP INDEX IF EXISTS idx_data_change_actor;
CREATE INDEX idx_data_change_actor_occurred
    ON data_change_audit_log (actor_id, occurred_at DESC);

-- Always NULL today: index only the rows that will carry a value. The
-- login_audit_log_id one stays because its FK needs it (purging old sessions).
DROP INDEX IF EXISTS idx_data_change_entity_id;
CREATE INDEX idx_data_change_entity_id
    ON data_change_audit_log (entity_key, entity_id)
    WHERE entity_id IS NOT NULL;

DROP INDEX IF EXISTS idx_data_change_login_audit;
CREATE INDEX idx_data_change_login_audit
    ON data_change_audit_log (login_audit_log_id)
    WHERE login_audit_log_id IS NOT NULL;

ALTER TABLE data_change_audit_log SET (
    autovacuum_vacuum_insert_scale_factor = 0.05,
    autovacuum_analyze_scale_factor = 0.02
);

-- report_audit_log ---------------------------------------------------------------
-- One row per generated PDF/XLSX. GET /v1/admin/audit/reports filters
-- report_type / entity_key / entity_uuid / format / actor_id + generated_at
-- range, sorted by generated_at DESC (the ally record page asks for
-- entityKey + entityUuid + sort=generatedAt). Nothing filters by
-- entity_identifier (only the ?q= LIKE reads it, which a btree cannot serve),
-- and ReportAuditService never fills entity_id.
DROP INDEX IF EXISTS idx_report_audit_entity;
CREATE INDEX idx_report_audit_entity_generated
    ON report_audit_log (entity_key, entity_uuid, generated_at DESC);

DROP INDEX IF EXISTS idx_report_audit_actor;
CREATE INDEX idx_report_audit_actor_generated
    ON report_audit_log (actor_id, generated_at DESC);

DROP INDEX IF EXISTS idx_report_audit_entity_id;
CREATE INDEX idx_report_audit_entity_id
    ON report_audit_log (entity_key, entity_id)
    WHERE entity_id IS NOT NULL;

ALTER TABLE report_audit_log SET (
    autovacuum_vacuum_insert_scale_factor = 0.05,
    autovacuum_analyze_scale_factor = 0.02
);

-- user_sessions_log ----------------------------------------------------------------
-- Legacy per-login row (login_audit_log is the session record). Only looked up
-- as findByJtiAndLogoutAtIsNull on logout / role switch: index the open
-- sessions only, so it stays small as history accumulates.
DROP INDEX IF EXISTS idx_user_sessions_log_jti;
CREATE INDEX idx_user_sessions_log_open_jti
    ON user_sessions_log (jti)
    WHERE logout_at IS NULL;

-- scheduled_job_runs --------------------------------------------------------------
-- ~400 rows/day (dispatch every 5 min, competitive evaluation every 15 min,
-- daily jobs). GET /v1/admin/audit/job-runs defaults to created_at DESC, which
-- had no index; per-job history keeps (scheduled_job_id, started_at DESC).
-- Each run is inserted as RUNNING and updated once when it finishes.
CREATE INDEX idx_scheduled_job_runs_created
    ON scheduled_job_runs (created_at DESC);

ALTER TABLE scheduled_job_runs SET (fillfactor = 90);

-- notifications -------------------------------------------------------------------
-- Outbox: every dispatch attempt updates the row (attempt_count, status,
-- next_retry_at…). Its partial indexes already fit the dispatcher; the
-- recipient one is empty today (no caller sets recipient_user_id) and costs
-- nothing, so it stays for the future inbox.
ALTER TABLE notifications SET (
    fillfactor = 90,
    autovacuum_vacuum_scale_factor = 0.05,
    autovacuum_analyze_scale_factor = 0.02
);

-- benefit_usages ------------------------------------------------------------------
-- The rating refresh (BenefitUsageRepository.refreshAllyRating) filters
-- `ally_id = ? AND ally_rating IS NOT NULL AND is_active AND status =
-- 'REGISTERED'`. The V224 index was partial on `rated_at IS NOT NULL`:
-- equivalent by CHECK constraint, but the planner does not use constraints
-- to prove a partial index applies, so it was never chosen.
DROP INDEX IF EXISTS idx_benefit_usages_ally_rated;
CREATE INDEX idx_benefit_usages_ally_rated
    ON benefit_usages (ally_id)
    WHERE ally_rating IS NOT NULL AND is_active AND status = 'REGISTERED';

-- Dashboard "latest usages" (DashboardQueries.findRecentUsages):
-- ORDER BY usage_datetime DESC LIMIT n, which had no index.
CREATE INDEX idx_benefit_usages_usage_datetime
    ON benefit_usages (usage_datetime DESC);

-- Duplicates of a UNIQUE constraint's own index ------------------------------------
-- Each one's columns are the leading columns of a UNIQUE index on the same
-- table, which serves the same lookups (and FKs): pure write overhead.
DROP INDEX IF EXISTS idx_user_roles_user_id;                  -- user_roles (user_id, role_id)
DROP INDEX IF EXISTS idx_role_permissions_role_id;            -- role_permissions (role_id, permission_id)
DROP INDEX IF EXISTS idx_states_country_id;                   -- states (country_id, code|name)
DROP INDEX IF EXISTS idx_cities_state_id;                     -- cities (state_id, name)
DROP INDEX IF EXISTS idx_ally_products_ally;                  -- uq_ally_products_ally_product (ally_id, product_id)
DROP INDEX IF EXISTS idx_subsidy_beneficiaries_subsidy;       -- uq_subsidy_beneficiaries (subsidy_id, …)
DROP INDEX IF EXISTS idx_audit_entity_config_key;             -- entity_config (entity_key) UNIQUE
DROP INDEX IF EXISTS idx_report_share_token;                  -- report_share (share_token) UNIQUE
DROP INDEX IF EXISTS idx_campaign_promoters_campaign;         -- uq_campaign_promoters_campaign_promoter (campaign_id, …)
DROP INDEX IF EXISTS idx_commission_retroactive_topup_cuts_promoter_period; -- uq_commission_retroactive_topup_cuts_key
-- member_documents (member_id) is the prefix of (member_id, document_type).
DROP INDEX IF EXISTS idx_member_documents_member;
