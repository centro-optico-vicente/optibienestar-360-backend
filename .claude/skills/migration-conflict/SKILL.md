---
name: migration-conflict
description: Resolver un PR del backend en conflicto o con CI rojo por migraciones Flyway (número de versión repetido, snapshot de BD desactualizado, PRs apilados). Usar cuando un PR con migraciones no se puede mergear.
---
# Conflicto de migraciones

1. **Estado real primero:** `gh pr view <N> --json state,mergeable,baseRefName,headRefName`. Si ya está `MERGED`, no empujar a esa rama: rama nueva desde `main` y migración nueva.
2. **Traer main sin tocar el working tree del usuario:** `git fetch origin` y trabajar en un `git worktree add` de la rama del PR si el usuario está en otra.
3. **Número repetido:** comparar `git ls-tree --name-only origin/main src/main/resources/db/migration/ | sort -V | tail -3` con las de la rama. Renombrar la(s) migración(es) del PR al siguiente número libre (`git mv`), y actualizar las referencias en código, docs o tests (`git grep -n 'V<viejo>__'`).
4. **Snapshot (`src/main/resources/db/snapshot/*`) en conflicto:** nunca resolverlo a mano. Tomar la versión de `main` (`git checkout origin/main -- src/main/resources/db/snapshot/`) y regenerar con `scripts/db/snapshot.sh --fresh`; si no hay Docker local, dejar la de `main` y el job `db-snapshot` del CI lo recommitea.
5. **Rebase y push:** `git rebase origin/main` → `git push --force-with-lease` (solo en la rama del PR, nunca en `main`).
6. **PRs apilados** (B sobre A): cuando A se mergea con squash, rebasar B con `git rebase --onto origin/main <último-commit-de-A> B` y repetir 3-5. Avanzar de a uno: no tocar el siguiente hasta que el anterior esté mergeado.
7. Reportar el número final de la migración y el estado del PR; no correr build ni tests locales salvo pedido.
