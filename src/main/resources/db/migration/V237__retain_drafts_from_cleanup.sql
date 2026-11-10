SET search_path TO app, public;

-- ============================================================================
-- V237: retention of registration drafts (hub ADR 0037 §2).
--
--   1. affiliation_requests and allies gain the "retain from cleanup" flag: a
--      draft held open (negotiation in progress, waiting for information) is
--      skipped by the REGISTRATION_DRAFT_CLEANUP job. The flag only matters
--      while the registration is a draft; on submit it is simply ignored.
--      Audit: who set it, when, and an optional short note. All of it is
--      cleared together when the flag is cleared (CHECK).
--   2. onboarding_policies gains the retention of each kind of draft, counted
--      from its last change: member_draft_retention_days (default 15) and
--      ally_draft_retention_days (default 30, long negotiations). 0 = never
--      clean up that kind. Read by the REGISTRATION_DRAFT_CLEANUP job on every
--      run (admin-only: not exposed in the public policy).
--   3. Granular permissions, one per action:
--        AFFILIATION_REQUEST_DRAFT_RETAIN_ASSISTED  the advisor retains / releases their own assisted affiliation draft
--        AFFILIATION_REQUEST_DRAFT_RETAIN           staff retains / releases any affiliation draft
--        ALLY_DRAFT_RETAIN_ASSISTED                 the advisor retains / releases their own ally draft
--        ALLY_REGISTRATION_RETAIN                   staff retains / releases any ally registration in progress
--      The assisted ones go to ASESOR, ADMINISTRADOR and SYSTEM (ownership is
--      checked in the service); the staff ones to ADMINISTRADOR and SYSTEM.
-- ============================================================================

-- ─── 1. Columns ─────────────────────────────────────────────────────────────

ALTER TABLE affiliation_requests
    ADD COLUMN retain_from_cleanup BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN retain_note         VARCHAR(200),
    ADD COLUMN retained_by         UUID,
    ADD COLUMN retained_at         TIMESTAMPTZ,
    ADD CONSTRAINT chk_affiliation_requests_retain
        CHECK (retain_from_cleanup OR (retain_note IS NULL AND retained_by IS NULL AND retained_at IS NULL));

ALTER TABLE allies
    ADD COLUMN retain_from_cleanup BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN retain_note         VARCHAR(200),
    ADD COLUMN retained_by         UUID,
    ADD COLUMN retained_at         TIMESTAMPTZ,
    ADD CONSTRAINT chk_allies_retain
        CHECK (retain_from_cleanup OR (retain_note IS NULL AND retained_by IS NULL AND retained_at IS NULL));

COMMENT ON COLUMN affiliation_requests.retain_from_cleanup IS
    'Borrador retenido (negociación abierta o a la espera de información): la limpieza de borradores abandonados lo omite';
COMMENT ON COLUMN affiliation_requests.retain_note IS 'Nota opcional del motivo de la retención (máx. 200)';
COMMENT ON COLUMN affiliation_requests.retained_by IS 'UUID del usuario que activó la retención';
COMMENT ON COLUMN affiliation_requests.retained_at IS 'Momento en que se activó la retención';
COMMENT ON COLUMN allies.retain_from_cleanup IS
    'Registro en progreso retenido (negociación abierta o a la espera de información): la limpieza de borradores abandonados lo omite';
COMMENT ON COLUMN allies.retain_note IS 'Nota opcional del motivo de la retención (máx. 200)';
COMMENT ON COLUMN allies.retained_by IS 'UUID del usuario que activó la retención';
COMMENT ON COLUMN allies.retained_at IS 'Momento en que se activó la retención';

-- ─── 2. Draft retention in the onboarding policy ───────────────────────────

ALTER TABLE onboarding_policies
    ADD COLUMN member_draft_retention_days INTEGER NOT NULL DEFAULT 15,
    ADD COLUMN ally_draft_retention_days   INTEGER NOT NULL DEFAULT 30,
    ADD CONSTRAINT chk_onboarding_policies_member_draft_retention CHECK (member_draft_retention_days >= 0),
    ADD CONSTRAINT chk_onboarding_policies_ally_draft_retention   CHECK (ally_draft_retention_days >= 0);

COMMENT ON COLUMN onboarding_policies.member_draft_retention_days IS
    'Días sin cambios tras los cuales se descarta un borrador de afiliación abandonado; 0 = nunca limpiar';
COMMENT ON COLUMN onboarding_policies.ally_draft_retention_days IS
    'Días sin cambios tras los cuales se descarta un borrador de aliado abandonado; 0 = nunca limpiar';

-- ─── 3. Permissions ─────────────────────────────────────────────────────────

INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('AFFILIATION_REQUEST_DRAFT_RETAIN_ASSISTED', 'AFFILIATION_REQUESTS', 'Retener o liberar de la limpieza un borrador de afiliación asistida propio (portal del asesor)'),
    ('AFFILIATION_REQUEST_DRAFT_RETAIN',          'AFFILIATION_REQUESTS', 'Retener o liberar de la limpieza cualquier borrador de afiliación en progreso'),
    ('ALLY_DRAFT_RETAIN_ASSISTED',                'ALLIES',               'Retener o liberar de la limpieza un borrador de aliado propio (portal del asesor)'),
    ('ALLY_REGISTRATION_RETAIN',                  'ALLIES',               'Retener o liberar de la limpieza cualquier registro de aliado en progreso')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
CROSS JOIN permissions p
WHERE (r.name IN ('SYSTEM', 'ADMINISTRADOR', 'ASESOR')
       AND p.name IN ('AFFILIATION_REQUEST_DRAFT_RETAIN_ASSISTED', 'ALLY_DRAFT_RETAIN_ASSISTED'))
   OR (r.name IN ('SYSTEM', 'ADMINISTRADOR')
       AND p.name IN ('AFFILIATION_REQUEST_DRAFT_RETAIN', 'ALLY_REGISTRATION_RETAIN'))
ON CONFLICT (role_id, permission_id) DO NOTHING;

DO $$
BEGIN
    IF (SELECT COUNT(*) FROM permissions
        WHERE name IN ('AFFILIATION_REQUEST_DRAFT_RETAIN_ASSISTED', 'AFFILIATION_REQUEST_DRAFT_RETAIN',
                       'ALLY_DRAFT_RETAIN_ASSISTED', 'ALLY_REGISTRATION_RETAIN')) <> 4 THEN
        RAISE EXCEPTION 'V237: the draft retention permissions were not created (domain missing?)';
    END IF;
    IF (SELECT COUNT(*) FROM role_permissions rp
        JOIN roles r ON r.roles_id = rp.role_id AND r.name IN ('SYSTEM', 'ADMINISTRADOR', 'ASESOR')
        JOIN permissions p ON p.permissions_id = rp.permission_id
         AND p.name IN ('AFFILIATION_REQUEST_DRAFT_RETAIN_ASSISTED', 'AFFILIATION_REQUEST_DRAFT_RETAIN',
                        'ALLY_DRAFT_RETAIN_ASSISTED', 'ALLY_REGISTRATION_RETAIN')) <> 10 THEN
        RAISE EXCEPTION 'V237: the roles did not receive the draft retention permissions';
    END IF;
END $$;
