---
name: new-migration
description: Crear una migración Flyway (tabla, columna, índice, permisos o datos sembrados) con número correcto y snapshot de BD regenerado. Usar ante cualquier cambio de esquema.
---
# Nueva migración

1. Tabla actual: `scripts/db/table.sh <tabla>` (y `--refs <tabla>` para quién la referencia).
2. Número: `git fetch -q && git ls-tree --name-only origin/main src/main/resources/db/migration/ | sort -V | tail -1` → el siguiente.
3. Escribir `src/main/resources/db/migration/V{N}__descripcion.sql` siguiendo `.claude/rules/migrations.md` (se carga sola). Detalle y ejemplos: [`.ai/playbooks/new-migration.md`](../../../.ai/playbooks/new-migration.md) — leerlo solo si hay dudas.
4. Ajustar la entidad JPA (está en `validate`: columnas y tipos deben coincidir).
5. Regenerar `src/main/resources/db/snapshot/` (`scripts/db/snapshot.sh --fresh`) y commitearlo en el mismo PR; si no hay Docker local, avisar que lo hará el job `db-snapshot` de CI.
