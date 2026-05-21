# Plan de ejecución por alcances verticales — Fase 5

> Cada alcance entrega migraciones Flyway + entidades JPA + servicios + endpoints completos.
> El [checklist.md](checklist.md) original se mantiene como referencia; este archivo es el roadmap de ejecución.
>
> Formato `[P/C]`: Prioridad (P0/P1/P2) + Complejidad (C1-C5). Marcar `- [x]` con fecha al completar.

## Dependencias entre alcances

```
1 (Auth) ──────────────────────────────────────────► todos los demás (login requerido)
2 (Catálogos) ──► 3 (Aliados), 4 (Afiliados)
3 (Aliados) ────► 7 (Validador)
4 (Afiliados) ──► 5 (Membresías)
5 (Membresías) ─► 6 (Pagos), 7 (Validador)
6 (Pagos) ──────► 8 (Promotores)
1–8 ────────────► 9 (Notificaciones)
1–9 ────────────► 10 (Optimización / Hardening)
```

---

## Alcance 1 — Seguridad y Autenticación

> Desbloquea todos los demás: sin login no hay panel de administración.

### Migraciones

- [ ] [P0/C2] `V10__users_and_roles.sql` — users, roles, permissions, user_roles, role_permissions
- [ ] [P0/C2] `V11__seed_roles.sql` — ADMIN, OPERADOR, ALIADO_USER, AFILIADO_USER, PROMOTOR

### Código

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

---

## Alcance 2 — Catálogos

> Datos de referencia necesarios para Aliados y Afiliados.

### Migraciones

- [ ] [P0/C2] `V12__catalogs.sql` — countries, states, cities, genders, document_types, marital_statuses, occupations
- [ ] [P0/C3] `V13__health_catalogs.sql` — medical_specialties, service_categories, ally_types

### Código

- [ ] [P0/C2] Entidades + repositorios para cada catálogo
- [ ] [P0/C2] `GET /v1/public/catalogs/*` (lectura pública sin auth)
- [ ] [P0/C2] `CRUD /v1/admin/catalogs/*`
- [ ] [P1/C2] `@Cacheable` TTL 1h en todos los catálogos

---

## Alcance 3 — Aliados

> Directorio de clínicas, farmacias, ambulancias. Requerido antes del Validador.

### Migraciones

- [ ] [P0/C3] `V14__allies.sql` — allies, ally_specialties, ally_services, ally_agreements
- [ ] [P0/C3] `V15__ally_users.sql`

### Código

- [ ] [P0/C2] Entidades Ally, AllyType, MedicalSpecialty, AllyService, AllyAgreement, AllyUser
- [ ] [P0/C2] Repos + Services (findByDocument, searchByLocationAndSpecialty)
- [ ] [P0/C2] DTOs (Create, Update, ListItem, Detail, Agreement)
- [ ] [P0/C3] `/v1/admin/allies` CRUD + RSQL
- [ ] [P0/C2] `/v1/admin/allies/{id}/specialties|services|agreements|users`
- [ ] [P0/C2] `GET /v1/public/allies` (directorio público)
- [ ] [P0/C2] `GET /v1/public/allies/{id}`
- [ ] [P0/C2] Upload logo aliado → StorageService → R2

---

## Alcance 4 — Afiliados y Familia (Suscriptores)

> Titular + beneficiarios + historial médico.

### Migraciones

- [ ] [P0/C3] `V18__members.sql` + member_documents
- [ ] [P0/C3] `V19__beneficiaries.sql`
- [ ] [P0/C3] `V20__medical_records.sql`
- [ ] [P1/C2] Índice GIN full-text `members.full_name` con unaccent

### Código

- [ ] [P0/C2] Entidades Member, Beneficiary, MemberDocument, MedicalRecord
- [ ] [P0/C3] Validaciones custom: cédula VE (V-/E-), edad mín 18, máx 3 beneficiarios
- [ ] [P0/C3] `/v1/admin/members` CRUD + RSQL
- [ ] [P0/C2] `/v1/admin/members/{id}/beneficiaries` CRUD
- [ ] [P0/C3] `/v1/admin/members/{id}/medical-record` (`@PreAuthorize`)
- [ ] [P0/C2] `POST /v1/admin/members/{id}/upload-document`
- [ ] [P0/C3] `/v1/me/member`
- [ ] [P0/C2] Anonimización en queries de aliados (no expone MedicalRecord)

---

## Alcance 5 — Planes y Membresías

> Núcleo del negocio: suscripción + ciclo ACTIVE → SUSPENDED → EXPIRED.

### Migraciones

- [ ] [P0/C3] `V16__plans.sql`
- [ ] [P0/C2] `V17__seed_plans.sql` — plan personal $10 inscripción / $5 titular / $5 beneficiario
- [ ] [P0/C3] `V21__memberships.sql`
- [ ] [P0/C2] Índices: `memberships(status, next_due_date)`

### Código

- [ ] [P0/C2] Entidades Plan, Membership
- [ ] [P0/C2] `/v1/admin/plans` CRUD
- [ ] [P0/C2] `POST /v1/admin/members/{id}/memberships`
- [ ] [P0/C2] `PUT /v1/admin/memberships/{id}/cancel|reactivate`
- [ ] [P0/C3] `MembershipStatusService` (ACTIVE/SUSPENDED/EXPIRED según pagos + grace period)
- [ ] [P0/C3] Job diario `@Scheduled` — actualiza status + dispara notificación
- [ ] [P1/C2] `GET /v1/admin/memberships/dashboard` KPIs

---

## Alcance 6 — Pagos manuales

> Flujo: registro → revisión humana → aprobación/rechazo + email + comisión.

### Migraciones

