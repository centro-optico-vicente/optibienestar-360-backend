-- ====================================================
-- V214__ally_opening_hours.sql
--
-- Structured weekly opening hours of an ally (hub ADR 0031 §7). Both final
-- agreement forms ask for them: "Días de Consulta / Horario de Atención"
-- (B2P) and "Horario de Atención" (B2B). One row per time range; a day may
-- have several ranges (e.g. 08:00–12:00 and 14:00–18:00). Overlapping ranges
-- on the same day are rejected by AllyOpeningHoursService. The whole week is
-- replaced at once, so rows are physically deleted on replace.
--
-- day_of_week follows ISO-8601 (1 = Monday … 7 = Sunday, java.time.DayOfWeek).
-- Times are wall-clock times in the business zone (America/Caracas, ADR 0010).
--
-- Permissions (granular, one per action):
--   ALLY_OPENING_HOURS_UPDATE      staff, any ally (granted with ALLY_UPDATE)
--   ALLY_OPENING_HOURS_UPDATE_OWN  ally portal; the service also requires an
--                                  active OWNER membership on that ally
-- Reading needs no new permission: ALLY_VIEW_ALL (admin), an active
-- membership (portal) and the public detail of a published ally.
-- ====================================================

CREATE TABLE ally_opening_hours
(
    ally_opening_hours_id BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                  UUID        NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    ally_id               BIGINT      NOT NULL REFERENCES allies (allies_id) ON DELETE CASCADE,
    day_of_week           SMALLINT    NOT NULL,
    opens_at              TIME        NOT NULL,
    closes_at             TIME        NOT NULL,

    is_active             BOOLEAN     NOT NULL DEFAULT TRUE,
    status                VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID,

    CONSTRAINT ck_ally_opening_hours_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_ally_opening_hours_range CHECK (closes_at > opens_at),
    CONSTRAINT ck_ally_opening_hours_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT uq_ally_opening_hours_start UNIQUE (ally_id, day_of_week, opens_at)
);

CREATE INDEX idx_ally_opening_hours_day ON ally_opening_hours (day_of_week, opens_at, closes_at);

CREATE TRIGGER trg_ally_opening_hours_updated_at
    BEFORE UPDATE ON ally_opening_hours
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE ally_opening_hours IS 'Horario semanal de atención del aliado: un tramo por fila, varios por día (ADR 0031 §7)';
COMMENT ON COLUMN ally_opening_hours.day_of_week IS 'Día ISO-8601: 1 = lunes … 7 = domingo';
COMMENT ON COLUMN ally_opening_hours.opens_at IS 'Hora de apertura (hora local de America/Caracas)';
COMMENT ON COLUMN ally_opening_hours.closes_at IS 'Hora de cierre (hora local de America/Caracas), siempre mayor que la apertura';

-- ─── Permissions ────────────────────────────────────────────────────────────
INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('ALLY_OPENING_HOURS_UPDATE',     'ALLIES', 'Editar el horario de atención de cualquier aliado'),
    ('ALLY_OPENING_HOURS_UPDATE_OWN', 'ALLIES', 'Editar el horario de atención del aliado propio (como propietario)')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code
ON CONFLICT (name) DO NOTHING;

-- Staff: every role that can already edit an ally.
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, np.permissions_id
FROM permissions ep
JOIN role_permissions rp ON rp.permission_id = ep.permissions_id
JOIN permissions np ON np.name = 'ALLY_OPENING_HOURS_UPDATE'
WHERE ep.name = 'ALLY_UPDATE'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Ally portal.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ALIADO'
  AND p.name = 'ALLY_OPENING_HOURS_UPDATE_OWN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

DO $$
BEGIN
    IF (SELECT COUNT(*) FROM permissions WHERE name LIKE 'ALLY_OPENING_HOURS_%') <> 2 THEN
        RAISE EXCEPTION 'V214: the two ALLY_OPENING_HOURS_* permissions were not created';
    END IF;
END $$;
