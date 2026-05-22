# Alcance 1 — Seguridad y Autenticación

> Desbloquea todos los demás: sin login no hay panel de administración.
> Índice: [../../checklist-vertical.md](../../checklist-vertical.md)

## Migraciones

- [ ] [P0/C2] `V10__users_and_roles.sql` — users, roles, permissions, user_roles, role_permissions
- [ ] [P0/C2] `V11__seed_roles.sql` — ADMIN, OPERADOR, ALIADO_USER, AFILIADO_USER, PROMOTOR

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
