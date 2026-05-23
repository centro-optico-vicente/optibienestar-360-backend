SET search_path TO app, public;

-- Roles: SYSTEM en inglés (técnico), resto en español sin sufijos redundantes
INSERT INTO roles (name, description)
VALUES ('SYSTEM',          'Administración técnica del sistema — acceso total incluyendo gestión de roles'),
       ('ADMINISTRADOR',   'Administración del negocio — acceso operativo completo'),
       ('OPERADOR',        'Gestión operativa sin acceso a historial médico'),
       ('OPERADOR_MEDICO', 'Operador con acceso a historial médico'),
       ('ALIADO',          'Operador del aliado — validación de afiliados y registro de uso de beneficios'),
       ('AFILIADO',        'Portal del afiliado — consulta y gestión de datos propios'),
       ('PROMOTOR',        'Venta de membresías y consulta de comisiones propias');


-- Permisos por dominio
INSERT INTO permissions (name, domain, description)
VALUES
    -- USERS
    ('USER_CREATE',         'USERS', 'Crear nuevas cuentas de usuario'),
    ('USER_UPDATE',         'USERS', 'Actualizar datos del perfil de usuario'),
    ('USER_DELETE',         'USERS', 'Desactivar cuentas de usuario'),
    ('USER_VIEW_ALL',       'USERS', 'Ver todos los usuarios del sistema'),
    ('USER_CHANGE_ROLE',    'USERS', 'Asignar o revocar roles de usuario'),
    ('USER_RESET_PASSWORD', 'USERS', 'Forzar restablecimiento de contraseña'),
    -- MEMBERS
    ('MEMBER_CREATE',          'MEMBERS', 'Registrar nuevos afiliados'),
    ('MEMBER_UPDATE',          'MEMBERS', 'Actualizar datos del afiliado'),
    ('MEMBER_DELETE',          'MEMBERS', 'Desactivar registros de afiliado'),
    ('MEMBER_VIEW_ALL',        'MEMBERS', 'Ver todos los afiliados'),
    ('MEMBER_VIEW_OWN',        'MEMBERS', 'Ver perfil propio de afiliado'),
    ('MEMBER_UPLOAD_DOCUMENT', 'MEMBERS', 'Cargar documentos de un afiliado'),
    ('MEDICAL_RECORD_VIEW',    'MEMBERS', 'Ver historial médico del afiliado'),
    ('MEDICAL_RECORD_UPDATE',  'MEMBERS', 'Actualizar historial médico del afiliado'),
    -- ALLIES
    ('ALLY_CREATE',           'ALLIES', 'Registrar nuevos aliados comerciales'),
    ('ALLY_UPDATE',           'ALLIES', 'Actualizar datos del aliado comercial'),
    ('ALLY_DELETE',           'ALLIES', 'Desactivar aliados comerciales'),
    ('ALLY_VIEW_ALL',         'ALLIES', 'Ver todos los aliados comerciales'),
    ('ALLY_VIEW_OWN',         'ALLIES', 'Ver datos propios del aliado'),
    ('ALLY_AGREEMENT_MANAGE', 'ALLIES', 'Gestionar acuerdos de servicio con aliados'),
    ('ALLY_VALIDATE_MEMBER',  'ALLIES', 'Validar solvencia del afiliado en tiempo real'),
    ('ALLY_REGISTER_USAGE',   'ALLIES', 'Registrar uso de beneficio del afiliado'),
    -- PLANS
    ('PLAN_CREATE',   'PLANS', 'Crear planes de membresía'),
    ('PLAN_UPDATE',   'PLANS', 'Actualizar configuración del plan'),
    ('PLAN_DELETE',   'PLANS', 'Desactivar planes'),
    ('PLAN_VIEW_ALL', 'PLANS', 'Ver todos los planes disponibles'),
    -- MEMBERSHIPS
    ('MEMBERSHIP_CREATE',      'MEMBERSHIPS', 'Crear nuevas membresías'),
    ('MEMBERSHIP_UPDATE',      'MEMBERSHIPS', 'Actualizar datos de membresía'),
    ('MEMBERSHIP_CANCEL',      'MEMBERSHIPS', 'Cancelar una membresía'),
    ('MEMBERSHIP_REACTIVATE',  'MEMBERSHIPS', 'Reactivar una membresía suspendida o expirada'),
    ('MEMBERSHIP_VIEW_ALL',    'MEMBERSHIPS', 'Ver todas las membresías'),
    ('MEMBERSHIP_VIEW_OWN',    'MEMBERSHIPS', 'Ver membresía propia'),
    -- PAYMENTS
    ('PAYMENT_REGISTER', 'PAYMENTS', 'Registrar un pago para revisión'),
    ('PAYMENT_APPROVE',  'PAYMENTS', 'Aprobar un pago pendiente'),
    ('PAYMENT_REJECT',   'PAYMENTS', 'Rechazar un pago pendiente'),
    ('PAYMENT_VIEW_ALL', 'PAYMENTS', 'Ver todos los pagos'),
    ('PAYMENT_VIEW_OWN', 'PAYMENTS', 'Ver historial de pagos propios'),
    -- PROMOTERS
    ('PROMOTER_CREATE',   'PROMOTERS', 'Registrar promotores'),
    ('PROMOTER_UPDATE',   'PROMOTERS', 'Actualizar datos del promotor'),
    ('PROMOTER_DELETE',   'PROMOTERS', 'Desactivar promotores'),
    ('PROMOTER_VIEW_ALL', 'PROMOTERS', 'Ver todos los promotores'),
    -- COMMISSIONS
    ('COMMISSION_VIEW_ALL', 'COMMISSIONS', 'Ver todos los registros de comisiones'),
    ('COMMISSION_VIEW_OWN', 'COMMISSIONS', 'Ver comisiones propias'),
    ('COMMISSION_PAYOUT',   'COMMISSIONS', 'Ejecutar ciclo de pago de comisiones'),
    -- REFERRALS
    ('REFERRAL_CODE_CREATE',   'REFERRALS', 'Generar códigos de referido'),
    ('REFERRAL_CODE_VIEW_ALL', 'REFERRALS', 'Ver todos los códigos de referido e historial'),
    ('REFERRAL_CODE_VIEW_OWN', 'REFERRALS', 'Ver código de referido propio e historial'),
    -- REPORTS
    ('REPORT_VIEW_DASHBOARD', 'REPORTS', 'Acceder al panel de control operativo'),
    ('REPORT_EXPORT',         'REPORTS', 'Exportar reportes');


