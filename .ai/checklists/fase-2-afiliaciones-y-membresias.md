# Checklist — FASE 2: Afiliaciones y Membresías

> Formato `[P/C]`: Prioridad (P0/P1/P2/P3) + Complejidad (C1-C5). Marcar `- [x]` con fecha al completar.
> Índice: [../checklist.md](../checklist.md) | Orden de ejecución: [../checklist-vertical.md](../checklist-vertical.md)

## Tarea 2.1 — Modelo de datos (migraciones Flyway)

> Las migraciones se ejecutan por alcance junto a su código — ver [checklist-vertical.md](../checklist-vertical.md).

- [ ] [P0/C2] `V10__users_and_roles.sql`: users, roles, permissions, user_roles, role_permissions
- [ ] [P0/C2] `V11__seed_roles.sql`: ADMIN, OPERADOR, ALIADO_USER, AFILIADO_USER, PROMOTOR
- [ ] [P0/C2] `V12__catalogs.sql`: countries, states, cities, genders, document_types, marital_statuses, occupations
- [ ] [P0/C3] `V13__health_catalogs.sql`: medical_specialties, service_categories, ally_types
- [ ] [P0/C3] `V14__allies.sql`: allies, ally_specialties, ally_services, ally_agreements
- [ ] [P0/C3] `V15__ally_users.sql`
- [ ] [P0/C3] `V16__plans.sql`
- [ ] [P0/C2] `V17__seed_plans.sql`: plan personal $10/$5/$5
- [ ] [P0/C3] `V18__members.sql` + member_documents
- [ ] [P0/C3] `V19__beneficiaries.sql`
- [ ] [P0/C3] `V20__medical_records.sql`
- [ ] [P0/C3] `V21__memberships.sql`
- [ ] [P0/C3] `V22__payments.sql`
- [ ] [P0/C3] `V23__benefit_usages.sql`
- [ ] [P0/C3] `V24__promoters.sql`
- [ ] [P0/C3] `V25__commissions.sql`
- [ ] [P0/C3] `V26__referrals.sql`
- [ ] [P0/C2] `V27__notifications.sql`
- [ ] [P0/C2] `V28__digital_cards_view.sql`
- [ ] [P0/C2] Índices clave: members(document), memberships(status, next_due), benefit_usages, payments(status)
- [ ] [P1/C2] Índice GIN full-text en members.full_name con unaccent

## Tarea 2.2 — Auth + Users

- [ ] [P0/C2] Entidades User, Role, Permission, UserRole, RolePermission (JPA)
- [ ] [P0/C2] Repositorios + Services + DTOs + Mappers
- [ ] [P0/C2] `POST /v1/auth/login`
- [ ] [P0/C2] `POST /v1/auth/refresh`
- [ ] [P0/C2] `POST /v1/auth/logout` (Redis blacklist)
- [ ] [P0/C2] `POST /v1/auth/recover-password` + `reset-password`
- [ ] [P0/C2] `GET /v1/me`
- [ ] [P0/C3] `/v1/admin/users` CRUD + RSQL
- [ ] [P1/C2] `POST /v1/me/change-password`
- [ ] [P1/C2] Anti-brute-force (lock IP+identifier)

## Tarea 2.3 — Aliados

- [ ] [P0/C2] Entidades Ally, AllyType, MedicalSpecialty, AllyService, AllyAgreement, AllyUser
- [ ] [P0/C2] Repos + Services (findByDocument, searchByLocationAndSpecialty)
- [ ] [P0/C2] DTOs (Create, Update, ListItem, Detail, Agreement)
- [ ] [P0/C3] `/v1/admin/allies` CRUD + RSQL
- [ ] [P0/C2] `/v1/admin/allies/{id}/specialties|services|agreements|users`
- [ ] [P0/C2] `GET /v1/public/allies` (directorio público)
- [ ] [P0/C2] `GET /v1/public/allies/{id}`
- [ ] [P0/C2] Upload logo aliado vía StorageService → R2

## Tarea 2.4 — Afiliados + Familia

- [ ] [P0/C2] Entidades Member, Beneficiary, MemberDocument, MedicalRecord
- [ ] [P0/C3] Validaciones custom: cédula VE (V-/E-), edad min 18, max 3 beneficiarios
- [ ] [P0/C3] `/v1/admin/members` CRUD + RSQL
- [ ] [P0/C2] `/v1/admin/members/{id}/beneficiaries` CRUD
- [ ] [P0/C3] `/v1/admin/members/{id}/medical-record` (`@PreAuthorize`)
- [ ] [P0/C2] `POST /v1/admin/members/{id}/upload-document`
- [ ] [P0/C3] `/v1/me/member`
- [ ] [P0/C2] Anonimización en queries de aliados (no expone MedicalRecord)

## Tarea 2.5 — Planes + Membresías

