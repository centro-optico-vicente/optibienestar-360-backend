# Alcance 4 — Afiliados y Familia (Suscriptores)

> Titular + beneficiarios + historial médico.
> Índice: [../../checklist-vertical.md](../../checklist-vertical.md)

## Migraciones

- [ ] [P0/C3] `V15__members.sql` + member_documents
- [ ] [P0/C3] `V16__beneficiaries.sql`
- [ ] [P0/C3] `V17__medical_records.sql`
- [ ] [P1/C2] Índice GIN full-text `members.full_name` con unaccent

## Código

- [ ] [P0/C2] Entidades Member, Beneficiary, MemberDocument, MedicalRecord
- [ ] [P0/C3] Validaciones custom: cédula VE (V-/E-), edad mín 18, tope beneficiarios = `plan.max_beneficiaries` _(v2: tope parametrizable, ver sección Adicionales v2)_
- [ ] [P0/C3] `/v1/admin/members` CRUD + RSQL
- [ ] [P0/C2] `/v1/admin/members/{id}/beneficiaries` CRUD
- [ ] [P0/C3] `/v1/admin/members/{id}/medical-record` (`@PreAuthorize`)
- [ ] [P0/C2] `POST /v1/admin/members/{id}/upload-document`
- [ ] [P0/C3] `/v1/me/member`
- [ ] [P0/C2] Anonimización en queries de aliados (no expone MedicalRecord)

## Adicionales v2 — Inclusión y Modificación de Beneficiarios

> Ver [`../scope-additions-v2.md`](../scope-additions-v2.md) (ítem PDF #7).
> Reemplaza el límite hardcoded "máx 3 beneficiarios" del checklist v1 — ahora es configurable por plan.

### Migraciones

- [ ] [v2] [P0/C2] `beneficiaries` ampliada: `extra_inscription_paid` boolean default false, `inscription_payment_id` FK opcional a `payments` (rastreo del cobro one-time del extra).

### Reglas de cobro (definidas por el flyer Centro Óptico Vicente)

- [ ] [v2] [P0/C2] **Mensualidad del titular NO cambia al agregar beneficiarios extra** — siempre paga el mismo `plan.monthly_fee` ($5/mes según flyer).
- [ ] [v2] [P0/C2] **Cada beneficiario adicional paga su propia inscripción** UNA vez ($5 según flyer "Afiliado Adicional"), parametrizable en `plan.extra_beneficiary_inscription_fee` (definido en vertical-5).
- [ ] [v2] [P0/C2] **Tope de beneficiarios incluidos sin cobro** = `plan.included_beneficiaries` (ej. plan Individual=0, Familiar=3, Corporativo=N parametrizable). Más allá del tope, dispara cobro de inscripción extra. Tope DURO = `plan.max_beneficiaries`.

### Endpoints

- [ ] [v2] [P0/C2] `POST /v1/admin/members/{uuid}/beneficiaries` — valida tope `plan.max_beneficiaries`, crea el beneficiario en `extra_inscription_paid=false`, genera `payment` por la inscripción extra si excede `plan.included_beneficiaries`.
- [ ] [v2] [P0/C2] `DELETE /v1/admin/members/{uuid}/beneficiaries/{benUuid}` — sustituir/eliminar beneficiario; NO reembolsa inscripción ni recalcula mensualidad (la mensualidad no depende del conteo).
- [ ] [v2] [P0/C2] `GET /v1/admin/members/{uuid}/beneficiaries` ya cubierto por v1 — extender DTO para incluir `extraInscriptionPaid` + `inscriptionPaymentUuid`.

### Validación

- [ ] [v2] [P0/C2] Bloquear creación si `member.activeBeneficiaries() >= plan.max_beneficiaries` (422 `member.beneficiary.cap_exceeded`).
