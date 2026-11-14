SET search_path TO app, public;

-- ============================================================================
-- V248: retire the legacy user_sessions_log table.
--
-- login_audit_log already tracks every session (session_status + CHECK, partial
-- indexes on ACTIVE, LoginSessionSweepJob marking EXPIRED) and is what
-- JwtAuthenticationFilter reads. user_sessions_log duplicated a subset of it,
-- was never read, never marked expired sessions and lost the session after a
-- refresh (it looked rows up by the rotating access-token jti).
--
-- Column check before dropping — nothing is lost:
--   user_id, jti, ip_address, user_agent, logout_reason → same columns
--   login_locale → locale · login_at → attempted_at · logout_at → logged_out_at
-- Only its CHECK on logout_reason had no equivalent; it moves here.
--
-- login_audit_log grows with every login attempt, so it also gets a lower
-- fillfactor: each successful login row is updated right after insert
-- (attachJti) and again on logout/sweep, and the free space lets those updates
-- stay on the same page (HOT) instead of touching every index.
-- ============================================================================

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM login_audit_log
               WHERE logout_reason IS NOT NULL
                 AND logout_reason NOT IN ('user_logout', 'role_switch', 'password_reset',
                                           'password_change', 'token_expired', 'forced')) THEN
        RAISE EXCEPTION 'V248: login_audit_log has a logout_reason outside the allowed list';
    END IF;
END $$;

ALTER TABLE login_audit_log
    ADD CONSTRAINT chk_login_audit_logout_reason CHECK (logout_reason IN (
        'user_logout', 'role_switch', 'password_reset', 'password_change', 'token_expired', 'forced'));

ALTER TABLE login_audit_log SET (fillfactor = 90);

DROP TABLE user_sessions_log;

-- The (still disabled) retention job no longer has a session log to purge.
UPDATE scheduled_jobs
SET parameters  = parameters - 'sessionLogDays',
    description = 'Borra filas antiguas de logs operativos: notificaciones ya enviadas o descartadas, historial de ejecuciones de jobs y enlaces de descarga vencidos o revocados. No toca la auditoría. Días de retención por tabla en los parámetros (mínimo 7).'
WHERE code = 'OPERATIONAL_LOG_RETENTION';
