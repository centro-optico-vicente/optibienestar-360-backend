SET search_path TO app, public;

-- security_policies: configurable auth parameters — no external FKs
CREATE TABLE security_policies
(
    security_policies_id     BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                     UUID        NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    max_login_attempts       INT         NOT NULL DEFAULT 5,
    lockout_duration_minutes INT         NOT NULL DEFAULT 30,
    days_password_expires    INT         NOT NULL DEFAULT 90,
    password_history_count   INT         NOT NULL DEFAULT 5,
    max_concurrent_sessions  INT         NOT NULL DEFAULT 3,
    is_active                BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by               UUID,
    updated_by               UUID
);

CREATE TRIGGER trg_security_policies_updated_at
    BEFORE UPDATE ON security_policies
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- roles: named authority groups — no external FKs
CREATE TABLE roles
(
    roles_id    BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid        UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    name        VARCHAR(50)  NOT NULL UNIQUE,
    description VARCHAR(200),
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by  UUID,
    updated_by  UUID
);

CREATE INDEX idx_roles_is_active ON roles (is_active) WHERE is_active;

CREATE TRIGGER trg_roles_updated_at
    BEFORE UPDATE ON roles
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- permission_domains: UI modules grouping permissions in the admin panel
-- with Spanish name + lucide icon + display order. Seeded here because
-- permissions.domain_id is a NOT NULL FK to this table — domains must
-- exist before V6 inserts the permission catalog.
CREATE TABLE permission_domains
(
    permission_domains_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                  UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    code                  VARCHAR(50)  NOT NULL UNIQUE,         -- 'MEMBERS' (technical, code-bound)
    name                  VARCHAR(100) NOT NULL UNIQUE,         -- 'Afiliados' (Spanish UI name — domain data, not code)
    icon                  VARCHAR(60),                          -- 'i-lucide-users'
    description           VARCHAR(255),                         -- longer help text for the UI (tooltip / subtitle)
    display_order         INT          NOT NULL DEFAULT 100,
    is_active             BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID
);

CREATE INDEX idx_permission_domains_is_active     ON permission_domains (is_active) WHERE is_active;
CREATE INDEX idx_permission_domains_display_order ON permission_domains (display_order) WHERE is_active;

CREATE TRIGGER trg_permission_domains_updated_at
    BEFORE UPDATE ON permission_domains
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Seed of the 10 UI domains. The `code` values are the strings that V6
-- joins against when inserting the permission catalog.
INSERT INTO permission_domains (code, name, icon, description, display_order)
VALUES
    ('USERS',       'Usuarios',   'i-lucide-user-cog',       'Gestión de cuentas y roles de usuarios del sistema',      10),
    ('MEMBERS',     'Afiliados',  'i-lucide-users',          'Afiliados al programa, documentos e historial médico',    20),
    ('ALLIES',      'Aliados',    'i-lucide-handshake',      'Aliados comerciales, acuerdos y validación de afiliados', 30),
    ('PLANS',       'Planes',     'i-lucide-clipboard-list', 'Configuración de planes de membresía',                    40),
    ('MEMBERSHIPS', 'Membresías', 'i-lucide-badge-check',    'Membresías activas, cancelaciones y reactivaciones',      50),
    ('PAYMENTS',    'Pagos',      'i-lucide-credit-card',    'Registro, aprobación y consulta de pagos',                60),
    ('PROMOTERS',   'Promotores', 'i-lucide-megaphone',      'Promotores y red de ventas',                              70),
    ('COMMISSIONS', 'Comisiones', 'i-lucide-percent',        'Comisiones de promotores y ciclos de pago',               80),
    ('REFERRALS',   'Referidos',  'i-lucide-share-2',        'Códigos de referido y seguimiento',                       90),
    ('REPORTS',     'Reportes',   'i-lucide-bar-chart-3',    'Panel de control y exportación de reportes',             100);


-- permissions: granular action rights — depends on permission_domains
CREATE TABLE permissions
(
    permissions_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid           UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    name           VARCHAR(100) NOT NULL UNIQUE,
    domain_id      BIGINT       NOT NULL REFERENCES permission_domains (permission_domains_id),
    description    VARCHAR(200),
    is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID
);

