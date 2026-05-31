# Alcance 1 — Seguridad y Autenticación

> Desbloquea todos los demás: sin login no hay panel de administración.
> Índice: [../../checklist-vertical.md](../../checklist-vertical.md)

## Migraciones

- [x] [P0/C2] `V5__users_and_roles.sql` — security_policies, roles, **permission_domains** (10 dominios UI con `code`/`name`/`icon`/`display_order`), permissions (con `domain_id` FK), users, user_password_history, user_roles, role_permissions, user_sessions_log
- [x] [P0/C2] `V6__seed_roles.sql` — 7 roles seed (SYSTEM, ADMINISTRADOR, OPERADOR, OPERADOR_MEDICO, ALIADO, AFILIADO, PROMOTOR) + **50 permisos** (incluye `ROLE_PERMISSION_EDIT`) + asignación rol→permisos (SYSTEM=50, ADMIN=48, OP_MEDICO=45, OPERADOR=43, AFILIADO/PROMOTOR=5, ALIADO=3)

## Hardening de base de datos

- [x] [P1/C1] `V3__app_roles.sql` — `REVOKE ALL ON SCHEMA app FROM PUBLIC` — impide que cualquier usuario autenticado acceda al esquema por defecto
- [x] [P1/C1] `V3__app_roles.sql` — `ALTER ROLE … SET search_path = app, public` para los 3 roles — previene inyección de esquema (CVE-2018-1058)
- [x] [P1/C1] `V3__app_roles.sql` — `CONNECTION LIMIT` por rol (`app=50`, `migration=5`, `readonly=10`) — evita agotamiento del pool
- [x] [P1/C1] `V3__app_roles.sql` — `statement_timeout=30s` e `idle_in_transaction_session_timeout=60s` para `optisalud_app` — mata queries colgadas y transacciones idle

## Código

- [x] [P0/C2] Entidades User, Role, Permission, UserRole (JPA) — RolePermission como @ManyToMany en Role
- [x] [P0/C2] Repositorios + UserDetailsServiceImpl + DTOs (UserDto, RoleDto) + UserMapper (MapStruct) — JWT claim renombrado roles→permissions
- [x] [P0/C2] `POST /v1/auth/login` — brute-force tracking, SecurityPolicy, UserSessionLog, Redis refresh token store
- [x] [P0/C2] `POST /v1/auth/refresh` — valida JTI en Redis, rota tokens
- [x] [P0/C2] `POST /v1/auth/logout` — blacklist JTI en Redis, revoca refresh token, actualiza UserSessionLog
- [x] [P0/C2] `POST /v1/auth/recover-password` + `reset-password` — token SHA-256, historial de contraseñas, revoca refresh tokens
- [x] [P0/C2] `GET /v1/me` — retorna UserDto del usuario autenticado
- [x] [P0/C3] `/v1/admin/users` CRUD + RSQL — filtro dinámico con rsql-jpa-spring-boot-starter
- [x] [P1/C1] `GET /v1/admin/roles` + `/{uuid}` — lectura de roles activos (AdminRoleController/RoleService) para poblar selects del panel; sin CRUD (roles son seed fijo en V6)
- [x] [P1/C2] `POST /v1/me/change-password` — verifica contraseña actual, historial, revoca refresh tokens
- [x] [P1/C2] Anti-brute-force (lock IP + identifier) — configurable via SecurityPolicy (max_login_attempts, lockout_duration_minutes)

## Hardening adicional (hallazgos audit multi-skill)

