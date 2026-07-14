SET search_path TO app, public;

-- Creates the OptiBienestar demo user (cédula V-3, password 'optibienestar360') with
-- default role ADMINISTRADOR and links it to every role except SYSTEM.
--
-- After the V16 refactor, demographic data (full_name, document, phone,
-- locale) lives in `persons`; `users` only keeps auth fields and references
-- persons via person_id (NOT NULL UNIQUE). Hence the person is inserted
-- first, then the user.
--
-- password_hash: app.bcrypt_hash('optibienestar360') yields a BCrypt $2a$ strength-12
-- hash, compatible with Spring's BCryptPasswordEncoder(12) (see V20__bcrypt_helper.sql).
WITH new_person AS (
    -- persons requires first_name + last_name NOT NULL: 'OptiBienestar' is split
    -- into a placeholder first/last name (same as the 'System'/'Administrador' seeds).
    INSERT INTO persons (
        first_name,
        last_name,
        document_type,
        document_number,
        email
    )
    VALUES (
        'OptiBienestar',
        'Demo',
        'V', '3',
        'optibienestar360@gmail.com'
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
        'optibienestar360@gmail.com',
        app.bcrypt_hash('optibienestar360'),   -- BCrypt $2a$ strength 12 (see V20)
        r.roles_id,            -- default_role_id = ADMINISTRADOR
        'ACTIVE',
        FALSE
    FROM new_person np
    CROSS JOIN roles r
    WHERE r.name = 'ADMINISTRADOR'
    RETURNING users_id, password_hash
),
roles_link AS (
    -- Link every role except SYSTEM (ADMINISTRADOR included)
    INSERT INTO user_roles (user_id, role_id)
    SELECT nu.users_id, r.roles_id
    FROM new_user nu
    CROSS JOIN roles r
    WHERE r.name <> 'SYSTEM'
    RETURNING 1
)
-- Record the hash in history to prevent immediate reuse (same as the seed)
INSERT INTO user_password_history (user_id, password_hash)
SELECT users_id, password_hash FROM new_user;
