# Playbook — Agregar nuevo rol o permiso

> **Reescrito 2026-10-10** contra el código real (ver [auditoría 2026-09-23](../notes/2026-09-23_audit.md), recomendación #4) — el SQL de ejemplo usaba `UUID` como PK de `roles`/`permissions`/`role_permissions`; la real es `BIGINT` identity + `uuid` separado (ver `V5__users_and_roles.sql`, `V6__seed_roles.sql`). También se documenta el trigger de auto-grant a `SYSTEM` (V30) que esta versión anterior no mencionaba.

> Para agregar un rol nuevo al sistema o ampliar permisos de uno existente.

## Cuándo

- Necesidad de un perfil de usuario no cubierto por los 5 base (ADMINISTRADOR, OPERADOR, ALIADO, AFILIADO, PROMOTOR)
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

Esquema real (`V5__users_and_roles.sql`): `roles`/`permissions` tienen PK `BIGINT` (`roles_id`/`permissions_id`) + `uuid` separado, columna `name` (no `code`), y **no** tienen columna `status` (solo `is_active`). `role_permissions` es una tabla pivot pura — sin `is_active`/`status`, solo audit columns. `permissions.domain_id` es obligatorio (FK a `permission_domains`, ver `new-entity.md` Paso 7). El patrón real completo de agregar permisos nuevos está en `V119__payment_catalogs_permissions_and_audit.sql`.

```sql
-- V{N}__add_role_operador_medico.sql
SET search_path TO app, public;

-- Insertar permisos nuevos (domain_id resuelto por code del dominio existente)
INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('MEDICAL_RECORD_VIEW',   'MEMBERS', 'Ver antecedentes médicos'),
    ('MEDICAL_RECORD_UPDATE', 'MEMBERS', 'Actualizar antecedentes médicos')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code
ON CONFLICT (name) DO NOTHING;

-- Insertar nuevo rol
INSERT INTO roles (name, description)
VALUES ('OPERADOR_MEDICO', 'Operador con acceso a antecedentes médicos')
ON CONFLICT (name) DO NOTHING;

-- Copiar todos los permisos de OPERADOR al nuevo rol
INSERT INTO role_permissions (role_id, permission_id)
SELECT (SELECT roles_id FROM roles WHERE name = 'OPERADOR_MEDICO'), rp.permission_id
FROM role_permissions rp
JOIN roles r ON rp.role_id = r.roles_id
WHERE r.name = 'OPERADOR'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Más los permisos específicos del nuevo rol
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r CROSS JOIN permissions p
WHERE r.name = 'OPERADOR_MEDICO'
  AND p.name IN ('MEDICAL_RECORD_VIEW', 'MEDICAL_RECORD_UPDATE')
ON CONFLICT (role_id, permission_id) DO NOTHING;
```

**`SYSTEM` no necesita que se lo mencione acá** — un trigger (`trg_permissions_grant_system`, `V30__system_role_permission_autosync.sql`) le auto-otorga cualquier permiso nuevo insertado, de ahora en más, sin que ninguna migración futura tenga que recordarlo. El nuevo ROL `OPERADOR_MEDICO` sí hay que crearlo y asignarle permisos explícitamente — el trigger solo cubre permisos nuevos hacia `SYSTEM`, no roles nuevos.

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

## Paso 4 — Audit log para acciones sensibles

**Corrección:** `@Auditable` (ver `new-entity.md` Paso 5) solo cubre `AuditAction.CREATE/UPDATE/DELETE` — no existe una anotación `@AuditAction` standalone, y **no hay auditoría automática de lectura (VIEW)** en el mecanismo actual. Prueba real: `MedicalRecordService.getForMember()` (lectura) no tiene ningún `@Auditable`; solo `update()` y `delete()` lo llevan:

```java
@Auditable(entity = "medical_record", action = AuditAction.UPDATE)
public MedicalRecordDto update(UUID memberUuid, MedicalRecordUpsertRequest req) { /* ... */ }

@Auditable(entity = "medical_record", action = AuditAction.DELETE, uuidArgIndex = 0)
public void delete(UUID memberUuid) { /* ... */ }
```

Si el rol/permiso nuevo necesita auditar también el **acceso de lectura** a datos sensibles (no solo mutaciones), hoy no hay un mecanismo genérico para eso — es una decisión de diseño nueva (ej. loguear manualmente en el service, o extender `AuditAction` con un valor `VIEW` y el aspecto que lo intercepta), no algo que se pueda copiar de un ejemplo existente.

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
