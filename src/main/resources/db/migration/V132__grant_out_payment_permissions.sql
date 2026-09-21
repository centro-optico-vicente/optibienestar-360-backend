SET search_path TO app, public;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
CROSS JOIN permissions p
WHERE r.name IN ('SYSTEM', 'ADMINISTRADOR', 'OPERADOR')
  AND p.name IN (
      'PAYMENT_VIEW_ALL',
      'PAYMENT_CREATE',
      'PAYMENT_UPDATE',
      'PAYMENT_DELETE',
      'PAYMENT_PROCESS',
      'PAYMENT_APPROVE',
      'PAYMENT_REJECT'
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;
