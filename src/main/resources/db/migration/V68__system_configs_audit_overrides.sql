SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V68: global audit overrides on the system_configs singleton (spec
-- 16-audit.md §Decisiones). These sit ABOVE the per-entity control in
-- audit_entity_config (V60):
--
--   data_change_audit_mode / report_audit_mode — one of:
--     PER_ENTITY     (default) — respect audit_entity_config row by row, as today.
--     FORCE_ENABLED  — audit every entity regardless of audit_entity_config
--                      (even a row with enabled=false or its action flag off).
--     FORCE_DISABLED — never audit any entity, regardless of audit_entity_config
--                      (emergency kill switch — perf incident, bulk load, etc.).
--
--   login_audit_enabled — plain on/off (default TRUE). login_audit_log has no
--   per-entity equivalent to override, so it only needs a simple toggle, not
--   the 3-way mode above.
--
-- Fail-safe posture unchanged: if this row can't be resolved, callers must
-- fall back to PER_ENTITY / enabled=true and never block the business
-- operation (same rule as audit_entity_config, spec §Decisiones 6).
-- ────────────────────────────────────────────────────────────────────────────

ALTER TABLE system_configs
    ADD COLUMN data_change_audit_mode VARCHAR(20) NOT NULL DEFAULT 'PER_ENTITY'
        CONSTRAINT chk_system_configs_data_change_audit_mode
            CHECK (data_change_audit_mode IN ('PER_ENTITY', 'FORCE_ENABLED', 'FORCE_DISABLED')),
    ADD COLUMN report_audit_mode      VARCHAR(20) NOT NULL DEFAULT 'PER_ENTITY'
        CONSTRAINT chk_system_configs_report_audit_mode
            CHECK (report_audit_mode IN ('PER_ENTITY', 'FORCE_ENABLED', 'FORCE_DISABLED')),
    ADD COLUMN login_audit_enabled    BOOLEAN     NOT NULL DEFAULT TRUE;
