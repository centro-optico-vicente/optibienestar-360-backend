SET search_path TO app, public;

-- V31: give ADMINISTRADOR full role management by granting ROLE_PERMISSION_EDIT.
--
-- V6 deliberately withheld the two governance permissions from ADMINISTRADOR
-- ("ADMINISTRADOR can be granted them later via the admin panel if needed").
-- Handing over role management is now safe: the SYSTEM role is editable only by
-- a SYSTEM actor (RoleService rejects everyone else with
-- `role.system.not_editable`) and is never deletable by anyone. So an
-- ADMINISTRADOR with ROLE_PERMISSION_EDIT gets full access to every role
-- EXCEPT SYSTEM, which stays reserved to SYSTEM actors.
--
-- USER_CHANGE_ROLE is deliberately NOT granted here: despite its name it also
-- doubles as the "SYSTEM only" marker guarding catalog writes (see
-- AdminCatalogsController.WRITE_AUTH), so granting it to ADMINISTRADOR would
-- silently hand over catalog write access as a side effect.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name = 'ADMINISTRADOR'
  AND p.name = 'ROLE_PERMISSION_EDIT'
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- Fail loudly instead of silently granting nothing: an INSERT ... SELECT whose
-- role/permission name does not match simply inserts 0 rows without error, so
-- assert the grant actually landed.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1
                   FROM role_permissions rp
                            JOIN roles r ON r.roles_id = rp.role_id
                            JOIN permissions p ON p.permissions_id = rp.permission_id
                   WHERE r.name = 'ADMINISTRADOR'
                     AND p.name = 'ROLE_PERMISSION_EDIT') THEN
        RAISE EXCEPTION 'V31: ADMINISTRADOR did not receive ROLE_PERMISSION_EDIT — role or permission name mismatch';
    END IF;
END $$;
