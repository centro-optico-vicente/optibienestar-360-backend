# Checklist — FASE 2: Afiliaciones y Membresías

> Formato `[P/C]`: Prioridad (P0/P1/P2/P3) + Complejidad (C1-C5). Marcar `- [x]` con fecha al completar.
> Índice: [../checklist.md](../checklist.md) | Orden de ejecución: [../checklist-vertical.md](../checklist-vertical.md)

## Tarea 2.1 — Modelo de datos (migraciones Flyway)

> Las migraciones se ejecutan por alcance junto a su código — ver [checklist-vertical.md](../checklist-vertical.md).

> Numeración alineada con migraciones reales en disco. V1–V4 son bootstrap Phase 1; V7 es `seed_users` supplementary.

- [x] [P0/C2] `V5__users_and_roles.sql`: users, roles, permissions, user_roles, role_permissions
- [x] [P0/C2] `V6__seed_roles.sql`: ADMIN, OPERADOR, ALIADO_USER, AFILIADO_USER, PROMOTOR _(+ V7 `seed_users` supplementary)_
- [x] [P0/C2] `V8__locations.sql`: countries (seed VE + iso_code), states (FK→countries, 24 entidades federales VE), cities (FK→states, seed curado)
- [x] [P0/C2] `V9__personal_catalogs.sql`: genders, document_types (normaliza el CHECK V/E de users en V5), marital_statuses, occupations
- [x] [P0/C3] `V10__health_catalogs.sql`: medical_specialties, service_categories, ally_types
- [x] [P0/C3] `V11__allies.sql`: allies, ally_specialties, ally_services, ally_agreements + `ally_service_review_log` + workflow v2 (`PROPOSED → IN_REVIEW → APPROVED → REMOVED`, bypass REJECTED) + permiso `ALLY_SERVICE_APPROVE`. Ver detalle en [vertical-3-aliados.md](vertical-3-aliados.md).
- [x] [P0/C3] `V12__ally_users.sql` — pivote N:M user↔ally con `ally_role` (OWNER/STAFF/VIEWER) + `is_primary`. Dropea `allies.manager_user_id` (V11) ahora redundante. Ver detalle en [vertical-3-aliados.md](vertical-3-aliados.md).
- [x] [P0/C3] `V13__plans.sql` — schema con todos los campos v2 (type ENUM check, included/max_beneficiaries, extra_beneficiary_inscription_fee) + is_published / published_at + grace_period_days. Seeds van en V14. Ver detalle en [vertical-5-planes-y-membresias.md](vertical-5-planes-y-membresias.md).
- [x] [P0/C2] `V14__seed_plans.sql`: 3 SKUs del flyer — Individual $10/$5 (published), Familiar $20/$5 (published), Corporativo $5/$5 (unpublished, pricing TBD). Ver detalle en [vertical-5-planes-y-membresias.md](vertical-5-planes-y-membresias.md).
- [x] [P0/C3] `V17__members.sql` + member_documents — `members.person_id` FK NOT NULL UNIQUE a `persons` (BaseEntity-style audit, sin demográficos). `member_documents` con CHECK constraint sobre `document_type` + `file_url` (R2 key). Ver detalle en [vertical-4-afiliados-y-familia.md](vertical-4-afiliados-y-familia.md).
- [x] [P0/C3] `V18__beneficiaries.sql` — FK a persons + UNIQUE(member_id, person_id) + `extra_inscription_paid` boolean v2 + `inscription_payment_id` BIGINT (FK pendiente para V21). Ver detalle en [vertical-4-afiliados-y-familia.md](vertical-4-afiliados-y-familia.md).
- [x] [P0/C3] `V19__medical_records.sql` — 1:1 con persons (blood_type CHECK + emergency contact estructurado + allergies/conditions/medications/notes TEXT). Ver detalle en [vertical-4-afiliados-y-familia.md](vertical-4-afiliados-y-familia.md).
- [x] [P0/C3] `V21__memberships.sql` — memberships con lifecycle ACTIVE/SUSPENDED/EXPIRED/CANCELED + partial UNIQUE 1-activa-por-member + pricing snapshot. Bumpeado desde V20 (V20 tomado por bcrypt_helper). Ver detalle en [vertical-5-planes-y-membresias.md](vertical-5-planes-y-membresias.md).
- [x] [P0/C3] `V23__payments.sql` — schema completo del workflow manual (PENDING/APPROVED/REJECTED) con proof of payment R2 + allocation (inscription/recurring) + review fields + 4 CHECK constraints de coherencia + 3 índices (pending queue, history, reports). Wires el FK diferido de V18 (beneficiaries.inscription_payment_id → payments). Ver detalle en [vertical-6-pagos-manuales.md](vertical-6-pagos-manuales.md).
- [x] [P0/C3] `V24__benefit_usages.sql` — audit ledger del validador: FK membership + ally + service/operator opcionales + dual time + copay pareado + metadata JSONB per-ally type + status workflow + 4 índices. Ver [vertical-7-validador.md](vertical-7-validador.md).
- [x] [P0/C3] `V25__promoters.sql` — promoters con v2 baked-in (`referral_code` UNIQUE + `is_system` flag) + seed INSTITUCION + agrega `members.promoter_id` NULLABLE. Ver [vertical-8-promotores-comisiones-referidos.md](vertical-8-promotores-comisiones-referidos.md).
- [x] [P0/C3] `V26__commissions.sql` — commissions audit (promoter+payment+member FKs) con calculation snapshot (pct XOR flat + tier snapshot) + period bounds + workflow PENDING/PAID/VOIDED/DISPUTED + 5 CHECK coherence + 4 índices. Ver [vertical-8-promotores-comisiones-referidos.md](vertical-8-promotores-comisiones-referidos.md).
- [x] [P0/C3] `V27__referrals.sql` — affiliate-to-affiliate referrals (referrer+referred FKs, lifecycle PENDING/REGISTERED/EXPIRED/REWARD_GRANTED/VOIDED, reward pct XOR flat, 8 CHECK coherence + 5 índices) + agrega `members.referral_code` UNIQUE partial. Ver [vertical-8-promotores-comisiones-referidos.md](vertical-8-promotores-comisiones-referidos.md).
- [ ] [P0/C2] `V28__notifications.sql`
- [ ] [P0/C2] `V29__digital_cards_view.sql`
- [ ] [P0/C2] Índices clave: members(document), memberships(status, next_due), benefit_usages, payments(status)
- [ ] [P1/C2] Índice GIN full-text en members.full_name con unaccent

