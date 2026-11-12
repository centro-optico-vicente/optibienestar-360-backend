SET search_path TO app, public;

-- ============================================================================
-- V244: seed the OPERATIONAL_LOG_RETENTION job — DISABLED.
--
-- Nothing purges the log tables today; they grow forever. This job purges only
-- OPERATIONAL logs (OperationalLogRetentionService): finished notifications,
-- finished job runs, the legacy user_sessions_log (login_audit_log stays) and
-- expired/revoked download links. The audit trail (data_change_audit_log,
-- login_audit_log, report_audit_log, medical_record_access_log, review logs)
-- is out of scope: keeping it is a compliance decision.
--
-- Seeded with enabled = false: deleting data is a policy decision, so it only
-- runs once someone enables it from the scheduled jobs admin, after reviewing
-- the retention below (days per table, editable in `parameters`; any value
-- below 7 is raised to 7).
-- ============================================================================

INSERT INTO scheduled_jobs (code, display_name, description, cron_expression, timezone, parameters, enabled)
VALUES (
    'OPERATIONAL_LOG_RETENTION',
    'Retención de logs operativos',
    'Borra filas antiguas de logs operativos: notificaciones ya enviadas o descartadas, historial de ejecuciones de jobs, el log de sesiones legado y enlaces de descarga vencidos o revocados. No toca la auditoría. Días de retención por tabla en los parámetros (mínimo 7).',
    '0 45 3 * * *',
    'America/Caracas',
    '{"notificationsDays": 90, "jobRunsDays": 90, "sessionLogDays": 180, "downloadLinksDays": 30}'::jsonb,
    false
);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM scheduled_jobs WHERE code = 'OPERATIONAL_LOG_RETENTION' AND NOT enabled) THEN
        RAISE EXCEPTION 'V244: OPERATIONAL_LOG_RETENTION was not seeded disabled';
    END IF;
END $$;