- [x] [P1/C1] `UserRole.role` — cambiar `FetchType.EAGER` → `LAZY`; el `@EntityGraph` existente cubre los casos que lo necesitan (spring-data-jpa / postgresql-expert). Aplicado: los 4 callers críticos (login, refresh, getMe, admin user get/create/update) usan `findByEmailAndActiveTrue` / `findWithRolesByUuid` con `@EntityGraph` que carga `userRoles.role.permissions`. Único caso sin EntityGraph: `UserService.list` con `findAll(spec, pageable)` — corre dentro de `@Transactional`, así que LAZY no rompe pero queda con N+1 (un SELECT por user para sus roles) → trade-off aceptable mientras el listado admin sea infrecuente; mitigar más adelante si pesa.
- [x] [P1/C2] Todos los repositorios del módulo auth — agregar `@Transactional(readOnly=true)` en todos los métodos SELECT derivados para activar dirty-check skip y connection readOnly (spring-data-jpa). **Aplicado** a nivel de interfaz (más limpio que método por método) en los 8 repos: `UserRepository`, `RoleRepository`, `PermissionRepository`, `PermissionDomainRepository`, `UserRoleRepository`, `UserPasswordHistoryRepository`, `UserSessionLogRepository`, `SecurityPolicyRepository`. Las queries `@Modifying` (solo `UserPasswordHistoryRepository.pruneOlderThan`) llevan `@Transactional` (write) método-level para sobreescribir el class-level. Las mutators de `SimpleJpaRepository` (`save`, `delete`, `deleteById`) ya vienen anotadas write desde Spring Data, por lo que el override jerárquico de Spring las preserva sin esfuerzo extra. Beneficio mecánico: Hibernate `FlushMode.MANUAL` (skip dirty-check), `Connection.setReadOnly(true)` (Postgres rechaza writes accidentales), y forward-compat para `AbstractRoutingDataSource` cuando aprovisionemos réplica de lectura.
- [x] [P1/C2] `TokenBlacklistService.storeRefreshToken()` — hacer atómica la secuencia `opsForSet().add()` + `expire()` usando `executePipelined` o `MULTI/EXEC` — evita TTL perdido si Redis falla entre las dos operaciones (redis-expert). **Aplicado**: las 3 operaciones (`SET refresh:<jti>`, `SADD user_refresh:<uuid>`, `EXPIRE user_refresh:<uuid>`) ahora viajan en una transacción `MULTI/EXEC` vía `SessionCallback`. Cierra dos ventanas: (a) `SADD` + `EXPIRE` sin atomicidad (el SET podría quedar sin TTL → leak permanente) y (b) `SET refresh` ya escrito pero `SADD` no ejecutado (token usable pero invisible a `revokeAllUserRefreshTokens`). Pipelining solo da el round-trip combinado sin atomicidad; MULTI/EXEC es la forma correcta.
- [x] [P1/C1] `AdminCreateUserRequest` + `AdminUpdateUserRequest` — `@Pattern("^[VE]$")` en `documentType` — devuelve 400 claro en vez del 409 engañoso que daba la violación del CHECK de la BD (owasp-security A01)
- [x] [P1/C1] `AdminUpdateUserRequest` — agregar `@Pattern` en `status` (`ACTIVE|SUSPENDED|LOCKED`) — previene valores arbitrarios sin validación (owasp-security A01). **Aplicado**: `@Pattern(regexp = "^(ACTIVE|SUSPENDED|LOCKED)$", message = "status must be 'ACTIVE', 'SUSPENDED' or 'LOCKED'")` con el mismo set de valores que el `CHECK` de BD en `V5__users_and_roles.sql`. El campo sigue siendo nullable (no `@NotBlank`) porque PATCH-style: omitirlo significa "no cambiar el status". Antes: un PUT con `status: "DROPPED"` reventaba en BD con 409 misleading; ahora devuelve 400 con mensaje claro.
- [x] [P0/C2] `V3__app_roles.sql` — los `ALTER DEFAULT PRIVILEGES FOR ROLE optisalud_migration` solo cubrían el caso de Flyway corriendo como `optisalud_migration`. Con el default `DATABASE_MIGRATION_USER=postgres`, V4+ creaban tablas con owner `postgres` y los defaults no aplicaban → `optisalud_app` recibía `permission denied for table users`. **Fix aplicado** (opción a): agregadas 4 líneas `ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA app GRANT … TO optisalud_app / optisalud_readonly` después de las existentes; ahora V3 cubre ambos roles de bootstrap. Verificado dropeando + recreando la BD local: `optisalud_app` lee `users` sin GRANTs manuales y `optisalud_readonly` recibe correctamente 403 al intentar INSERT.
- [x] [P1/C1] `AuthService.resolveClientIp()` — `HttpServletRequest.getRemoteAddr()` devuelve `[0:0:0:0:0:0:0:1]` (con corchetes) para IPv6 localhost; Postgres `inet` rechaza ese formato y `UserSessionLog.ip_address` revienta con 409 al cierre del login local desde `::1`. **Aplicado** (opción b): el método ahora detecta el patrón `[...]` y quita los corchetes antes de pasarlo a la entidad. Sin imports nuevos, sin lookup de `InetAddress.getByName()`. Curl/clients que vengan por IPv4 (`127.0.0.1`) o por hostname que resuelva IPv6 sin corchetes pasan sin tocar; solo se modifica la string si lleva el wrap `[…]`.

