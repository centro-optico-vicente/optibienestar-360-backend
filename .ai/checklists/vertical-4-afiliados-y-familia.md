# Alcance 4 — Afiliados y Familia (Suscriptores)

> Titular + beneficiarios + historial médico.
> Índice: [../../checklist-vertical.md](../../checklist-vertical.md)

## Migraciones

> 📌 Refactor 2026-06: V15-V25 originales se bumpearon +2 al insertarse V15 persons + V16 refactor_users_persons. Ver [ADR 0011](../decisions/0011-persons-identity-hub.md).

- [x] [P0/C3] `V17__members.sql` + member_documents — `members.person_id` FK NOT NULL UNIQUE a `persons` (datos demográficos del titular viven en persons, no en members) _(Schema mínimo en `members`: `person_id` UNIQUE strict (readmisión = reactivar la row existente, no insertar otra), `occupation_id` FK opcional al catálogo V9 (snapshot al momento de afiliación, no se sigue si el member cambia de trabajo), `enrolled_at` DATE default CURRENT_DATE, `notes` TEXT, audit. NO incluye `is_holder` (todo Member es titular por definición — beneficiaries son entity aparte). NO duplica nombres/cédula/contacto/dirección (vive en persons via FK). Búsqueda full-text reusa `idx_persons_full_name_unaccent` (V15) via JOIN — sin índice nuevo en members. **`member_documents`** con `document_type` CHECK constraint (ID_FRONT/ID_BACK/PROOF_OF_RESIDENCE/MEDICAL_HISTORY/MEMBER_PHOTO/INCOME_PROOF/OTHER), `file_url` (R2 key) + `file_name + file_size_bytes + mime_type` para download/validación, `uploaded_by` FK users; ON DELETE CASCADE al borrar el member. Índices `(member_id)` y `(member_id, document_type)` para el upload-once-per-type guard service-side. contextLoads V1..V17 limpio.)_
- [x] [P0/C3] `V18__beneficiaries.sql` — `beneficiaries.person_id` FK NOT NULL a `persons`; UNIQUE(member_id, person_id) evita duplicados en el mismo plan _(Schema: `member_id` FK CASCADE + `person_id` FK NOT NULL + `relationship` CHECK ENUM (SPOUSE/CHILD/PARENT/SIBLING/OTHER) — keep small para no agregar catálogo. **v2 fields baked in desde día 1**: `extra_inscription_paid` BOOLEAN default false; `inscription_payment_id BIGINT` declarado SIN FK constraint (payments aún no existe — V21 planeada) — el ALTER ADD CONSTRAINT FK se hará en V21. UNIQUE(member_id, person_id) — el mismo person puede ser beneficiary de varios titulares en distintos planes (caso divorcio + niño), pero nunca dos veces bajo el mismo member. Soft-delete reactiva la row existente. 3 índices: `(member_id) WHERE active` (listing), `(person_id)` (validator cross-titular lookup), `(member_id) WHERE active AND NOT extra_inscription_paid` (cobranza pendiente). Sustitución A→B es service-level (soft-delete A, INSERT B). contextLoads V1..V18 limpio.)_
- [x] [P0/C3] `V19__medical_records.sql` _(Schema mínimo 1:1 con persons (UNIQUE strict). Campos: `blood_type VARCHAR(5)` con CHECK ('A+'/'A-'/'B+'/'B-'/'AB+'/'AB-'/'O+'/'O-') — mantenido como String porque los valores tienen `+`/`-` y no son identifiers legales de enum Java; `allergies` + `chronic_conditions` + `current_medications` TEXT; emergency contact estructurado (name, phone, relationship CHECK ENUM SPOUSE/CHILD/PARENT/SIBLING/FRIEND/OTHER); `notes` libre. Audit BaseEntity. NO índices extra — tabla pequeña queried solo por person_id (ya unique). Privacy: ally users NUNCA deben recibir esta entity vía API; enforce service-side per ADR/vertical-4 rule. contextLoads V1..V19 limpio.)_
- [ ] [P1/C2] Búsqueda full-text reusa `idx_persons_full_name_unaccent` (V15) vía JOIN — no se crea índice duplicado en members

## Código

- [x] [P0/C2] Entidades Member, Beneficiary, MemberDocument, MedicalRecord _(4 entities en `modules/member/entity/`, todas extienden `BaseEntity`. **Member**: `@OneToOne(optional=false) Person` con `@JoinColumn(name="person_id", unique=true)`, `@ManyToOne Occupation` opcional (snapshot), `enrolledAt LocalDate` default now, `@OneToMany List<Beneficiary>` + `List<MemberDocument>`. **Beneficiary**: `@ManyToOne Member` + `@ManyToOne Person`, `@Enumerated(STRING) Relationship` enum (SPOUSE/CHILD/PARENT/SIBLING/OTHER), `extraInscriptionPaid` boolean v2, `inscriptionPaymentId Long` (FK diferido). UniqueConstraint columnNames {member_id, person_id}. **MemberDocument**: `@ManyToOne Member` + `@Enumerated(STRING) DocumentType` enum (ID_FRONT/ID_BACK/PROOF_OF_RESIDENCE/MEDICAL_HISTORY/MEMBER_PHOTO/INCOME_PROOF/OTHER), file metadata (fileUrl R2 key + fileName + fileSizeBytes + mimeType), `@ManyToOne User uploadedBy`. **MedicalRecord**: `@OneToOne Person` UNIQUE, `bloodType String` (no enum porque 'A+' no es identifier Java), `allergies/chronic_conditions/current_medications/notes` TEXT, structured emergency contact con `@Enumerated(STRING) EmergencyContactRelationship` enum (SPOUSE/CHILD/PARENT/SIBLING/FRIEND/OTHER). Hibernate validate verde — contextLoads OK.)_
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
