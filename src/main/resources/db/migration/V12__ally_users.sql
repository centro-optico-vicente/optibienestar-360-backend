SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V12: ally_users — membresía N:M usuario ↔ aliado.
-- Un User (cuenta de login) puede pertenecer a múltiples Allies; un Ally
-- puede tener múltiples Users que lo operan. Soporta los casos:
--   - Cadena de farmacias con N sucursales: 1 User dueño con membresía OWNER
--     en cada uno de los N Allies; recepcionistas con membresía STAFF en su
--     sucursal.
--   - Clínica con 1 admin (OWNER), 2 recepcionistas (STAFF), 1 contador
--     externo que sólo ve reportes (VIEWER).
--
-- Roles intra-aliado (`ally_role`):
--   - OWNER  → puede editar perfil del ally, proponer/retirar services,
--              firmar agreements, gestionar otros ally_users.
--   - STAFF  → ops del día a día (validar afiliados en el mostrador, registrar
--              uso de beneficios).
--   - VIEWER → solo lectura (reportes, dashboard).
-- Estos roles SON ortogonales al rol global del usuario en `user_roles`
-- (típicamente ALIADO para todos). El service layer combina ambos:
-- el usuario debe tener (a) el permiso global ALLY_* requerido Y
-- (b) una membresía activa en el ally afectado con `ally_role` suficiente.
--
-- `is_primary`: una membresía por ally se marca como contacto principal —
-- esto reemplaza la columna `allies.manager_user_id` introducida en V11
-- (drop al final de esta migración para mantener una sola fuente de verdad).
-- Partial unique index garantiza que NO haya dos primaries activos por ally.
-- ────────────────────────────────────────────────────────────────────────────

CREATE TABLE ally_users
(
    ally_users_id    BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid             UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    ally_id          BIGINT       NOT NULL REFERENCES allies (allies_id) ON DELETE CASCADE,
    user_id          BIGINT       NOT NULL REFERENCES users (users_id),

    ally_role        VARCHAR(20)  NOT NULL DEFAULT 'STAFF'
                        CHECK (ally_role IN ('OWNER', 'STAFF', 'VIEWER')),
    is_primary       BOOLEAN      NOT NULL DEFAULT FALSE,
    joined_at        DATE,

    -- Audit + soft-delete (BaseEntity-style)
    is_active        BOOLEAN      NOT NULL DEFAULT TRUE,
    status           VARCHAR(50),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_by       UUID,

    -- Un usuario no puede aparecer dos veces en el mismo ally (incluso entre
    -- activos e inactivos — el flujo de readmisión actualiza is_active del
    -- registro existente en vez de crear uno nuevo).
    UNIQUE (ally_id, user_id),

    -- Coherencia: si es primary, debe ser OWNER. Un STAFF/VIEWER nunca es
    -- contacto principal — eso confundiría reportes y comunicaciones.
    CONSTRAINT chk_ally_users_primary_is_owner CHECK (
        is_primary = FALSE OR ally_role = 'OWNER'
    )
);

-- "¿En qué allies está este usuario?" — sirve para construir el menú lateral
-- de selector de ally cuando el user pertenece a varios.
CREATE INDEX idx_ally_users_user ON ally_users (user_id);

-- Sólo UNA membresía primary activa por ally. Partial unique index permite:
--   - múltiples primaries inactivos (historial de cambios de dueño).
--   - múltiples no-primary activos (los STAFF/VIEWER).
CREATE UNIQUE INDEX uniq_ally_users_one_primary_per_ally
    ON ally_users (ally_id)
    WHERE is_primary = TRUE AND is_active = TRUE;

CREATE TRIGGER trg_ally_users_updated_at
    BEFORE UPDATE ON ally_users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ─── Drop allies.manager_user_id ────────────────────────────────────────────
-- Reemplazado por ally_users(is_primary = TRUE AND is_active = TRUE).
-- Mantener ambos sería doble fuente de verdad — `manager_user_id` apuntaría
-- a una row de `ally_users` con `is_primary=true`, requiriendo trigger
-- sincronizador o constraint complejo. La normalización gana.
-- Postgres dropea automáticamente el índice idx_allies_manager dependiente.
ALTER TABLE allies DROP COLUMN manager_user_id;
