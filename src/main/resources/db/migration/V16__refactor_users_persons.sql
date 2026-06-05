SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V16: relocate demographic data from `users` into the new `persons` hub.
-- ────────────────────────────────────────────────────────────────────────────
-- After this migration:
--   - `users` keeps ONLY auth/security fields (email, password, status,
--     failed_login_attempts, lockout, password_reset, locale → persons, ...)
--   - All demographic data lives in `persons` (full_name, document, phone,
--     locale, contact, address).
--   - `users.person_id` is a NOT NULL UNIQUE FK to persons (1:1).
--
-- Backfill strategy:
--   - For each existing user row, create a corresponding persons row.
--   - Single-word seed names ('System', 'Administrador') get hardcoded splits
--     into first_name/last_name (CASE below). Other rows use a naïve split
--     (first whitespace-separated token = first_name, remainder = last_name).
--   - person_id is added nullable first, populated, then promoted to NOT NULL.
-- ────────────────────────────────────────────────────────────────────────────

-- Step 1: backfill persons from existing users.
INSERT INTO persons (
    first_name,
    last_name,
    document_type,
    document_number,
    phone,
    email,
    locale
)
SELECT
    -- first_name
    CASE u.full_name
        WHEN 'System'        THEN 'System'
        WHEN 'Administrador' THEN 'Administrador'
        ELSE split_part(u.full_name, ' ', 1)
    END,
    -- last_name (NOT NULL — fall back to first_name when single-word)
    CASE u.full_name
        WHEN 'System'        THEN 'Account'
        WHEN 'Administrador' THEN 'Principal'
        ELSE COALESCE(
            NULLIF(TRIM(SUBSTRING(u.full_name FROM POSITION(' ' IN u.full_name) + 1)), ''),
            split_part(u.full_name, ' ', 1)
        )
    END,
    u.document_type,
    u.document_number,
    u.phone,
    u.email,
    u.locale
FROM users u
WHERE u.document_type IS NOT NULL  -- skip users w/o cédula; defensive
ON CONFLICT (document_type, document_number) DO NOTHING;

-- Step 2: add users.person_id (nullable first, to allow backfill).
ALTER TABLE users
    ADD COLUMN person_id BIGINT REFERENCES persons (persons_id);

-- Step 3: link each user to its newly-created persons row by matching cédula.
UPDATE users u
SET person_id = (
    SELECT p.persons_id
    FROM persons p
    WHERE p.document_type = u.document_type
      AND p.document_number = u.document_number
)
WHERE u.document_type IS NOT NULL;

-- Step 4: promote person_id to NOT NULL UNIQUE (1:1 with persons).
ALTER TABLE users
    ALTER COLUMN person_id SET NOT NULL,
    ADD  CONSTRAINT uniq_users_person UNIQUE (person_id);

-- Step 5: drop the demographic columns now owned by persons.
-- The composite UNIQUE + the chk_users_document constraint must go before the
-- columns themselves.
ALTER TABLE users
    DROP CONSTRAINT IF EXISTS chk_users_document,
    DROP CONSTRAINT IF EXISTS users_document_type_document_number_key;

ALTER TABLE users
    DROP COLUMN full_name,
    DROP COLUMN document_type,
    DROP COLUMN document_number,
    DROP COLUMN phone,
    DROP COLUMN locale;
