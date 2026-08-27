SET search_path TO app, public;

-- V79: split the system's 8 `_MANAGE` permissions (7 controllers) into
-- granular VIEW_ALL/CREATE/UPDATE/DELETE, mirroring V78's catalog split.
-- Also renames PAYMENT_REGISTER -> PAYMENT_CREATE for the same reason: it was
-- the only permission with a verb outside the VIEW_ALL/VIEW_OWN/CREATE/UPDATE/
-- DELETE/MANAGE convention, which made it invisible to the "Crear" bulk toggle
-- in the role-permissions modal (it only matches the _CREATE suffix).
--
-- Domains are untouched (ALLIES/ROLES/COMMISSIONS/MEMBERS already exist) —
-- only permission names change.


-- 1. New permissions.
INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    -- ALLY_AGREEMENT_MANAGE -> full CRUD (today gates reads too)
    ('ALLY_AGREEMENT_VIEW_ALL', 'ALLIES', 'Ver acuerdos de servicio con aliados'),
    ('ALLY_AGREEMENT_CREATE',   'ALLIES', 'Crear acuerdos de servicio con aliados'),
    ('ALLY_AGREEMENT_UPDATE',   'ALLIES', 'Editar acuerdos de servicio con aliados'),
    ('ALLY_AGREEMENT_DELETE',   'ALLIES', 'Eliminar acuerdos de servicio con aliados'),

    -- ALLY_USERS_MANAGE -> full CRUD, read decoupled from ALLY_VIEW_ALL
    ('ALLY_USER_VIEW_ALL', 'ALLIES', 'Ver el personal asignado a un aliado'),
    ('ALLY_USER_CREATE',   'ALLIES', 'Asignar personal a un aliado'),
    ('ALLY_USER_UPDATE',   'ALLIES', 'Editar el rol o contacto primario del personal asignado'),
    ('ALLY_USER_DELETE',   'ALLIES', 'Quitar personal asignado a un aliado'),

    -- ROLE_USERS_MANAGE -> assign/remove only (no edit verb), read decoupled from USER_VIEW_ALL
    ('ROLE_USER_VIEW_ALL', 'ROLES', 'Ver los usuarios asignados a un rol'),
    ('ROLE_USER_CREATE',   'ROLES', 'Asignar usuarios a un rol'),
    ('ROLE_USER_DELETE',   'ROLES', 'Quitar usuarios de un rol'),

    -- COMMISSION_TIER_MANAGE -> full CRUD
    ('COMMISSION_TIER_VIEW_ALL', 'COMMISSIONS', 'Ver escalas de comisión'),
    ('COMMISSION_TIER_CREATE',   'COMMISSIONS', 'Crear escalas de comisión'),
    ('COMMISSION_TIER_UPDATE',   'COMMISSIONS', 'Editar escalas de comisión'),
    ('COMMISSION_TIER_DELETE',   'COMMISSIONS', 'Eliminar escalas de comisión'),

    -- BONUS_RULE_MANAGE -> full CRUD
    ('BONUS_RULE_VIEW_ALL', 'COMMISSIONS', 'Ver reglas de bono'),
    ('BONUS_RULE_CREATE',   'COMMISSIONS', 'Crear reglas de bono'),
    ('BONUS_RULE_UPDATE',   'COMMISSIONS', 'Editar reglas de bono'),
    ('BONUS_RULE_DELETE',   'COMMISSIONS', 'Eliminar reglas de bono'),

    -- CORPORATE_CONTRACT_MANAGE -> CRUD (VIEW_ALL already exists, untouched)
    -- plus a dedicated CRUD set for the contract<->member association.
    ('CORPORATE_CONTRACT_CREATE',          'MEMBERS', 'Crear contratos corporativos'),
    ('CORPORATE_CONTRACT_UPDATE',          'MEMBERS', 'Editar contratos corporativos'),
    ('CORPORATE_CONTRACT_DELETE',          'MEMBERS', 'Eliminar contratos corporativos'),
    ('CORPORATE_CONTRACT_MEMBER_VIEW_ALL', 'MEMBERS', 'Ver los afiliados inscritos en un contrato corporativo'),
    ('CORPORATE_CONTRACT_MEMBER_CREATE',   'MEMBERS', 'Inscribir afiliados en un contrato corporativo'),
    ('CORPORATE_CONTRACT_MEMBER_UPDATE',   'MEMBERS', 'Editar la inscripción de un afiliado en un contrato'),
    ('CORPORATE_CONTRACT_MEMBER_DELETE',   'MEMBERS', 'Desinscribir un afiliado de un contrato corporativo'),

    -- COLLECTION_COMMISSION_TIER_MANAGE -> full CRUD
    ('COLLECTION_COMMISSION_TIER_VIEW_ALL', 'COMMISSIONS', 'Ver escalas de comisión de cobranza'),
    ('COLLECTION_COMMISSION_TIER_CREATE',   'COMMISSIONS', 'Crear escalas de comisión de cobranza'),
    ('COLLECTION_COMMISSION_TIER_UPDATE',   'COMMISSIONS', 'Editar escalas de comisión de cobranza'),
    ('COLLECTION_COMMISSION_TIER_DELETE',   'COMMISSIONS', 'Eliminar escalas de comisión de cobranza'),

    -- LEADERBOARD_PRIZE_MANAGE -> full CRUD
    ('LEADERBOARD_PRIZE_VIEW_ALL', 'COMMISSIONS', 'Ver premios de tabla de posiciones'),
    ('LEADERBOARD_PRIZE_CREATE',   'COMMISSIONS', 'Crear premios de tabla de posiciones'),
    ('LEADERBOARD_PRIZE_UPDATE',   'COMMISSIONS', 'Editar premios de tabla de posiciones'),
    ('LEADERBOARD_PRIZE_DELETE',   'COMMISSIONS', 'Eliminar premios de tabla de posiciones')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code;


