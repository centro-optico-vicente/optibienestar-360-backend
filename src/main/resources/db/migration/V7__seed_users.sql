SET search_path TO app, public;

-- Usuarios seed iniciales del sistema
--
-- Estrategia: password placeholder con cambio manual post-deploy
--   password_hash:           bcrypt(strength 12) de 'ChangeMe2026!'
--   status:                  ACTIVE
--   password_never_expires:  ${seed_password_never_expires}
--                            (false en prod, true en dev — ver application*.properties)
--
-- IMPORTANTE: tras el primer deploy a producción, loguear con cada usuario y cambiar
-- la contraseña desde el panel admin. El campo password_reset_token está reservado al
-- flujo de recuperación vía email (AuthService.recoverPassword) y no se usa aquí.

INSERT INTO users (
    email,
    password_hash,
    full_name,
    document_type,
    document_number,
    phone,
    default_role_id,
    status,
    password_never_expires
)
SELECT
    seed.email,
    seed.password_hash,
    seed.full_name,
    seed.document_type,
    seed.document_number,
    seed.phone,
    r.roles_id,
    'ACTIVE',
    ${seed_password_never_expires}
FROM (
    VALUES
        ('fenixcoreenterprises@gmail.com',
         '$2a$12$hLibxc5QDDRQZqCfFKjsBuoLn.KJCtW1Zc22u7pfGwWK07j/I0ZdK',
         'System',
         'V', '1',
         '+58412-5164689',
         'SYSTEM'),
        ('elsiosanchez15@outlook.com',
         '$2a$12$hLibxc5QDDRQZqCfFKjsBuoLn.KJCtW1Zc22u7pfGwWK07j/I0ZdK',
         'Administrador',
         'V', '2',
         '+58414 538 4801',
         'ADMINISTRADOR')
) AS seed(email, password_hash, full_name, document_type, document_number, phone, role_name)
JOIN roles r ON r.name = seed.role_name;


-- Vincular cada usuario seed a su rol en user_roles
INSERT INTO user_roles (user_id, role_id)
SELECT u.users_id, u.default_role_id
FROM users u
WHERE u.email IN ('fenixcoreenterprises@gmail.com', 'elsiosanchez15@outlook.com');


-- Registrar el password placeholder en historial para prevenir reutilización
INSERT INTO user_password_history (user_id, password_hash)
SELECT u.users_id, u.password_hash
FROM users u
WHERE u.email IN ('fenixcoreenterprises@gmail.com', 'elsiosanchez15@outlook.com');
