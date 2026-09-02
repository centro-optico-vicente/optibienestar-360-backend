SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V83: split the single data_change_audit_mode (V68) into one AuditMode per
-- action, plus a new global override for capture_before_after — matching the
-- granularity that already exists per entity in entity_config (auditCreate/
-- auditUpdate/auditDelete/captureBeforeAfter, V60/V80).
--
-- Before: one mode governed create+update+delete together (isEnabled() short-
-- circuited on the whole action set before even checking which action it was).
-- After: audit_create_mode / audit_update_mode / audit_delete_mode can each be
-- forced independently — e.g. force-disable deletes globally without also
-- touching creates/updates. capture_before_after_mode is a genuinely new
-- override: previously there was no way to force before/after snapshots
-- on/off system-wide, only per entity.
--
-- report_audit_mode is untouched — report generation is a single action, it
-- already has the maximum useful granularity.
-- ────────────────────────────────────────────────────────────────────────────

ALTER TABLE system_configs
    ADD COLUMN audit_create_mode         VARCHAR(20) NOT NULL DEFAULT 'PER_ENTITY'
        CONSTRAINT chk_system_configs_audit_create_mode
            CHECK (audit_create_mode IN ('PER_ENTITY', 'FORCE_ENABLED', 'FORCE_DISABLED')),
    ADD COLUMN audit_update_mode         VARCHAR(20) NOT NULL DEFAULT 'PER_ENTITY'
        CONSTRAINT chk_system_configs_audit_update_mode
            CHECK (audit_update_mode IN ('PER_ENTITY', 'FORCE_ENABLED', 'FORCE_DISABLED')),
    ADD COLUMN audit_delete_mode         VARCHAR(20) NOT NULL DEFAULT 'PER_ENTITY'
        CONSTRAINT chk_system_configs_audit_delete_mode
            CHECK (audit_delete_mode IN ('PER_ENTITY', 'FORCE_ENABLED', 'FORCE_DISABLED')),
    ADD COLUMN capture_before_after_mode VARCHAR(20) NOT NULL DEFAULT 'PER_ENTITY'
        CONSTRAINT chk_system_configs_capture_before_after_mode
            CHECK (capture_before_after_mode IN ('PER_ENTITY', 'FORCE_ENABLED', 'FORCE_DISABLED'));

-- Preserve existing installs' behavior: whatever data_change_audit_mode was
-- set to now applies identically to all three actions.
UPDATE system_configs
SET audit_create_mode = data_change_audit_mode,
    audit_update_mode = data_change_audit_mode,
    audit_delete_mode = data_change_audit_mode;

ALTER TABLE system_configs
    DROP CONSTRAINT chk_system_configs_data_change_audit_mode,
    DROP COLUMN data_change_audit_mode;