-- SYSTEM: todos los permisos (49)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name = 'SYSTEM';


-- ADMINISTRADOR: todos excepto USER_CHANGE_ROLE (48)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name = 'ADMINISTRADOR'
  AND p.name != 'USER_CHANGE_ROLE';


-- OPERADOR: acceso operativo completo sin acceso médico ni gestión de roles (43)
-- Excluye: USER_CHANGE_ROLE, MEDICAL_RECORD_*, PAYMENT_VIEW_OWN,
--          COMMISSION_VIEW_OWN, REFERRAL_CODE_VIEW_OWN
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name = 'OPERADOR'
  AND p.name NOT IN (
      'USER_CHANGE_ROLE',
      'MEDICAL_RECORD_VIEW',
      'MEDICAL_RECORD_UPDATE',
      'PAYMENT_VIEW_OWN',
      'COMMISSION_VIEW_OWN',
      'REFERRAL_CODE_VIEW_OWN'
  );


-- OPERADOR_MEDICO: igual que OPERADOR más acceso a historial médico (45)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name = 'OPERADOR_MEDICO'
  AND p.name NOT IN (
      'USER_CHANGE_ROLE',
      'PAYMENT_VIEW_OWN',
      'COMMISSION_VIEW_OWN',
      'REFERRAL_CODE_VIEW_OWN'
  );


-- ALIADO: validación de afiliados y registro de uso (3)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name = 'ALIADO'
  AND p.name IN ('ALLY_VIEW_OWN', 'ALLY_VALIDATE_MEMBER', 'ALLY_REGISTER_USAGE');


-- AFILIADO: portal propio — perfil, membresía, pagos y referido (5)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name = 'AFILIADO'
  AND p.name IN (
      'MEMBER_VIEW_OWN',
      'MEMBERSHIP_VIEW_OWN',
      'PAYMENT_VIEW_OWN',
      'PAYMENT_REGISTER',
      'REFERRAL_CODE_VIEW_OWN'
  );


-- PROMOTOR: registro de afiliados, membresías, pagos y comisiones propias (5)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name = 'PROMOTOR'
  AND p.name IN (
      'MEMBER_CREATE',
      'MEMBERSHIP_CREATE',
      'PAYMENT_REGISTER',
      'COMMISSION_VIEW_OWN',
      'REFERRAL_CODE_VIEW_OWN'
  );


-- Política de seguridad activa con valores predeterminados seguros
INSERT INTO security_policies (max_login_attempts, lockout_duration_minutes, days_password_expires,
                               password_history_count, max_concurrent_sessions)
VALUES (5, 30, 90, 5, 3);
