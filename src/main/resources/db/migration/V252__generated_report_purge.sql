SET search_path TO app, public;

-- ============================================================================
-- V252: purge generated reports from R2 after a system-wide retention.
--
-- Every generated document (record cards, reports) is archived to R2 under
-- temporary/report_audit_log/… with an attached_files row (visibility
-- TEMPORARY, category GENERATED_REPORT) linked from
-- report_audit_log.attached_file_id. Nothing ever removed them.
--
-- GENERATED_REPORT_PURGE deletes the R2 object once it is older than
-- system_configs.generated_report_retention_days (default 30, editable from the
-- system settings) and MARKS its attached_files row (is_active = false,
-- status = 'PURGED'); no row is deleted. report_audit_log keeps its row and
-- its link to the file, so the audit trail still says which document was
-- generated, by whom and when; only the copy is gone.
--
-- Seeded DISABLED: deleting from R2 cannot be undone, so it runs only once
-- someone enables it from the scheduled jobs admin.
--
-- Volume: one row per generated document. The partial index holds only the
-- still-stored temporary files, ordered by upload time, which is exactly what
-- the job scans (oldest first, in batches); purged rows leave the index.
-- ============================================================================

ALTER TABLE system_configs
	ADD COLUMN generated_report_retention_days INT NOT NULL DEFAULT 30
		CONSTRAINT chk_system_configs_generated_report_retention_days CHECK (generated_report_retention_days > 0);

COMMENT ON COLUMN system_configs.generated_report_retention_days IS
	'Days a generated report stays stored in R2 before GENERATED_REPORT_PURGE deletes it (the audit row stays)';

CREATE INDEX idx_attached_files_temporary_stored
	ON attached_files (uploaded_at, attached_files_id)
	WHERE visibility = 'TEMPORARY' AND is_active;

INSERT INTO scheduled_jobs (code, display_name, description, cron_expression, timezone, parameters, enabled)
VALUES (
    'GENERATED_REPORT_PURGE',
    'Depuración de reportes generados',
    'Borra de R2 los reportes y fichas generados (archivos temporales) con más días que la retención configurada en el sistema, y los marca como depurados. La auditoría de reportes se conserva; solo se elimina la copia del archivo.',
    '0 30 4 * * *',
    'America/Caracas',
    '{}'::jsonb,
    false
);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM scheduled_jobs WHERE code = 'GENERATED_REPORT_PURGE' AND NOT enabled) THEN
        RAISE EXCEPTION 'V252: GENERATED_REPORT_PURGE was not seeded disabled';
    END IF;
END $$;