## Tarea 2.2 — Auth + Users

- [x] [P0/C2] Entidades User, Role, Permission, UserRole, RolePermission (JPA) _(RolePermission implementado como `@JoinTable` dentro de Role — relación M:N sin atributos extra; las otras 4 son @Entity explícitas)_
- [x] [P0/C2] Repositorios + Services + DTOs + Mappers _(módulo `modules/auth/{repository,service,dto,mapper}` completo)_
- [x] [P0/C2] `POST /v1/auth/login`
- [x] [P0/C2] `POST /v1/auth/refresh`
- [x] [P0/C2] `POST /v1/auth/logout` (Redis blacklist) _(TokenBlacklistService con Redis, 3 keys, graceful degradation)_
- [x] [P0/C2] `POST /v1/auth/recover-password` + `reset-password`
- [x] [P0/C2] `GET /v1/me` _(MeController)_
- [x] [P0/C3] `/v1/admin/users` CRUD + RSQL _(AdminUserController con @PreAuthorize fine-grained + paginación)_
- [x] [P1/C1] `GET /v1/admin/roles` + `/{uuid}` _(AdminRoleController — lectura de roles activos para selects del panel; sin CRUD)_
- [x] [P1/C2] `POST /v1/me/change-password`
- [x] [P1/C2] Anti-brute-force (lock IP+identifier) _(campos brute-force en User + SecurityPolicy configurable)_

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

