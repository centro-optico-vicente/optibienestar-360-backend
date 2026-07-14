SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- app.bcrypt_hash(plain, strength) — BCrypt hashing compatible with Spring.
-- ────────────────────────────────────────────────────────────────────────────
-- Spring uses `new BCryptPasswordEncoder(12)` (see SecurityConfig). pgcrypto's
-- `crypt(plain, gen_salt('bf', strength))` produces a BCrypt hash with the
-- `$2a$` prefix, which BCryptPasswordEncoder.matches() verifies fine.
--
-- Lets SQL seeds insert plaintext passwords without precomputing the hash in
-- the app:
--   INSERT INTO users (..., password_hash) VALUES (..., app.bcrypt_hash('optibienestar360'));
--   SELECT app.bcrypt_hash('myPass', 12);   -- explicit strength
--
-- VOLATILE because gen_salt() is non-deterministic (random salt per call).
-- SET search_path pins public so crypt()/gen_salt() from pgcrypto resolve
-- regardless of the caller's search_path.
--
-- SECURITY: the plaintext travels inside the SQL statement and may end up in
-- the server logs (log_statement = 'all'/'mod'). Intended for seeds and dev;
-- in production the real hashing is done by the app (UserService.encode),
-- not by the database.
-- ────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION app.bcrypt_hash(plain text, strength int DEFAULT 12)
    RETURNS text
    LANGUAGE sql
    VOLATILE
    RETURNS NULL ON NULL INPUT
    SET search_path = public, app
AS $$
    SELECT crypt(plain, gen_salt('bf', strength));
$$;

COMMENT ON FUNCTION app.bcrypt_hash(text, int) IS
    'BCrypt hash ($2a$) compatible with Spring BCryptPasswordEncoder. Seeds/dev only: plaintext may appear in SQL logs.';
