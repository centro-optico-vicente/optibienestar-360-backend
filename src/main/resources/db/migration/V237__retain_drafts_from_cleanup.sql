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
--      ally_draft_retention_days (default 30, long negotiations), both >= 1.
--      Read by the REGISTRATION_DRAFT_CLEANUP job on every run (admin-only:
--      not exposed in the public policy). Keeping one record is a per-record
--      decision (the retain flag), never a global switch.
--   3. Granular permissions, one per action:
--        AFFILIATION_REQUEST_DRAFT_RETAIN_ADVISOR  the advisor finds and retains / releases an affiliation draft in progress:
--                                                   their own (assisted or from their QR) or a self-registered one nobody is attributed to
--        AFFILIATION_REQUEST_DRAFT_RETAIN           staff retains / releases any affiliation draft
--        ALLY_DRAFT_RETAIN_ADVISOR                 same for ally registrations in progress (captured by them, or self-registered with no advisor)
--        ALLY_REGISTRATION_RETAIN                   staff retains / releases any ally registration in progress
--      The assisted ones go to ASESOR, ADMINISTRADOR and SYSTEM (the scope is
--      checked in the service: another advisor's drafts stay invisible); the staff ones to ADMINISTRADOR and SYSTEM.
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
    'Registro mantenido (negociación abierta o a la espera de información): la limpieza de borradores abandonados lo omite';
COMMENT ON COLUMN affiliation_requests.retain_note IS 'Nota opcional del motivo por el que se mantiene el registro (máx. 200)';
COMMENT ON COLUMN affiliation_requests.retained_by IS 'UUID del usuario que marcó el registro como mantenido';
COMMENT ON COLUMN affiliation_requests.retained_at IS 'Momento en que se marcó el registro como mantenido';
COMMENT ON COLUMN allies.retain_from_cleanup IS
    'Registro en progreso mantenido (negociación abierta o a la espera de información): la limpieza de borradores abandonados lo omite';
COMMENT ON COLUMN allies.retain_note IS 'Nota opcional del motivo por el que se mantiene el registro (máx. 200)';
COMMENT ON COLUMN allies.retained_by IS 'UUID del usuario que marcó el registro como mantenido';
COMMENT ON COLUMN allies.retained_at IS 'Momento en que se marcó el registro como mantenido';

-- ─── 2. Draft retention in the onboarding policy ───────────────────────────

ALTER TABLE onboarding_policies
    ADD COLUMN member_draft_retention_days INTEGER NOT NULL DEFAULT 15,
    ADD COLUMN ally_draft_retention_days   INTEGER NOT NULL DEFAULT 30,
    ADD CONSTRAINT chk_onboarding_policies_member_draft_retention CHECK (member_draft_retention_days >= 1),
    ADD CONSTRAINT chk_onboarding_policies_ally_draft_retention   CHECK (ally_draft_retention_days >= 1);

COMMENT ON COLUMN onboarding_policies.member_draft_retention_days IS
    'Días sin cambios tras los cuales se descarta un borrador de afiliación abandonado';
COMMENT ON COLUMN onboarding_policies.ally_draft_retention_days IS
    'Días sin cambios tras los cuales se descarta un borrador de aliado abandonado';

-- ─── 3. Permissions ─────────────────────────────────────────────────────────

INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('AFFILIATION_REQUEST_DRAFT_RETAIN_ADVISOR', 'AFFILIATION_REQUESTS', 'Encontrar y mantener (o dejar de mantener) un borrador de afiliación en progreso propio o sin asesor, para que la limpieza no lo borre (portal del asesor)'),
    ('AFFILIATION_REQUEST_DRAFT_RETAIN',          'AFFILIATION_REQUESTS', 'Mantener (o dejar de mantener) cualquier borrador de afiliación en progreso para que la limpieza no lo borre'),
    ('ALLY_DRAFT_RETAIN_ADVISOR',                'ALLIES',               'Encontrar y mantener (o dejar de mantener) un registro de aliado en progreso propio o sin asesor, para que la limpieza no lo borre (portal del asesor)'),
    ('ALLY_REGISTRATION_RETAIN',                  'ALLIES',               'Mantener (o dejar de mantener) cualquier registro de aliado en progreso para que la limpieza no lo borre')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
CROSS JOIN permissions p
WHERE (r.name IN ('SYSTEM', 'ADMINISTRADOR', 'ASESOR')
       AND p.name IN ('AFFILIATION_REQUEST_DRAFT_RETAIN_ADVISOR', 'ALLY_DRAFT_RETAIN_ADVISOR'))
   OR (r.name IN ('SYSTEM', 'ADMINISTRADOR')
       AND p.name IN ('AFFILIATION_REQUEST_DRAFT_RETAIN', 'ALLY_REGISTRATION_RETAIN'))
ON CONFLICT (role_id, permission_id) DO NOTHING;

DO $$
BEGIN
    IF (SELECT COUNT(*) FROM permissions
        WHERE name IN ('AFFILIATION_REQUEST_DRAFT_RETAIN_ADVISOR', 'AFFILIATION_REQUEST_DRAFT_RETAIN',
                       'ALLY_DRAFT_RETAIN_ADVISOR', 'ALLY_REGISTRATION_RETAIN')) <> 4 THEN
        RAISE EXCEPTION 'V237: the draft retention permissions were not created (domain missing?)';
    END IF;
    IF (SELECT COUNT(*) FROM role_permissions rp
        JOIN roles r ON r.roles_id = rp.role_id AND r.name IN ('SYSTEM', 'ADMINISTRADOR', 'ASESOR')
        JOIN permissions p ON p.permissions_id = rp.permission_id
         AND p.name IN ('AFFILIATION_REQUEST_DRAFT_RETAIN_ADVISOR', 'AFFILIATION_REQUEST_DRAFT_RETAIN',
                        'ALLY_DRAFT_RETAIN_ADVISOR', 'ALLY_REGISTRATION_RETAIN')) <> 10 THEN
        RAISE EXCEPTION 'V237: the roles did not receive the draft retention permissions';
    END IF;
END $$;
