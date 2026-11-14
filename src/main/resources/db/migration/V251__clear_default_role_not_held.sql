-- ============================================================
-- V251 — Clear sign-in roles the user no longer holds.
--
-- users.default_role_id is the role every session starts with. Removing a role
-- from a user (role screen, user edit) or deactivating a role did not clear it,
-- so some users point at a role they no longer have. The login already falls
-- back to the oldest effective role, but the screens showed a default that is
-- not available. From now on the backend clears it on those paths; this
-- migration fixes the rows left behind.
--
-- A default is kept only when the user has an active, non-expired assignment
-- of that role and the role itself is active.
-- ============================================================

UPDATE app.users u
   SET default_role_id = NULL
 WHERE u.default_role_id IS NOT NULL
   AND NOT EXISTS (
        SELECT 1
          FROM app.user_roles ur
          JOIN app.roles r ON r.roles_id = ur.role_id
         WHERE ur.user_id = u.users_id
           AND ur.role_id = u.default_role_id
           AND ur.is_active
           AND (ur.expires_at IS NULL OR ur.expires_at > now())
           AND r.is_active
   );