- [ ] [P0/C2] Entidades Plan, Membership
- [ ] [P0/C2] `/v1/admin/plans` CRUD
- [ ] [P0/C2] `POST /v1/admin/members/{id}/memberships`
- [ ] [P0/C2] `PUT /v1/admin/memberships/{id}/cancel|reactivate`
- [ ] [P0/C3] `MembershipStatusService` (ACTIVE/SUSPENDED/EXPIRED según pagos+grace)
- [ ] [P0/C3] Job diario `@Scheduled` actualiza status, envía notificación
- [ ] [P1/C2] `GET /v1/admin/memberships/dashboard` KPIs

## Tarea 2.6 — Pagos manuales

- [ ] [P0/C2] Entidad Payment
- [ ] [P0/C3] `POST /v1/admin/payments` (con soporte multipart → R2)
- [ ] [P0/C3] `PUT /v1/admin/payments/{id}/approve`
- [ ] [P0/C2] `PUT /v1/admin/payments/{id}/reject`
- [ ] [P0/C2] `GET /v1/admin/payments` + RSQL
- [ ] [P0/C2] `GET /v1/admin/payments/{id}/support` (presigned URL)
- [ ] [P0/C2] `GET /v1/me/payments`
- [ ] [P0/C2] Templates `payment-received.html` + `payment-approved.html` + `payment-rejected.html`

## Tarea 2.7 — Promotores + Comisiones + Referidos

- [ ] [P0/C2] Entidades Promoter, Commission, Referral
- [ ] [P0/C3] `/v1/admin/promoters` CRUD
- [ ] [P0/C3] `CommissionService` calcula al aprobar pago inicial
- [ ] [P0/C2] `GET /v1/admin/commissions`
- [ ] [P0/C2] `POST /v1/admin/commissions/payout` cierre ciclo
- [ ] [P0/C2] `GET /v1/promoter/dashboard`
- [ ] [P0/C3] `ReferralService` valida code + aplica descuento
- [ ] [P0/C2] `POST /v1/admin/referral-codes`
- [ ] [P0/C2] `GET /v1/me/referrals`

## Tarea 2.8 — Validador (CRÍTICO p95 < 200ms)

- [ ] [P0/C3] `GET /v1/ally/validate/{document}`
- [ ] [P0/C3] Cache Redis `validator:{document}` TTL 60s + invalidación al cambiar membership status
- [ ] [P0/C2] `POST /v1/ally/benefit-usage`
- [ ] [P0/C2] `GET /v1/ally/usage-history`
- [ ] [P0/C2] Métricas Redis contador validaciones/día
- [ ] [P1/C2] Rate limit por aliado (1000/día default)
- [ ] [P1/C3] `GET /v1/admin/usage-metrics`

## Tarea 2.9 — Notificaciones + Carnet digital

- [ ] [P0/C2] Entidad Notification (cola persistente)
- [ ] [P0/C3] NotificationService: enqueue + envío async + reintentos
- [ ] [P0/C2] Templates: payment-reminder, overdue, welcome, suspended, expired, referral, commission-payout
- [ ] [P0/C3] Job diario detecta vencimientos próximos (3 días antes)
- [ ] [P0/C3] Job diario detecta vencidos en gracia
- [ ] [P0/C2] `GET /v1/me/digital-card` + QR generado
- [ ] [P0/C2] `GET /v1/me/family` + `/v1/me/usage-history`

## Tarea 2.10 — Optimización 100K

- [ ] [P1/C3] EXPLAIN ANALYZE queries críticas
- [ ] [P1/C2] Índices adicionales según EXPLAIN
- [ ] [P1/C3] Tuning HikariCP
- [ ] [P1/C2] `@Cacheable` catálogos TTL 1h
- [ ] [P1/C3] Paginación obligatoria (default 20, max 100)
- [ ] [P1/C2] Lazy loading + @EntityGraph/JOIN FETCH (evitar N+1)
- [ ] [P2/C3] Vista materializada `member_metrics_mv` refresh 1h
- [ ] [P2/C3] Particionamiento futuro: benefit_usages, payments
- [ ] [P2/C3] Tests k6: 1000 concurrent validaciones, p95 < 200ms

## Tarea 2.11 — Reportes

- [ ] [P1/C3] `GET /v1/admin/reports/memberships|allies|commissions`
- [ ] [P1/C2] `GET /v1/admin/reports/export?type=...&format=csv`

## Tarea 2.12 — Hardening + QA

- [ ] [P0/C3] OWASP Top 10 audit con skill
- [ ] [P0/C2] CSP estricto
- [ ] [P0/C2] Validación uploads (MIME real, tamaño max)
- [ ] [P0/C2] Audit logs acciones sensibles
- [ ] [P1/C3] Tests integrales: 70% coverage services críticos
- [ ] [P1/C3] Tests E2E con Testcontainers
