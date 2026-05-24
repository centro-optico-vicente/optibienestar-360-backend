# Alcance 1 — Seguridad y Autenticación

> Desbloquea todos los demás: sin login no hay panel de administración.
> Índice: [../../checklist-vertical.md](../../checklist-vertical.md)

## Migraciones

- [x] [P0/C2] `V5__users_and_roles.sql` — security_policies, roles, permissions, users, user_password_history, user_roles, role_permissions, user_sessions_log
- [x] [P0/C2] `V6__seed_roles.sql` — SYSTEM, ADMINISTRADOR, OPERADOR, OPERADOR_MEDICO, ALIADO, AFILIADO, PROMOTOR + 49 permisos + asignación rol→permisos

## Hardening de base de datos

- [x] [P1/C1] `V3__app_roles.sql` — `REVOKE ALL ON SCHEMA app FROM PUBLIC` — impide que cualquier usuario autenticado acceda al esquema por defecto
- [x] [P1/C1] `V3__app_roles.sql` — `ALTER ROLE … SET search_path = app, public` para los 3 roles — previene inyección de esquema (CVE-2018-1058)
- [ ] [P1/C1] `V3__app_roles.sql` — `CONNECTION LIMIT` por rol (`app=50`, `migration=5`, `readonly=10`) — evita agotamiento del pool
- [ ] [P1/C1] `V3__app_roles.sql` — `statement_timeout=30s` e `idle_in_transaction_session_timeout=60s` para `optisalud_app` — mata queries colgadas y transacciones idle

## Código

- [x] [P0/C2] Entidades User, Role, Permission, UserRole (JPA) — RolePermission como @ManyToMany en Role
- [x] [P0/C2] Repositorios + UserDetailsServiceImpl + DTOs (UserDto, RoleDto) + UserMapper (MapStruct) — JWT claim renombrado roles→permissions
- [x] [P0/C2] `POST /v1/auth/login` — brute-force tracking, SecurityPolicy, UserSessionLog, Redis refresh token store
- [x] [P0/C2] `POST /v1/auth/refresh` — valida JTI en Redis, rota tokens
- [x] [P0/C2] `POST /v1/auth/logout` — blacklist JTI en Redis, revoca refresh token, actualiza UserSessionLog
- [x] [P0/C2] `POST /v1/auth/recover-password` + `reset-password` — token SHA-256, historial de contraseñas, revoca refresh tokens
- [x] [P0/C2] `GET /v1/me` — retorna UserDto del usuario autenticado
- [x] [P0/C3] `/v1/admin/users` CRUD + RSQL — filtro dinámico con rsql-jpa-spring-boot-starter
- [x] [P1/C2] `POST /v1/me/change-password` — verifica contraseña actual, historial, revoca refresh tokens
- [x] [P1/C2] Anti-brute-force (lock IP + identifier) — configurable via SecurityPolicy (max_login_attempts, lockout_duration_minutes)