-- 2. PAYMENT_REGISTER -> PAYMENT_CREATE rename (same domain as the old row).
INSERT INTO permissions (name, domain_id, description)
SELECT 'PAYMENT_CREATE', domain_id, 'Registrar un pago para revisión'
FROM permissions WHERE name = 'PAYMENT_REGISTER';

INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, np.permissions_id
FROM role_permissions rp
         JOIN permissions p  ON p.permissions_id = rp.permission_id AND p.name = 'PAYMENT_REGISTER'
         CROSS JOIN permissions np
WHERE np.name = 'PAYMENT_CREATE'
ON CONFLICT (role_id, permission_id) DO NOTHING;

DELETE FROM permissions WHERE name = 'PAYMENT_REGISTER';


-- 3. Backfill each `_MANAGE` permission's holders onto the new granular set.
WITH manage_map(old_manage, new_names) AS (
    VALUES
        ('ALLY_AGREEMENT_MANAGE',           ARRAY['ALLY_AGREEMENT_VIEW_ALL', 'ALLY_AGREEMENT_CREATE', 'ALLY_AGREEMENT_UPDATE', 'ALLY_AGREEMENT_DELETE']),
        ('ALLY_USERS_MANAGE',               ARRAY['ALLY_USER_VIEW_ALL', 'ALLY_USER_CREATE', 'ALLY_USER_UPDATE', 'ALLY_USER_DELETE']),
        ('ROLE_USERS_MANAGE',               ARRAY['ROLE_USER_VIEW_ALL', 'ROLE_USER_CREATE', 'ROLE_USER_DELETE']),
        ('COMMISSION_TIER_MANAGE',          ARRAY['COMMISSION_TIER_VIEW_ALL', 'COMMISSION_TIER_CREATE', 'COMMISSION_TIER_UPDATE', 'COMMISSION_TIER_DELETE']),
        ('BONUS_RULE_MANAGE',               ARRAY['BONUS_RULE_VIEW_ALL', 'BONUS_RULE_CREATE', 'BONUS_RULE_UPDATE', 'BONUS_RULE_DELETE']),
        ('CORPORATE_CONTRACT_MANAGE',       ARRAY['CORPORATE_CONTRACT_CREATE', 'CORPORATE_CONTRACT_UPDATE', 'CORPORATE_CONTRACT_DELETE',
                                                    'CORPORATE_CONTRACT_MEMBER_VIEW_ALL', 'CORPORATE_CONTRACT_MEMBER_CREATE',
                                                    'CORPORATE_CONTRACT_MEMBER_UPDATE', 'CORPORATE_CONTRACT_MEMBER_DELETE']),
        ('COLLECTION_COMMISSION_TIER_MANAGE', ARRAY['COLLECTION_COMMISSION_TIER_VIEW_ALL', 'COLLECTION_COMMISSION_TIER_CREATE', 'COLLECTION_COMMISSION_TIER_UPDATE', 'COLLECTION_COMMISSION_TIER_DELETE']),
        ('LEADERBOARD_PRIZE_MANAGE',        ARRAY['LEADERBOARD_PRIZE_VIEW_ALL', 'LEADERBOARD_PRIZE_CREATE', 'LEADERBOARD_PRIZE_UPDATE', 'LEADERBOARD_PRIZE_DELETE'])
)
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, np.permissions_id
FROM role_permissions rp
         JOIN permissions p ON p.permissions_id = rp.permission_id
         JOIN manage_map mm ON mm.old_manage = p.name
         JOIN permissions np ON np.name = ANY (mm.new_names)
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- 4. Preserve read access that today comes from a broader permission, now
--    that the read is decoupled into its own key.
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, np.permissions_id
FROM role_permissions rp
         JOIN permissions p  ON p.permissions_id = rp.permission_id AND p.name = 'ALLY_VIEW_ALL'
         CROSS JOIN permissions np