CREATE INDEX idx_permissions_domain_id ON permissions (domain_id);
CREATE INDEX idx_permissions_is_active ON permissions (is_active) WHERE is_active;

CREATE TRIGGER trg_permissions_updated_at
    BEFORE UPDATE ON permissions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- users: application accounts — depends on roles (default_role_id)
CREATE TABLE users
(
    users_id                  BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                      UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    email                     CITEXT       NOT NULL UNIQUE,
    password_hash             VARCHAR(255) NOT NULL,
    full_name                 VARCHAR(200) NOT NULL,
    document_type             VARCHAR(2)   CHECK (document_type IN ('V', 'E')),
    document_number           VARCHAR(20),
    phone                     VARCHAR(30),
    default_role_id           BIGINT       REFERENCES roles (roles_id),
    failed_login_attempts     INT          NOT NULL DEFAULT 0,
    locked_until              TIMESTAMPTZ,
    password_reset_token      VARCHAR(255),
    password_reset_expires_at TIMESTAMPTZ,
    last_login_at             TIMESTAMPTZ,
    password_never_expires    BOOLEAN      NOT NULL DEFAULT FALSE,
    is_active                 BOOLEAN      NOT NULL DEFAULT TRUE,
    status                    VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE'
                                  CHECK (status IN ('ACTIVE', 'SUSPENDED', 'LOCKED')),
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by                UUID,
    updated_by                UUID,
    UNIQUE (document_type, document_number),
    CONSTRAINT chk_users_document CHECK (
        (document_type IS NULL AND document_number IS NULL) OR
        (document_type IS NOT NULL AND document_number IS NOT NULL)
    )
);

CREATE INDEX idx_users_is_active    ON users (is_active) WHERE is_active;
CREATE INDEX idx_users_status       ON users (status);
CREATE INDEX idx_users_locked_until ON users (locked_until) WHERE locked_until IS NOT NULL;
CREATE INDEX idx_users_default_role ON users (default_role_id);

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- user_password_history: prevents password reuse — insert-only, no updated_at
CREATE TABLE user_password_history
(
    user_password_history_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id                  BIGINT       NOT NULL REFERENCES users (users_id),
    password_hash            VARCHAR(255) NOT NULL,
    expires_at               TIMESTAMPTZ,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_password_history_user_id ON user_password_history (user_id, created_at DESC);


-- user_roles: pivot users <-> roles
CREATE TABLE user_roles
(
    user_roles_id BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       BIGINT      NOT NULL REFERENCES users (users_id),
    role_id       BIGINT      NOT NULL REFERENCES roles (roles_id),
    assigned_by   UUID,
    expires_at    TIMESTAMPTZ,
    is_active     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by    UUID,
    updated_by    UUID,
    UNIQUE (user_id, role_id)
);

CREATE INDEX idx_user_roles_user_id ON user_roles (user_id);
CREATE INDEX idx_user_roles_role_id ON user_roles (role_id);

CREATE TRIGGER trg_user_roles_updated_at
    BEFORE UPDATE ON user_roles
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- role_permissions: pivot roles <-> permissions
CREATE TABLE role_permissions
(
    role_permissions_id BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    role_id             BIGINT      NOT NULL REFERENCES roles (roles_id),
    permission_id       BIGINT      NOT NULL REFERENCES permissions (permissions_id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    UNIQUE (role_id, permission_id)
);

CREATE INDEX idx_role_permissions_role_id       ON role_permissions (role_id);
CREATE INDEX idx_role_permissions_permission_id ON role_permissions (permission_id);

CREATE TRIGGER trg_role_permissions_updated_at
    BEFORE UPDATE ON role_permissions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- user_sessions_log: login/logout audit trail — insert-only, no updated_at
CREATE TABLE user_sessions_log
(
    user_sessions_log_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id              BIGINT       NOT NULL REFERENCES users (users_id),
    jti                  VARCHAR(36)  NOT NULL,
    ip_address           INET         NOT NULL,
    user_agent           VARCHAR(500),
    login_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    logout_at            TIMESTAMPTZ,
    logout_reason        VARCHAR(50)  CHECK (logout_reason IN ('user_logout', 'token_expired', 'forced')),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_sessions_log_user_id ON user_sessions_log (user_id);
CREATE INDEX idx_user_sessions_log_jti     ON user_sessions_log (jti);
