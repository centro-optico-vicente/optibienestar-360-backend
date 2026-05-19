# Playbook — Agregar nuevo rol o permiso

> Para agregar un rol nuevo al sistema o ampliar permisos de uno existente.

## Cuándo

- Necesidad de un perfil de usuario no cubierto por los 5 base (ADMIN, OPERADOR, ALIADO_USER, AFILIADO_USER, PROMOTOR)
- Necesidad de un permiso granular nuevo para una feature

## Paso 1 — Definir el rol/permiso

Documentar:
- Nombre (UPPER_SNAKE_CASE)
- Descripción (qué usuarios lo tendrán)
- Permisos asociados (granulares)

Ejemplo:
```
Nuevo rol: OPERADOR_MEDICO
Descripción: Operador con acceso a antecedentes médicos
Permisos: todos los del rol OPERADOR + MEDICAL_RECORD_VIEW + MEDICAL_RECORD_UPDATE
```

## Paso 2 — Migración Flyway

```sql
-- V{N}__add_role_OPERADOR_MEDICO.sql

-- Insertar nuevo permiso si no existe
INSERT INTO permissions (permission_id, code, description, is_active, status, created_at, updated_at)
VALUES (gen_random_uuid(), 'MEDICAL_RECORD_VIEW', 'View medical records', TRUE, 'ACTIVE', NOW(), NOW())
ON CONFLICT (code) DO NOTHING;

INSERT INTO permissions (permission_id, code, description, is_active, status, created_at, updated_at)
VALUES (gen_random_uuid(), 'MEDICAL_RECORD_UPDATE', 'Update medical records', TRUE, 'ACTIVE', NOW(), NOW())
ON CONFLICT (code) DO NOTHING;

-- Insertar nuevo rol
INSERT INTO roles (role_id, code, name, description, is_active, status, created_at, updated_at)
VALUES (gen_random_uuid(), 'OPERADOR_MEDICO', 'Operador Médico', 'Operador con acceso a antecedentes médicos', TRUE, 'ACTIVE', NOW(), NOW())
ON CONFLICT (code) DO NOTHING;

-- Asignar todos los permisos de OPERADOR al nuevo rol
INSERT INTO role_permissions (role_permission_id, role_id, permission_id, is_active, status, created_at, updated_at)
SELECT
    gen_random_uuid(),
    (SELECT role_id FROM roles WHERE code = 'OPERADOR_MEDICO'),
    permission_id,
    TRUE, 'ACTIVE', NOW(), NOW()
FROM role_permissions rp
JOIN roles r ON rp.role_id = r.role_id
WHERE r.code = 'OPERADOR'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Adicionalmente, los permisos específicos del nuevo rol
INSERT INTO role_permissions (role_permission_id, role_id, permission_id, is_active, status, created_at, updated_at)
SELECT
    gen_random_uuid(),
    (SELECT role_id FROM roles WHERE code = 'OPERADOR_MEDICO'),
    p.permission_id,
    TRUE, 'ACTIVE', NOW(), NOW()
FROM permissions p
WHERE p.code IN ('MEDICAL_RECORD_VIEW', 'MEDICAL_RECORD_UPDATE')
ON CONFLICT (role_id, permission_id) DO NOTHING;
```

## Paso 3 — Aplicar `@PreAuthorize` en endpoints

```java
@PreAuthorize("hasAuthority('MEDICAL_RECORD_VIEW')")
@GetMapping("/{memberId}/medical-record")
public MedicalRecordDTO get(@PathVariable UUID memberId) {
    // El audit log debe registrar este acceso
    return service.findByMemberId(memberId);
}

@PreAuthorize("hasAuthority('MEDICAL_RECORD_UPDATE')")
@PutMapping("/{memberId}/medical-record")
public MedicalRecordDTO update(@PathVariable UUID memberId, @Valid @RequestBody MedicalRecordUpdateDTO dto) {
    return service.update(memberId, dto);
}
```

## Paso 4 — Audit log obligatorio para acciones sensibles

Si el rol/permiso afecta datos sensibles (médicos, financieros), agregar audit:

```java
@AuditAction(action = "MEDICAL_RECORD_VIEW", entity = "MedicalRecord")
public MedicalRecordDTO findByMemberId(UUID memberId) {
    // ...
}
```

## Paso 5 — Tests

```java
@Test @WithMockUser(authorities = "MEDICAL_RECORD_VIEW")
void getMedicalRecord_returns200WhenAuthorized() throws Exception {
    mvc.perform(get("/v1/admin/members/{id}/medical-record", id))
        .andExpect(status().isOk());
}

@Test @WithMockUser  // sin authority
void getMedicalRecord_returns403WhenNotAuthorized() throws Exception {
    mvc.perform(get("/v1/admin/members/{id}/medical-record", id))
        .andExpect(status().isForbidden());
}
```

## Paso 6 — Documentación

- [ ] Actualizar [`../specs/05-roles-permissions.md`](../specs/05-roles-permissions.md) con el nuevo rol/permiso
- [ ] Actualizar [hub `stakeholders.md`](../../../centro-optico-vicente/.ai/context/stakeholders.md) si introduce un nuevo perfil de usuario
- [ ] Actualizar Swagger annotations si afecta endpoints documentados

## Paso 7 — Frontend

Notificar al equipo frontend para:
- Agregar el rol al composable `usePermissions()`
- Agregar items de menú condicional según el nuevo rol/permiso
- Actualizar layouts si el rol nuevo necesita uno distinto

## Reglas

- **Granularidad fina:** preferir permisos atómicos sobre roles grandes.
  - ❌ Rol `SUPER_ADMIN` con todo
  - ✅ Permisos `USER_CREATE`, `USER_UPDATE`, `MEMBER_VIEW_ALL`, etc.
- **No hardcodear roles** en `@PreAuthorize`. Preferir permisos (`hasAuthority('X')`) sobre roles (`hasRole('Y')`).
- **Cambios de permisos** invalidan los JWTs existentes con claims viejos. Considerar refresh forzado si el cambio es crítico.

## Referencias

- [`../specs/04-security.md`](../specs/04-security.md)
- [`../specs/05-roles-permissions.md`](../specs/05-roles-permissions.md)
- [hub `03-security.md` sección "Autorización"](../../../centro-optico-vicente/.ai/specs/03-security.md)
- [hub `stakeholders.md`](../../../centro-optico-vicente/.ai/context/stakeholders.md)
