SET search_path TO app, public;

-- V155: seeds MEMBERSHIP_CHARGE_GENERATION (V153 plan) and adds the
-- daysBeforeDue / daysBeforeGraceEnd / daysBeforeAdvisorNotify knobs the
-- membership-reminder job runners now read from their own `parameters`
-- JSONB instead of a hardcoded constant — merged in with `||`, not
-- overwritten, so any other key already on those rows survives.

INSERT INTO scheduled_jobs (code, display_name, description, cron_expression, timezone, parameters)
VALUES (
    'MEMBERSHIP_CHARGE_GENERATION',
    'Generación de cargos de membresía',
    'Crea el cargo PENDING del próximo mes a cobrar para cada membresía ACTIVE/SUSPENDED, una vez que la fecha de hoy entra en la ventana daysBeforeDue de su fecha de cobro programada.',
    '0 0 2 * * *',
    'America/Caracas',
    '{"daysBeforeDue": 3}'::jsonb
);

UPDATE scheduled_jobs
SET parameters = parameters || '{"daysBeforeDue": 3}'::jsonb
WHERE code = 'MEMBERSHIP_DUE_SOON';

UPDATE scheduled_jobs
SET parameters = parameters || '{"daysBeforeGraceEnd": 3, "daysBeforeAdvisorNotify": 3}'::jsonb
WHERE code = 'MEMBERSHIP_GRACE';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM scheduled_jobs
        WHERE code = 'MEMBERSHIP_CHARGE_GENERATION'
          AND parameters ->> 'daysBeforeDue' = '3'
    ) THEN
        RAISE EXCEPTION 'V155: MEMBERSHIP_CHARGE_GENERATION was not seeded correctly';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM scheduled_jobs
        WHERE code = 'MEMBERSHIP_DUE_SOON'
          AND parameters ->> 'daysBeforeDue' = '3'
    ) THEN
        RAISE EXCEPTION 'V155: MEMBERSHIP_DUE_SOON.parameters.daysBeforeDue merge did not apply';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM scheduled_jobs
        WHERE code = 'MEMBERSHIP_GRACE'
          AND parameters ->> 'daysBeforeGraceEnd' = '3'
          AND parameters ->> 'daysBeforeAdvisorNotify' = '3'
    ) THEN
        RAISE EXCEPTION 'V155: MEMBERSHIP_GRACE.parameters merge did not apply';
    END IF;
END $$;
