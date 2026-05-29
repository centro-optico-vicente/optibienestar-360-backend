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
- [ ] [P0/C3] Validaciones custom: cédula VE (V-/E-), edad mín 18, máx 3 beneficiarios
- [ ] [P0/C3] `/v1/admin/members` CRUD + RSQL
- [ ] [P0/C2] `/v1/admin/members/{id}/beneficiaries` CRUD
- [ ] [P0/C3] `/v1/admin/members/{id}/medical-record` (`@PreAuthorize`)
- [ ] [P0/C2] `POST /v1/admin/members/{id}/upload-document`
- [ ] [P0/C3] `/v1/me/member`
- [ ] [P0/C2] Anonimización en queries de aliados (no expone MedicalRecord)
