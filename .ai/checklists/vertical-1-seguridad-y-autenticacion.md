# Alcance 1 — Seguridad y Autenticación

> Desbloquea todos los demás: sin login no hay panel de administración.
> Índice: [../../checklist-vertical.md](../../checklist-vertical.md)

## Migraciones

- [x] [P0/C2] `V5__users_and_roles.sql` — security_policies, roles, permissions, users, user_password_history, user_roles, role_permissions, user_sessions_log
- [x] [P0/C2] `V6__seed_roles.sql` — SYSTEM, ADMINISTRADOR, OPERADOR, OPERADOR_MEDICO, ALIADO, AFILIADO, PROMOTOR + 49 permisos + asignación rol→permisos

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

- [ ] [P1/C1] `UserRole.role` — cambiar `FetchType.EAGER` → `LAZY`; el `@EntityGraph` existente cubre los casos que lo necesitan (spring-data-jpa / postgresql-expert)
- [ ] [P1/C2] Todos los repositorios del módulo auth — agregar `@Transactional(readOnly=true)` en todos los métodos SELECT derivados para activar dirty-check skip y connection readOnly (spring-data-jpa)
- [ ] [P1/C2] `TokenBlacklistService.storeRefreshToken()` — hacer atómica la secuencia `opsForSet().add()` + `expire()` usando `executePipelined` o `MULTI/EXEC` — evita TTL perdido si Redis falla entre las dos operaciones (redis-expert)
- [x] [P1/C1] `AdminCreateUserRequest` + `AdminUpdateUserRequest` — `@Pattern("^[VE]$")` en `documentType` — devuelve 400 claro en vez del 409 engañoso que daba la violación del CHECK de la BD (owasp-security A01)
- [ ] [P1/C1] `AdminUpdateUserRequest` — agregar `@Pattern` en `status` (`ACTIVE|SUSPENDED|LOCKED`) — previene valores arbitrarios sin validación (owasp-security A01)

## Evaluación — RBAC dinámico (roles editables) *(spike, no comprometido)*

> Extiende el `GET /v1/admin/roles` read-only actual a gestión completa. La idea: los **permisos siguen fijos en código** (cada uno referenciado por algún `@PreAuthorize`), y los **roles pasan a ser contenedores editables** de esos permisos. Evaluar viabilidad y decidir antes de implementar.

- [ ] [P2/C2] **Spike compatibilidad `@PreAuthorize`** — confirmar que la autorización ya es **por permiso** (`hasAuthority('PERM')`), no por rol → roles editables NO rompen los `@PreAuthorize`. Restricción clave: el **catálogo de permisos permanece code-bound** (crear permisos sin un `@PreAuthorize` que los use es inútil) → NO habrá CRUD de permisos, solo de roles.
- [ ] [P2/C2] `GET /v1/admin/permissions` (read-only) — catálogo de permisos para que el panel arme/edite los roles. Sin CRUD.
- [ ] [P2/C3] CRUD de roles + reasignación rol↔permisos — `POST/PUT/DELETE /v1/admin/roles` + endpoint para setear `role_permissions`. Validar nombre único; soft-delete si el rol tiene usuarios asignados.
- [ ] [P2/C3] **Token staleness** — al cambiar permisos de un rol, los `accessToken` activos (TTL 15 min) cargan permisos viejos hasta el refresh. Decidir: aceptar la ventana de 15 min, o forzar invalidación (revocar refresh tokens / blacklist) de los usuarios afectados.
- [ ] [P2/C2] **Guards anti-lockout** — proteger rol `SYSTEM` (no borrable / no editable) y evitar que un admin se quite a sí mismo permisos críticos o se deje sin acceso.
