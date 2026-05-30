# Alcance 2 — Catálogos

> Datos de referencia necesarios para Aliados y Afiliados.
> Índice: [../../checklist-vertical.md](../../checklist-vertical.md)

## Migraciones

- [x] [P0/C2] `V8__locations.sql` — `countries` (seed VE + `iso_code` UNIQUE), `states` (FK→countries, seed 24 entidades federales VE), `cities` (FK→states, seed curado: capitales/municipios principales)
- [x] [P0/C2] `V9__personal_catalogs.sql` — `genders` (M/F/O), `document_types` (V/E/J/P — normaliza el CHECK inline de `users` en V5), `marital_statuses` (soltero/casado/divorciado/viudo/concubinato), `occupations` (lista curada)
- [ ] [P0/C3] `V10__health_catalogs.sql` — medical_specialties, service_categories, ally_types

## Código

- [x] [P0/C2] Entidades + repositorios para cada catálogo _(módulo `modules/catalog` — 7 entities heredando `BaseAuditEntity` + 7 repos `JpaRepository` con finders por uuid/code)_
- [ ] [P0/C2] `GET /v1/public/catalogs/*` (lectura pública sin auth)
- [ ] [P0/C2] `CRUD /v1/admin/catalogs/*`
- [ ] [P1/C2] `@Cacheable` TTL 1h en todos los catálogos