WHERE np.name = 'ALLY_USER_VIEW_ALL'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, np.permissions_id
FROM role_permissions rp
         JOIN permissions p  ON p.permissions_id = rp.permission_id AND p.name = 'USER_VIEW_ALL'
         CROSS JOIN permissions np
WHERE np.name = 'ROLE_USER_VIEW_ALL'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, np.permissions_id
FROM role_permissions rp
         JOIN permissions p  ON p.permissions_id = rp.permission_id AND p.name = 'CORPORATE_CONTRACT_VIEW_ALL'
         CROSS JOIN permissions np
WHERE np.name = 'CORPORATE_CONTRACT_MEMBER_VIEW_ALL'
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- 5. Drop the superseded `_MANAGE` keys. The V30 ON DELETE CASCADE clears
--    role_permissions automatically.
DELETE FROM permissions WHERE name IN (
    'ALLY_AGREEMENT_MANAGE', 'ALLY_USERS_MANAGE', 'ROLE_USERS_MANAGE',
    'COMMISSION_TIER_MANAGE', 'BONUS_RULE_MANAGE', 'CORPORATE_CONTRACT_MANAGE',
    'COLLECTION_COMMISSION_TIER_MANAGE', 'LEADERBOARD_PRIZE_MANAGE'
);


-- 6. Fail loudly rather than migrate into a half-applied state.
DO $$
DECLARE
    n int;
BEGIN
    SELECT count(*) INTO n FROM permissions
    WHERE name IN (
        'ALLY_AGREEMENT_VIEW_ALL', 'ALLY_AGREEMENT_CREATE', 'ALLY_AGREEMENT_UPDATE', 'ALLY_AGREEMENT_DELETE',
        'ALLY_USER_VIEW_ALL', 'ALLY_USER_CREATE', 'ALLY_USER_UPDATE', 'ALLY_USER_DELETE',
        'ROLE_USER_VIEW_ALL', 'ROLE_USER_CREATE', 'ROLE_USER_DELETE',
        'COMMISSION_TIER_VIEW_ALL', 'COMMISSION_TIER_CREATE', 'COMMISSION_TIER_UPDATE', 'COMMISSION_TIER_DELETE',
        'BONUS_RULE_VIEW_ALL', 'BONUS_RULE_CREATE', 'BONUS_RULE_UPDATE', 'BONUS_RULE_DELETE',
        'CORPORATE_CONTRACT_CREATE', 'CORPORATE_CONTRACT_UPDATE', 'CORPORATE_CONTRACT_DELETE',
        'CORPORATE_CONTRACT_MEMBER_VIEW_ALL', 'CORPORATE_CONTRACT_MEMBER_CREATE',
        'CORPORATE_CONTRACT_MEMBER_UPDATE', 'CORPORATE_CONTRACT_MEMBER_DELETE',
        'COLLECTION_COMMISSION_TIER_VIEW_ALL', 'COLLECTION_COMMISSION_TIER_CREATE',
        'COLLECTION_COMMISSION_TIER_UPDATE', 'COLLECTION_COMMISSION_TIER_DELETE',
        'LEADERBOARD_PRIZE_VIEW_ALL', 'LEADERBOARD_PRIZE_CREATE', 'LEADERBOARD_PRIZE_UPDATE', 'LEADERBOARD_PRIZE_DELETE',
        'PAYMENT_CREATE'
    );
    IF n <> 35 THEN
        RAISE EXCEPTION 'V79: expected 35 new permissions, found %', n;
    END IF;

    SELECT count(*) INTO n FROM permissions
    WHERE name IN (
        'ALLY_AGREEMENT_MANAGE', 'ALLY_USERS_MANAGE', 'ROLE_USERS_MANAGE',
        'COMMISSION_TIER_MANAGE', 'BONUS_RULE_MANAGE', 'CORPORATE_CONTRACT_MANAGE',
        'COLLECTION_COMMISSION_TIER_MANAGE', 'LEADERBOARD_PRIZE_MANAGE', 'PAYMENT_REGISTER'
    );
    IF n <> 0 THEN
        RAISE EXCEPTION 'V79: % superseded permission(s) still exist', n;
    END IF;

    -- SYSTEM must hold every new permission (V30 trigger).
    SELECT count(*) INTO n
    FROM permissions p
    WHERE p.name IN ('ALLY_AGREEMENT_VIEW_ALL', 'CORPORATE_CONTRACT_MEMBER_CREATE', 'PAYMENT_CREATE')
      AND NOT EXISTS (
          SELECT 1 FROM role_permissions rp
                            JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'SYSTEM'
          WHERE rp.permission_id = p.permissions_id
      );
    IF n > 0 THEN
        RAISE EXCEPTION 'V79: SYSTEM is missing % of the sampled new permissions', n;
    END IF;
END $$;
