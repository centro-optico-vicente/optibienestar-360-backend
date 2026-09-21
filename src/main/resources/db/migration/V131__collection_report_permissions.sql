SET search_path TO app, public;

INSERT INTO permissions (name, domain_id, description)
SELECT v.name, d.permission_domains_id, v.description
FROM (VALUES
    ('COLLECTION_REPORT_GENERATE', 'Generar reportes de cobros'),
    ('COLLECTION_RECORD_AUDIT_VIEW', 'Ver el historial de cambios de cobros'),
    ('COLLECTION_REPORT_AUDIT_VIEW', 'Ver el historial de reportes de cobros')
) v(name, description)
JOIN permission_domains d ON d.code = 'PAYMENTS'
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
CROSS JOIN permissions p
WHERE r.name IN ('SYSTEM', 'ADMINISTRADOR', 'OPERADOR')
  AND p.name IN (
      'COLLECTION_REPORT_GENERATE',
      'COLLECTION_RECORD_AUDIT_VIEW',
      'COLLECTION_REPORT_AUDIT_VIEW'
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;