- [x] [P0/C2] Entidades Member, Beneficiary, MemberDocument, MedicalRecord — 4 entities en `modules/member/entity/` con enums internos (Relationship, DocumentType, EmergencyContactRelationship); `bloodType` queda como String. Ver detalle en [vertical-4-afiliados-y-familia.md](vertical-4-afiliados-y-familia.md).
- [x] [P0/C3] Validaciones custom: cédula VE (V-/E-), edad min 18, max 3 beneficiarios — `@VenezuelanDocumentNumber` + `@MinimumAge` (2 de 3 reglas con Jakarta Validation); tope beneficiarios queda service-side al implementar Plan. Ver detalle en [vertical-4-afiliados-y-familia.md](vertical-4-afiliados-y-familia.md).
- [x] [P0/C3] `/v1/admin/members` CRUD + RSQL — `AdminMemberController` (5 endpoints guardados por perms MEMBER_*) + `MembersService` que orquesta `PersonService.findOrCreate` para dedupe por cédula. Ver detalle en [vertical-4-afiliados-y-familia.md](vertical-4-afiliados-y-familia.md).
- [x] [P0/C2] `/v1/admin/members/{id}/beneficiaries` CRUD — `AdminMemberBeneficiariesController` + `BeneficiariesService` (reutiliza `PersonService.findOrCreate` + readmission flow vía UNIQUE V18). Cap del plan diferido hasta V20. Ver detalle en [vertical-4-afiliados-y-familia.md](vertical-4-afiliados-y-familia.md).
- [ ] [P0/C3] `/v1/admin/members/{id}/medical-record` (`@PreAuthorize`)
- [ ] [P0/C2] `POST /v1/admin/members/{id}/upload-document`
- [x] [P0/C3] `/v1/me/member` — self-service del afiliado logueado. `MyMemberController.GET` guardado por `MEMBER_VIEW_OWN`; resuelve via `MemberRepository.findByUserUuid` (JPQL una sola query). 404 si no enrolled. Ver detalle en [vertical-4-afiliados-y-familia.md](vertical-4-afiliados-y-familia.md).
- [ ] [P0/C2] Anonimización en queries de aliados (no expone MedicalRecord)

## Tarea 2.5 — Planes + Membresías

- [x] [P0/C2] Entidades Plan, Membership — 2 entities en `modules/membership/entity/` con enum interno `PlanType` (INDIVIDUAL/FAMILIAR/CORPORATIVO) en Plan + `LifecycleStatus` (ACTIVE/SUSPENDED/EXPIRED/CANCELED) en Membership. Pricing snapshot copiado del plan al alta para inmunidad. Ver detalle en [vertical-5-planes-y-membresias.md](vertical-5-planes-y-membresias.md).
- [x] [P0/C2] `/v1/admin/plans` CRUD — `AdminPlanController` (5 endpoints guardados por perms PLAN_*) + `PlansService` con pre-checks de unicidad del code y coherencia max≥included. Ver detalle en [vertical-5-planes-y-membresias.md](vertical-5-planes-y-membresias.md).
- [x] [P0/C2] `POST /v1/admin/members/{id}/memberships` — `AdminMemberMembershipsController` + `MembershipsService.enroll` con pricing snapshot del plan + status ACTIVE + next_due_date calendar +1 mes. Cancel/reactivate van en bullet siguiente. Ver detalle en [vertical-5-planes-y-membresias.md](vertical-5-planes-y-membresias.md).
- [x] [P0/C2] `PUT /v1/admin/memberships/{id}/cancel|reactivate` — `AdminMembershipController` + `MembershipLifecycleService` con state-machine: cancel desde cualquier estado, reactivate solo SUSPENDED/EXPIRED (CANCELED es terminal). Ver detalle en [vertical-5-planes-y-membresias.md](vertical-5-planes-y-membresias.md).
- [x] [P0/C3] `MembershipStatusService` (ACTIVE/SUSPENDED/EXPIRED según pagos+grace) — date-driven state-machine con `evaluate` pure-function + `applyTransition` mutate + `applyDueTransitions` batch (consumido por daily job). 9 unit tests cubriendo fronteras de grace, CANCELED/soft-deleted skip, grace=0. Ver detalle en [vertical-5-planes-y-membresias.md](vertical-5-planes-y-membresias.md).
- [x] [P0/C3] Job diario `@Scheduled` actualiza status, envía notificación — reemplazado por el framework `scheduled_jobs` (V22 + módulo `modules/scheduling/`) + primer runner `MembershipStatusJobRunner` con templates email `membership-suspended/expired` _es/_en. Hot reload runtime, hybrid sync/async manual trigger, audit ledger JSONB. Ver detalle en [vertical-5-planes-y-membresias.md](vertical-5-planes-y-membresias.md).
- [ ] [P1/C2] `GET /v1/admin/memberships/dashboard` KPIs

