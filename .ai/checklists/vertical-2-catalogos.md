# Alcance 2 — Catálogos

> Datos de referencia necesarios para Aliados y Afiliados.
> Índice: [../checklist.md](../checklist.md)

<!-- resumen-totales:start -->
| Tareas | Hechas | Pendientes | % avance | Estado |
|---|---|---|---|---|
| 9 | 9 | 0 | 100% | ✅ Completa |

_Snapshot — recontar con `grep -c '^- \[x\]'`. Panorama global: [checklist.md](../checklist.md)._
<!-- resumen-totales:end -->

## Migraciones

- [x] [P0/C2] `V8__locations.sql` — `countries` (seed VE + `iso_code` UNIQUE), `states` (FK→countries, seed 24 entidades federales VE), `cities` (FK→states, seed curado: capitales/municipios principales)
- [x] [P0/C2] `V9__personal_catalogs.sql` — `genders` (M/F/O), `document_types` (V/E/J/P — normaliza el CHECK inline de `users` en V5), `marital_statuses` (soltero/casado/divorciado/viudo/concubinato), `occupations` (lista curada)
- [x] [P0/C3] `V10__health_catalogs.sql` — medical_specialties, service_categories, ally_types

## Código

- [x] [P0/C2] Entidades + repositorios para cada catálogo _(módulo `modules/catalog` — 10 entities heredando `BaseAuditEntity` + 10 repos `JpaRepository` con finders por uuid/code; cubre V8 locations + V9 personal + V10 health)_
- [x] [P0/C2] `GET /v1/public/catalogs/*` (lectura pública sin auth) _(PublicCatalogsController — 20 endpoints sobre los 10 catálogos; whitelist `/v1/public/**` ya existe en SecurityConfig)_
- [x] [P0/C2] `CRUD /v1/admin/catalogs/*` _(AdminCatalogsController — 50 endpoints sobre los 10 catálogos; @PreAuthorize reusa `USER_VIEW_ALL` para read y `USER_CHANGE_ROLE` para write; soft-delete; DTOs Create/Update con validación; ver follow-up de permisos abajo)_
- [x] [P2/C2] Permisos dedicados de catálogos, reemplazando el reuso de permisos de USERS. **WRITE** se hizo más granular de lo planeado: en vez de un `CATALOG_CREATE/UPDATE/DELETE` global, V32 renombró `USER_CHANGE_ROLE → CATALOG_WRITE` y V33 lo partió en 10 `CATALOG_<ENTIDAD>_WRITE` (uno por catálogo, delegable). **READ** (este cierre): V34 crea `CATALOG_VIEW_ALL` y mueve el `READ_AUTH` del `AdminCatalogsController` de `USER_VIEW_ALL` → `CATALOG_VIEW_ALL`. La migración lo otorga a todo rol con `USER_VIEW_ALL` (los reads siguen idénticos) + todo rol con algún `CATALOG_*_WRITE` (cierra el 403 latente writer-sin-reader) → cambio behavior-preserving. Falta solo sumar `CATALOG_VIEW_ALL` a `types/permissions.ts` del frontend (PR aparte).
- [x] [P1/C2] `@Cacheable` TTL 1h en todos los catálogos _(cache name `catalogs` ya configurado en RedisCacheConfig con TTL 1h; `@Cacheable` solo sobre el path "dropdown" = unpaged + sin filter + sin q + sin parent filter; `@CacheEvict(allEntries=true)` cross-evict en cada create/update/delete — catálogos cambian raro, cross-evict simplifica el modelo; self-injection via `@Lazy` para que el call de cache pase por el proxy AOP)_
- [x] [P1/C2] Listados con paginación + RSQL + `q` _(breaking v1 — Page<XDto>; 20 endpoints públicos + 50 admin CRUD; `?page=&size=&sort=` estándar; `?size=-1` o `?unpaged=true` → unpaged; `?filter=` RSQL con whitelist por catálogo; `?q=` search libre `unaccent(lower(field))` LIKE sobre name/code/description según aplique; default `size=50` para catálogos. Regla global documentada en spec/06 y playbook/new-endpoint)_
