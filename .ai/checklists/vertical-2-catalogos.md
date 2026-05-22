# Alcance 2 — Catálogos

> Datos de referencia necesarios para Aliados y Afiliados.
> Índice: [../../checklist-vertical.md](../../checklist-vertical.md)

## Migraciones

- [ ] [P0/C2] `V12__catalogs.sql` — countries, states, cities, genders, document_types, marital_statuses, occupations
- [ ] [P0/C3] `V13__health_catalogs.sql` — medical_specialties, service_categories, ally_types

## Código

- [ ] [P0/C2] Entidades + repositorios para cada catálogo
- [ ] [P0/C2] `GET /v1/public/catalogs/*` (lectura pública sin auth)
- [ ] [P0/C2] `CRUD /v1/admin/catalogs/*`
- [ ] [P1/C2] `@Cacheable` TTL 1h en todos los catálogos
