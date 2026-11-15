---
paths:
  - "src/main/resources/db/migration/**"
---
# Al crear o tocar migraciones Flyway

- Número = siguiente a la última **en `origin/main`** (`git fetch -q && git ls-tree --name-only origin/main src/main/resources/db/migration/ | sort -V | tail -1`), no solo la local: otra rama pudo tomarlo. Nombre `V{N}__snake_case.sql`.
- Una migración mergeada es inmutable; corregir con `V{N+1}__fix_…sql`.
- Tabla nueva: PK `{tabla}_id BIGINT GENERATED … IDENTITY`, `uuid UUID NOT NULL DEFAULT gen_random_uuid()`, columnas de auditoría, `is_active`, `status`; `COMMENT ON` de tabla y columnas en español (lo lee `schema-map.md`); constraints con nombre (`fk_`, `ck_`, `uq_`, `idx_`). Comentarios SQL de código en inglés.
- Antes de escribir, ver la tabla real con `scripts/db/table.sh <tabla>` (no `schema.sql`).
- Datos de catálogo/permisos sembrados en la misma migración, idempotentes (`ON CONFLICT DO NOTHING`); permisos granulares y asignados a los roles que corresponden.
- Mismo PR: regenerar `src/main/resources/db/snapshot/` (`scripts/db/snapshot.sh --fresh`); si no se puede, el job `db-snapshot` de CI lo commitea. Nunca editar el snapshot a mano.
- Si el PR ya fue mergeado, la corrección va en rama nueva desde `main` con migración nueva.