## Tarea 2.6 — Pagos manuales

- [x] [P0/C2] Entidad Payment — `modules/payment/entity/Payment.java` extends BaseEntity con `@ManyToOne` Membership + payer/reviewer User; enum interno `PaymentMethod` (7 valores) + `PaymentStatus` (PENDING/APPROVED/REJECTED). Ver detalle en [vertical-6-pagos-manuales.md](vertical-6-pagos-manuales.md).
- [x] [P0/C3] `POST /v1/admin/payments` (con soporte multipart → R2) — `AdminPaymentController` + `PaymentsService.register`. Multipart (JSON payment + opcional support file). R2 inyectado via `ObjectProvider<StorageService>` — file metadata siempre se persiste; bytes solo se uploadean si `storage.r2.enabled=true`. Status arranca PENDING. Ver detalle en [vertical-6-pagos-manuales.md](vertical-6-pagos-manuales.md).
- [x] [P0/C3] `PUT /v1/admin/payments/{id}/approve` — body opcional con reason de aprobación; valida PENDING (422 si ya revisado); setea reviewedBy/At + status=APPROVED. Ver [vertical-6-pagos-manuales.md](vertical-6-pagos-manuales.md).
- [x] [P0/C2] `PUT /v1/admin/payments/{id}/reject` — body requerido con `@NotBlank reason` (mirror CHECK V23 + UX al afiliado); valida PENDING; setea reviewedBy/At + status=REJECTED. Ver [vertical-6-pagos-manuales.md](vertical-6-pagos-manuales.md).
- [x] [P0/C2] `GET /v1/admin/payments` + RSQL — `Page<PaymentDto>` default `receivedAt DESC` size 20; RSQL allowlist 12 campos + free-text `?q` sobre referenceNumber/adminNotes/supportFileName. Aprovecha índice V23 `(status, received_at DESC)`. Ver [vertical-6-pagos-manuales.md](vertical-6-pagos-manuales.md).
- [x] [P0/C2] `GET /v1/admin/payments/{id}/support` (presigned URL) — JSON `PaymentSupportUrlDto` con `url` + `expiresAt` + metadata; query `?ttlMinutes` clampeado [1..60] default 5; 422 si R2 off, 404 si no hay proof. Ver [vertical-6-pagos-manuales.md](vertical-6-pagos-manuales.md).
- [x] [P0/C2] `GET /v1/me/payments` — self-service del afiliado. `MyPaymentsController` guardado por `PAYMENT_VIEW_OWN`; single-JPQL `findOwnByUserUuid` que walks user→person→member→membership→payment. Sort `receivedAt DESC`. Sin RSQL (full history para el afiliado). Ver [vertical-6-pagos-manuales.md](vertical-6-pagos-manuales.md).
- [x] [P0/C2] Templates `payment-received.html` + `payment-approved.html` + `payment-rejected.html` — 6 archivos (3 × _es/_en), wired desde PaymentsService.register/approve/reject. SMTP failure no rollbackea la transacción. 9 subjects nuevos en bundles. Ver [vertical-6-pagos-manuales.md](vertical-6-pagos-manuales.md).

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

- [x] [P0/C3] OWASP Top 10 audit con skill _(ejecutado en Vertical 1 — auth module; pendiente re-ejecutar al cierre de cada vertical)_
- [ ] [P0/C2] CSP estricto
- [ ] [P0/C2] Validación uploads (MIME real, tamaño max)
- [ ] [P0/C2] Audit logs acciones sensibles
- [ ] [P1/C3] Tests integrales: 70% coverage services críticos
- [ ] [P1/C3] Tests E2E con Testcontainers