## Evaluación — RBAC con permisos por rol editables *(spike, no comprometido)*

> Extiende el `GET /v1/admin/roles` read-only actual a gestión completa. La idea: los **permisos siguen fijos en código** (cada uno referenciado por algún `@PreAuthorize`), y los **roles pasan a ser contenedores editables** de esos permisos. Evaluar viabilidad y decidir antes de implementar.

- [x] [P2/C2] **Spike compatibilidad `@PreAuthorize`** — **confirmado por auditoría**: los 8 `@PreAuthorize` usan `hasAuthority('PERM')`; cero usos de `hasRole`/`@Secured`/`@RolesAllowed`/`RoleHierarchy`; cero hardcodes de nombres de rol en lógica; `SecurityConfig` solo exige `authenticated()`; `JwtAuthenticationFilter` mapea permisos directo sin prefijo `ROLE_`. Roles editables son compatibles sin tocar autorización. Restricción confirmada: catálogo de permisos permanece **code-bound** (no habrá CRUD de permisos, solo de roles).
- [x] [P2/C2] `GET /v1/admin/permissions` (read-only) — catálogo de permisos para que el panel arme/edite los roles. Sin CRUD.
- [ ] [P2/C3] CRUD de roles + reasignación rol↔permisos — `POST/PUT/DELETE /v1/admin/roles` + endpoint para setear `role_permissions`. Validar nombre único; soft-delete si el rol tiene usuarios asignados.
- [ ] [P2/C3] **Token staleness** — al cambiar permisos de un rol, los `accessToken` activos (TTL 15 min) cargan permisos viejos hasta el refresh. Decidir: aceptar la ventana de 15 min, o forzar invalidación (revocar refresh tokens / blacklist) de los usuarios afectados.
- [ ] [P2/C2] **Guards anti-lockout** — proteger rol `SYSTEM` (no borrable / no editable) y evitar que un admin se quite a sí mismo permisos críticos o se deje sin acceso.

## RBAC con permisos por rol editables — Implementación (panel friendly para no-técnicos)

> Fase mínima del spike: catálogo de permisos navegable + edición de `role_permissions` por rol existente, sin CRUD de roles. Se promueve `permission.domain` (texto libre) a tabla `permission_domains` con `label`/`icon`/`display_order` para que el panel muestre nombres en español agrupados por módulo. Decisión de staleness: aceptar ventana de 15 min (TTL del access). El admin nunca ve nombres técnicos (`MEMBER_CREATE`, `MEMBERS`) — ve `description` en español y `label` del dominio.

### Migraciones

> Las migraciones de schema (`permission_domains`) y catálogo (`ROLE_PERMISSION_EDIT`) se consolidaron dentro de **V5** y **V6** respectivamente — ver primera sección "Migraciones" arriba. No hay V11/V12 dedicadas; los slots quedan libres para Fase 2 (`allies`, `ally_users`).

### Código — entidades y DTOs

