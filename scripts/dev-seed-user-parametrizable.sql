-- ────────────────────────────────────────────────────────────────────────────
-- Creates a manual, parametrizable user with its person, default role and a
-- link to every role except SYSTEM.
-- ────────────────────────────────────────────────────────────────────────────
-- After the V16 refactor, demographic data (full_name, document, phone,
-- locale) lives in `persons`; `users` only keeps auth fields and references
-- persons via person_id (NOT NULL UNIQUE). Hence the person is inserted first.
--
-- Parameters via SET LOCAL / current_setting() (standard SQL — runs in
-- DBeaver, pgAdmin, psql, etc.). SET LOCAL scopes the values to THIS
-- transaction: they are discarded on COMMIT, not left attached to the
-- connection. The insert is also atomic (all or nothing).
--
-- Run the whole block (BEGIN..COMMIT) in a single execution.
-- ────────────────────────────────────────────────────────────────────────────

BEGIN;

-- ── Parameters (edit here) ──────────────────────────────────────────────────
SET LOCAL app.p_first_name            = 'OptiSalud';
SET LOCAL app.p_last_name             = 'Demo';
SET LOCAL app.p_document_type         = 'V';
SET LOCAL app.p_document_number       = '3';
SET LOCAL app.p_email                 = 'optisalud@gmail.com';
-- Plaintext password; hashed with app.bcrypt_hash() (BCrypt, see V20).
SET LOCAL app.p_password              = 'optisalud';
SET LOCAL app.p_default_role          = 'ADMINISTRADOR';
SET LOCAL app.p_status                = 'ACTIVE';
SET LOCAL app.p_password_never_expires = 'false';   -- 'true' / 'false'
-- ─────────────────────────────────────────────────────────────────────────────

SET LOCAL search_path TO app, public;

WITH new_person AS (
    -- persons requires first_name + last_name NOT NULL.
    INSERT INTO persons (
        first_name,
        last_name,
        document_type,
        document_number,
        email
    )
    VALUES (
        current_setting('app.p_first_name'),
        current_setting('app.p_last_name'),
        current_setting('app.p_document_type'),
        current_setting('app.p_document_number'),
        current_setting('app.p_email')
    )
    RETURNING persons_id
),
new_user AS (
    INSERT INTO users (
        person_id,
        email,
        password_hash,
        default_role_id,
        status,
        password_never_expires
    )
    SELECT
        np.persons_id,
        current_setting('app.p_email'),
        app.bcrypt_hash(current_setting('app.p_password')),      -- BCrypt $2a$ strength 12
        r.roles_id,                                              -- default_role_id
        current_setting('app.p_status'),
        current_setting('app.p_password_never_expires')::boolean
    FROM new_person np
    CROSS JOIN roles r
    WHERE r.name = current_setting('app.p_default_role')
    RETURNING users_id, password_hash
),
roles_link AS (
    -- Link every role except SYSTEM (default role included)
    INSERT INTO user_roles (user_id, role_id)
    SELECT nu.users_id, r.roles_id
    FROM new_user nu
    CROSS JOIN roles r
    WHERE r.name <> 'SYSTEM'
    RETURNING 1
)
-- Record the hash in history to prevent immediate reuse
INSERT INTO user_password_history (user_id, password_hash)
SELECT users_id, password_hash FROM new_user;

COMMIT;
