# ADR 0006 (local backend) — Convenciones de búsqueda en repositorios JPA

**Estado:** Aceptado  
**Fecha:** 2026-05-23  
**Relacionado con:** [ADR 0005 — Convención de identificadores](0005-table-id-convention.md)

## Contexto

Todo repositorio JPA debe exponer métodos de búsqueda consistentes para que el resto del código no dependa del `id` interno (`BIGINT`) ni deba conocer la estructura interna de cada tabla. Las reglas deben ser predecibles y aplicables automáticamente al crear cualquier entidad nueva.

## Orden de prioridad de búsqueda

| Prioridad | Método | Cuándo usarlo |
|---|---|---|
| 1 | `findById(Long id)` — **heredado de `JpaRepository`** | Solo en operaciones internas JPA (FKs, joins, carga lazy). Nunca en lógica de negocio expuesta. |
| 2 | `findByUuid(UUID uuid)` | Toda operación iniciada desde la API REST o la capa de servicio. Es el identificador externo canónico. |
| 3 | `findByName/Code/Email(...)` | Cuando la entrada viene de un humano o de otro sistema que conoce el código de negocio. |

> `findById` está disponible en todos los repositorios sin declararlo. Se usa internamente (ej. resolver una FK antes de hacer un `save`), pero **el código de servicio nunca recibe ni expone el `Long id` directamente** — siempre trabaja con `UUID` en la interfaz pública.

## Decisión

### Regla 1 — Entidades con `uuid` (tablas maestro)

Toda entidad que extienda `BaseEntity` o `BaseAuditEntity` tiene columna `uuid`. Su repositorio **debe** exponer:

```java
Optional<T> findByUuid(UUID uuid);
```

Si además la entidad necesita carga eager de asociaciones (ej. roles → permisos), se agrega una variante con `@EntityGraph`:

```java
@EntityGraph(attributePaths = {"...asociaciones..."})
Optional<T> findWithXxxByUuid(UUID uuid);
```

### Regla 2 — Entidades con código natural (nombre / código de negocio)

Toda entidad con un campo que actúa como identificador de negocio (`name`, `code`, `email`, etc.) **debe** exponer búsqueda por ese campo:

```java
Optional<T> findByName(String name);   // Role, Permission
Optional<T> findByEmail(String email); // User
Optional<T> findByCode(String code);   // futuros: Plan, Ally, Member, etc.
```

El campo que sirve como código de negocio es el que tiene `UNIQUE` en la migración SQL.

### Regla 3 — Tablas detalle / pivot (sin `uuid`)

Las tablas que no tienen `uuid` propio (`user_roles`, `user_password_history`, `user_sessions_log`, etc.) se buscan **por los campos FK que forman su clave compuesta funcional**:

| Tabla | Búsqueda canónica |
|---|---|
| `user_roles` | por **cada FK** (`findByUserId` / `findByRoleId`, con variante `...AndActiveTrue`) y por la **clave compuesta** (`findByUserIdAndRoleId`) |
| `user_password_history` | `findRecentByUserId(Long userId)` (ORDER BY created_at DESC) |
| `user_sessions_log` | por `jti` (`findByJtiAndLogoutAtIsNull`) y por FK `user_id` (`findByUserId`, `findByUserIdAndLogoutAtIsNull`) |
| `role_permissions` | modelado como `@ManyToMany` en `Role` — sin repositorio propio |

> Para una pivot de dos FK (`user_roles`), exponer búsqueda por **ambos** lados de la
> relación, no solo uno: `findByUserId*` para "los roles de un usuario" y
> `findByRoleId*` para "los usuarios de un rol", más `findByUserIdAndRoleId` para
> resolver/reconciliar una asignación puntual sin chocar con el `UNIQUE (user_id, role_id)`.

Nunca exponer el `BIGINT id` interno en métodos públicos del repositorio ni en DTOs.

### Regla 4 — Listados de referencia

Toda entidad con campo `is_active` debe tener:

```java
List<T> findAllByActiveTrue();
```

Para entidades con agrupación por dominio o categoría (`Permission.domain`, futuros catálogos):

```java
List<T> findAllByDomain(String domain);   // Permission
List<T> findAllByCategory(String cat);    // futuros catálogos
```

---

## Tabla resumen — estado actual

| Repositorio | `findByUuid` | Código natural | Por FK compuesta | `findAllByActiveTrue` |
|---|---|---|---|---|
| `UserRepository` | ✅ + variante con `@EntityGraph` | `findByEmailAndActiveTrue` | — | — |
| `RoleRepository` | ✅ | `findByName` | — | ✅ |
| `PermissionRepository` | ✅ | `findByName` | — | ✅ + `findAllByDomain` |
| `SecurityPolicyRepository` | ✅ | — | — | implícito en `findFirstByActiveTrue` |
| `ContactMessageRepository` | ✅ | — | — | — |
| `UserRoleRepository` | sin uuid | — | `findByUserId(AndActiveTrue)`, `findByRoleId(AndActiveTrue)`, `findByUserIdAndRoleId` | — |
| `UserPasswordHistoryRepository` | sin uuid | — | `findRecentByUserId` | — |
| `UserSessionLogRepository` | sin uuid | `findByJtiAndLogoutAtIsNull` | `findByUserId`, `findByUserIdAndLogoutAtIsNull` | — |

---

## Por qué

- Consistencia: cualquier desarrollador sabe qué métodos buscar sin leer la entidad.
- Seguridad: se evita exponer `BIGINT` secuenciales en la API (ver ADR 0005).
- Mantenibilidad: los cambios de schema no filtran al código de servicio que usa `uuid`.

## Consecuencias

- Al crear una entidad nueva que extienda `BaseEntity`/`BaseAuditEntity`, agregar `findByUuid` al repositorio es **obligatorio antes del primer PR**.
- Si la entidad tiene un campo de código de negocio único, agregar `findByCode`/`findByName` en el mismo PR.
- Las tablas pivot sin `uuid` deben documentar aquí su búsqueda canónica al ser creadas.