- [x] [P2/C1] `entity/PermissionDomain.java` (hereda `BaseAuditEntity`) + `repository/PermissionDomainRepository.java` con `findAllByActiveTrueOrderByDisplayOrder()`
- [x] [P2/C1] Refactor `entity/Permission.java` — reemplazar `String domain` por `@ManyToOne(fetch=LAZY) @JoinColumn(name="domain_id") PermissionDomain domain`
- [x] [P2/C1] `dto/PermissionDomainDto` (uuid, code, label, icon, description, displayOrder, permissions[]) + `dto/PermissionDto` (uuid, label, description) — **NO** exponer `name` técnico al panel
- [x] [P2/C1] `dto/UpdateRolePermissionsRequest` con `@NotNull List<UUID> permissionUuids`

### Código — servicios

- [x] [P2/C2] `PermissionService.getCatalog()` — devuelve `List<PermissionDomainDto>` con permisos anidados, ordenado por `display_order` (dominio) y `description` (permiso)
- [x] [P2/C2] `RoleService.updateRolePermissions(roleUuid, Set<UUID> permissionUuids)` — guard SYSTEM (403 vía `AccessDeniedException`); anti-lockout (rechaza con 422 si el actor se quita `ROLE_PERMISSION_EDIT` a sí mismo — compara perms del actor cargando el rol editado con el nuevo set y los demás roles tal cual); valida que todos los uuids existen (422 con la lista de los que faltan); reemplaza el set vía `role.getPermissions().clear() + addAll()` (preserva la instancia de la colección — patrón Hibernate recomendado para `@ManyToMany`).
- [x] [P2/C1] `RoleService.getRolePermissions(roleUuid): Set<UUID>` — devuelve los uuids del set actual para precargar checkboxes del panel.

### Endpoints

- [x] [P2/C1] `PermissionController.GET /v1/admin/permissions` — devuelve catálogo completo (tree dominios→permisos); `@PreAuthorize("hasAuthority('ROLE_PERMISSION_EDIT')")`
- [x] [P2/C1] `AdminRoleController.GET /v1/admin/roles/{uuid}/permissions` — devuelve `Set<UUID>` del set actual del rol; `@PreAuthorize("hasAuthority('ROLE_PERMISSION_EDIT')")`.
- [x] [P2/C2] `AdminRoleController.PUT /v1/admin/roles/{uuid}/permissions` — recibe `@Valid UpdateRolePermissionsRequest` + `@AuthenticationPrincipal CustomUserDetails`; delega a `roleService.updateRolePermissions(uuid, set, actor.getUuid())` que aplica los guards; devuelve **204 No Content** en éxito; `@PreAuthorize("hasAuthority('ROLE_PERMISSION_EDIT')")`.

### Tests

- [ ] [P2/C2] Integración `PermissionControllerIT` — GET catálogo devuelve 10 dominios ordenados por `display_order` con 49 permisos repartidos; sin token devuelve 401; con token sin `ROLE_PERMISSION_EDIT` devuelve 403
- [ ] [P2/C2] Integración `AdminRoleControllerIT` — PUT exitoso a rol no-SYSTEM aplica cambio (verificable con GET siguiente); PUT a rol SYSTEM devuelve 403 con mensaje claro; PUT con permissionUuid inexistente devuelve 400; PUT que dejaría al actor sin `ROLE_PERMISSION_EDIT` devuelve 400 con mensaje "auto-lockout"

### Documentación

- [ ] [P2/C1] `playbooks/edit-role-permissions.md` — flujo operativo del admin (paso a paso desde el panel); documentar la ventana de propagación de 15 min y cómo forzar refresh inmediato si urge (revocar `refresh:<user_uuid>` en Redis con `redis-cli DEL`)
- [ ] [P2/C1] Actualizar `.ai/specs/05-roles-permissions.md` — marcar la fase mínima como implementada; documentar la nueva tabla `permission_domains`, los 3 endpoints nuevos y el permiso `ROLE_PERMISSION_EDIT`