- [ ] [P0/C3] `V22__payments.sql`
- [ ] [P0/C2] Índice: `payments(status)`

### Código

- [ ] [P0/C2] Entidad Payment
- [ ] [P0/C3] `POST /v1/admin/payments` (multipart soporte → R2)
- [ ] [P0/C3] `PUT /v1/admin/payments/{id}/approve`
- [ ] [P0/C2] `PUT /v1/admin/payments/{id}/reject`
- [ ] [P0/C2] `GET /v1/admin/payments` + RSQL
- [ ] [P0/C2] `GET /v1/admin/payments/{id}/support` (presigned URL)
- [ ] [P0/C2] `GET /v1/me/payments`
- [ ] [P0/C2] Templates `payment-received.html` + `payment-approved.html` + `payment-rejected.html`

---

## Alcance 7 — Validador en tiempo real

> **CRÍTICO: p95 < 200ms.** El aliado verifica vigencia del afiliado antes de aplicar descuento.

### Migraciones

- [ ] [P0/C3] `V23__benefit_usages.sql`
- [ ] [P0/C2] Índices en `benefit_usages` según EXPLAIN ANALYZE

### Código

- [ ] [P0/C3] `GET /v1/ally/validate/{document}` — cache Redis `validator:{document}` TTL 60s
- [ ] [P0/C3] Invalidación de cache al cambiar status de membresía o aprobar/rechazar pago
- [ ] [P0/C2] `POST /v1/ally/benefit-usage`
- [ ] [P0/C2] `GET /v1/ally/usage-history`
- [ ] [P0/C2] Métricas Redis: contador validaciones/día por aliado
- [ ] [P1/C2] Rate limit por aliado (1 000 validaciones/día default)
- [ ] [P1/C3] `GET /v1/admin/usage-metrics`

---

## Alcance 8 — Promotores, Comisiones y Referidos

> Red de ventas: comisión automática al aprobar pago inicial + códigos de descuento.

### Migraciones

- [ ] [P0/C3] `V24__promoters.sql`
- [ ] [P0/C3] `V25__commissions.sql`
- [ ] [P0/C3] `V26__referrals.sql`

### Código

- [ ] [P0/C2] Entidades Promoter, Commission, Referral
- [ ] [P0/C3] `/v1/admin/promoters` CRUD
- [ ] [P0/C3] `CommissionService` — calcula al aprobar pago inicial (PERCENT o FLAT por tipo de plan)
- [ ] [P0/C2] `GET /v1/admin/commissions`
- [ ] [P0/C2] `POST /v1/admin/commissions/payout` — cierre de ciclo + CSV + email promotor
- [ ] [P0/C2] `GET /v1/promoter/dashboard`
- [ ] [P0/C3] `ReferralService` — valida código + aplica descuento al referrer
- [ ] [P0/C2] `POST /v1/admin/referral-codes`
- [ ] [P0/C2] `GET /v1/me/referrals`

---

## Alcance 9 — Notificaciones y Carnet digital

> Cola persistente de emails + jobs diarios de vencimientos + QR del afiliado.

### Migraciones

- [ ] [P0/C2] `V27__notifications.sql`
- [ ] [P0/C2] `V28__digital_cards_view.sql`

### Código

- [ ] [P1/C2] Cola async `@Async` + reintentos exponenciales (Tarea 1.8 pendiente)
- [ ] [P0/C2] Entidad Notification (cola persistente)
- [ ] [P0/C3] `NotificationService`: enqueue + envío async + reintentos
- [ ] [P0/C2] Templates: `welcome.html`, `payment-reminder.html`, `payment-overdue.html`, `membership-suspended.html`, `membership-expired.html`, `referral-reward.html`, `commission-payout.html`
- [ ] [P0/C3] Job diario — detecta vencimientos próximos (3 días antes de `next_due_date`)
- [ ] [P0/C3] Job diario — detecta vencidos en período de gracia
- [ ] [P0/C2] `GET /v1/me/digital-card` + QR generado
- [ ] [P0/C2] `GET /v1/me/family` + `/v1/me/usage-history`

---

## Alcance 10 — Optimización, Reportes y Hardening

> Cierre de Fase 5. Requiere datos reales para EXPLAIN y cobertura de tests.

### Optimización (Tarea 5.10)

- [ ] [P1/C3] EXPLAIN ANALYZE queries críticas (validate, memberships listing, payments)
- [ ] [P1/C2] Índices adicionales según EXPLAIN
- [ ] [P1/C3] Tuning HikariCP (pool size, timeout, keepalive)
- [ ] [P1/C3] Paginación obligatoria (default 20, max 100) en todos los listados
- [ ] [P1/C2] Lazy loading + `@EntityGraph` / JOIN FETCH (evitar N+1)
- [ ] [P2/C3] Vista materializada `member_metrics_mv` refresh 1h
- [ ] [P2/C3] Tests k6: 1 000 validaciones concurrentes, p95 < 200ms

### Reportes (Tarea 5.11)

- [ ] [P1/C3] `GET /v1/admin/reports/memberships|allies|commissions`
- [ ] [P1/C2] `GET /v1/admin/reports/export?type=...&format=csv`

### Hardening y QA (Tarea 5.12)

- [ ] [P0/C3] OWASP Top 10 audit con skill
- [ ] [P0/C2] CSP estricto
- [ ] [P0/C2] Validación uploads (MIME real, tamaño máximo)
- [ ] [P0/C2] Audit logs para acciones sensibles (aprobación pagos, acceso a MedicalRecord)
- [ ] [P1/C3] Tests integrales: 70% coverage servicios críticos
- [ ] [P1/C3] Tests E2E con Testcontainers
