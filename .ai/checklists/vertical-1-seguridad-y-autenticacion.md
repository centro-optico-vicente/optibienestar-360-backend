# Alcance 1 — Seguridad y Autenticación

> Desbloquea todos los demás: sin login no hay panel de administración.
> Índice: [../../checklist-vertical.md](../../checklist-vertical.md)

## Migraciones

- [ ] [P0/C2] `V10__users_and_roles.sql` — users, roles, permissions, user_roles, role_permissions
- [ ] [P0/C2] `V11__seed_roles.sql` — ADMIN, OPERADOR, ALIADO_USER, AFILIADO_USER, PROMOTOR

## Hardening de base de datos

- [x] [P1/C1] `V3__app_roles.sql` — `REVOKE ALL ON SCHEMA app FROM PUBLIC` — impide que cualquier usuario autenticado acceda al esquema por defecto
- [x] [P1/C1] `V3__app_roles.sql` — `ALTER ROLE … SET search_path = app, public` para los 3 roles — previene inyección de esquema (CVE-2018-1058)
- [ ] [P1/C1] `V3__app_roles.sql` — `CONNECTION LIMIT` por rol (`app=50`, `migration=5`, `readonly=10`) — evita agotamiento del pool
- [ ] [P1/C1] `V3__app_roles.sql` — `statement_timeout=30s` e `idle_in_transaction_session_timeout=60s` para `optisalud_app` — mata queries colgadas y transacciones idle

## Código

- [ ] [P0/C2] Entidades User, Role, Permission, UserRole, RolePermission (JPA)
- [ ] [P0/C2] Repositorios + Services + DTOs + Mappers
- [ ] [P0/C2] `POST /v1/auth/login`
- [ ] [P0/C2] `POST /v1/auth/refresh`
- [ ] [P0/C2] `POST /v1/auth/logout` (Redis blacklist)
- [ ] [P0/C2] `POST /v1/auth/recover-password` + `reset-password`
- [ ] [P0/C2] `GET /v1/me`
- [ ] [P0/C3] `/v1/admin/users` CRUD + RSQL
- [ ] [P1/C2] `POST /v1/me/change-password`
- [ ] [P1/C2] Anti-brute-force (lock IP + identifier)
