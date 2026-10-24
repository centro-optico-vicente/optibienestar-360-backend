-- ====================================================
-- V192__terms_versions_is_mandatory.sql
--
-- Whether a T&C version must be accepted before using the platform, or can
-- be postponed ("recordar más tarde") — configurable per version instead of
-- hardcoded by type.
--   - is_mandatory = TRUE : the acceptance modal can't be dismissed.
--   - is_mandatory = FALSE: the user may postpone it; meanwhile the module of
--     that contract stays locked (advisor / ally self-service answer 403
--     terms.pending).
-- It is policy, not contract text: unlike the rest of the row it may be
-- changed on a version that is already vigente (no re-acceptance needed).
--
-- Backfill: AFILIADO versions mandatory (membership agreement every user
-- signs), ASESOR / ALIADO optional — the behavior the app already had.
-- ====================================================

SET search_path TO app, public;

ALTER TABLE terms_versions
    ADD COLUMN is_mandatory BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE terms_versions SET is_mandatory = TRUE WHERE term_type = 'AFILIADO';

COMMENT ON COLUMN terms_versions.is_mandatory IS
    'TRUE = debe aceptarse para usar la plataforma; FALSE = se puede posponer (bloquea solo el módulo del contrato)';
