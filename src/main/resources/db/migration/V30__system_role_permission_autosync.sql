SET search_path TO app, public;

-- V30: keep the SYSTEM role holding EVERY permission automatically, and let a
-- deleted permission clean up its grants on its own.
--
-- Until now "SYSTEM has every permission" depended on each migration that adds
-- a new permission remembering to also grant it to SYSTEM (the
-- `WHERE r.name IN ('SYSTEM', ...)` pattern in V6/V11/V22/V28). This automates
-- that guarantee so it can never be forgotten, and adds cascade cleanup on
-- permission deletion.


-- 1a. Backfill: grant SYSTEM every permission that exists today. Idempotent —
-- ON CONFLICT skips the rows it already has (SYSTEM is complete as of V28).
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name = 'SYSTEM'
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- 1b. Trigger: every permission inserted from now on is auto-granted to SYSTEM.
-- Schema-qualified so it resolves regardless of the caller's search_path.
-- ON CONFLICT DO NOTHING so a migration that ALSO grants SYSTEM explicitly (the
-- historical pattern) does not collide with the unique (role_id, permission_id).
-- If the SYSTEM role does not exist yet (an insert before it is seeded), the
-- SELECT yields no row and the grant is simply skipped.
CREATE OR REPLACE FUNCTION app.grant_permission_to_system()
    RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO app.role_permissions (role_id, permission_id)
    SELECT r.roles_id, NEW.permissions_id
    FROM app.roles r
    WHERE r.name = 'SYSTEM'
    ON CONFLICT (role_id, permission_id) DO NOTHING;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_permissions_grant_system
    AFTER INSERT ON permissions
    FOR EACH ROW EXECUTE FUNCTION app.grant_permission_to_system();


-- 2. Cascade: deleting a permission removes its role_permissions rows instead of
-- failing on the FK. The inline FK from V5 is auto-named
-- `role_permissions_permission_id_fkey`; recreate it with ON DELETE CASCADE.
-- role_permissions is the only table referencing permissions.
ALTER TABLE role_permissions
    DROP CONSTRAINT role_permissions_permission_id_fkey,
    ADD CONSTRAINT role_permissions_permission_id_fkey
        FOREIGN KEY (permission_id) REFERENCES permissions (permissions_id) ON DELETE CASCADE;
