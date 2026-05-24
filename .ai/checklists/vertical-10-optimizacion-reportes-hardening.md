# Alcance 10 — Optimización, Reportes y Hardening

> Cierre de Fase 2. Requiere datos reales para EXPLAIN y cobertura de tests.
> Índice: [../../checklist-vertical.md](../../checklist-vertical.md)

## Optimización

- [ ] [P1/C3] EXPLAIN ANALYZE queries críticas (validate, memberships listing, payments)
- [ ] [P1/C2] Índices adicionales según EXPLAIN
- [ ] [P1/C3] Tuning HikariCP (pool size, timeout, keepalive)
- [ ] [P1/C3] Paginación obligatoria (default 20, max 100) en todos los listados — incluye: `findRecentByUserId` (sin LIMIT actual), `findAllByActiveTrue` en roles/permissions (spring-data-jpa)
- [ ] [P1/C1] `user_sessions_log.jti` — cambiar índice B-tree → HASH (`USING HASH`) para lookups de igualdad pura (jti equality-only) — migración V(N) (postgresql-expert)
- [ ] [P1/C2] `findByPasswordResetToken` en `UserRepository` — cambiar return type a proyección `UserPasswordResetView` (uuid + passwordResetTokenExpiresAt) — evita cargar entidad completa con roles en flujo de reset (spring-data-jpa)
- [ ] [P1/C2] Lazy loading + `@EntityGraph` / JOIN FETCH (evitar N+1) — incluye: `UserRole.role` EAGER→LAZY (postgresql-expert), revisar cualquier listado RSQL que no use EntityGraph
- [ ] [P2/C3] Vista materializada `member_metrics_mv` refresh 1h
- [ ] [P2/C3] Tests k6: 1 000 validaciones concurrentes, p95 < 200ms

## Reportes

- [ ] [P1/C3] `GET /v1/admin/reports/memberships|allies|commissions`
- [ ] [P1/C2] `GET /v1/admin/reports/export?type=...&format=csv`

## Hardening y QA

- [x] [P0/C3] OWASP Top 10 audit con skill — ejecutado en Vertical 1 (owasp-security + api-security + spring-boot-engineer + redis-expert + postgresql-expert) — 2026-05-24
- [ ] [P0/C2] CSP estricto
- [ ] [P0/C2] Validación uploads (MIME real, tamaño máximo)
- [ ] [P0/C2] Audit logs para acciones sensibles (aprobación pagos, acceso a MedicalRecord)
- [ ] [P1/C3] Tests integrales: 70% coverage servicios críticos
- [ ] [P1/C3] Tests E2E con Testcontainers
