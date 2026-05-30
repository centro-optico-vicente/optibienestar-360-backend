# Alcance 2 — Catálogos

> Datos de referencia necesarios para Aliados y Afiliados.
> Índice: [../../checklist-vertical.md](../../checklist-vertical.md)

## Migraciones

- [x] [P0/C2] `V8__locations.sql` — `countries` (seed VE + `iso_code` UNIQUE), `states` (FK→countries, seed 24 entidades federales VE), `cities` (FK→states, seed curado: capitales/municipios principales)
- [x] [P0/C2] `V9__personal_catalogs.sql` — `genders` (M/F/O), `document_types` (V/E/J/P — normaliza el CHECK inline de `users` en V5), `marital_statuses` (soltero/casado/divorciado/viudo/concubinato), `occupations` (lista curada)
- [x] [P0/C3] `V10__health_catalogs.sql` — medical_specialties, service_categories, ally_types

## Código

- [x] [P0/C2] Entidades + repositorios para cada catálogo _(módulo `modules/catalog` — 10 entities heredando `BaseAuditEntity` + 10 repos `JpaRepository` con finders por uuid/code; cubre V8 locations + V9 personal + V10 health)_
- [x] [P0/C2] `GET /v1/public/catalogs/*` (lectura pública sin auth) _(PublicCatalogsController — 20 endpoints sobre los 10 catálogos; whitelist `/v1/public/**` ya existe en SecurityConfig)_
- [x] [P0/C2] `CRUD /v1/admin/catalogs/*` _(AdminCatalogsController — 50 endpoints sobre los 10 catálogos; @PreAuthorize reusa `USER_VIEW_ALL` para read y `USER_CHANGE_ROLE` para write; soft-delete; DTOs Create/Update con validación; ver follow-up de permisos abajo)_
- [ ] [P2/C2] Crear permisos dedicados `CATALOG_VIEW_ALL` / `CATALOG_CREATE` / `CATALOG_UPDATE` / `CATALOG_DELETE` en una migración futura y reemplazar los `@PreAuthorize` de `AdminCatalogsController` (hoy reusa permisos de USERS por pragmatismo)
- [ ] [P1/C2] `@Cacheable` TTL 1h en todos los catálogos
