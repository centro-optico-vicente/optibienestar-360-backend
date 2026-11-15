# Skills instalados (backend)

Gestionados con `npx skills`. Viven en `.agents/skills/{name}/`.

## Instalados y activos

| Skill | Slash command | Cuándo invocar |
|---|---|---|
| `springboot-patterns` | `/springboot-patterns` | Patrones REST, capas, cache, async, paginación, validación |
| `spring-data-jpa` | `/spring-data-jpa` | Entidades, repositorios, `@Query`, auditing, transacciones |
| `postgresql-expert` | `/postgresql-expert` | Schema, índices, EXPLAIN, queries complejas, migraciones |
| `redis-expert` | `/redis-expert` | Cache strategies, TTL, eviction, blacklist tokens |
| `owasp-security` | `/owasp-security` | Audit Spring Security, JWT, password storage, CORS, OWASP Top 10 |
| `api-security-best-practices` | `/api-security-best-practices` | Rate limiting, validación entrada, headers HTTP |
| `rest-api-design` | `/rest-api-design` | Diseño endpoints, status codes, paginación, RSQL |

## Reglas

- Skills viven en `.agents/skills/{name}/`. No editar directamente.
- Lockfile en `skills-lock.json` al root del repo.
- Si un skill conflictúa con un ADR local, **prevalece el ADR**.
- `spring-boot-engineer` y `smithery-ai-cli` se quitaron el 2026-11-15: el primero está escrito para Spring Boot 3 (el repo usa 4) y se solapa con `springboot-patterns`; el segundo no tiene relación con el backend. Cada skill instalado ocupa contexto en todos los turnos ([ADR 0040 del hub](https://github.com/centro-optico-vicente/centro-optico-vicente/blob/main/.ai/decisions/0040-ai-context-token-budget.md)).
- Skills propios del repo (no de terceros, versionados en `.claude/skills/`): `new-migration`, `new-entity`, `new-endpoint`, `new-role`, `migration-conflict`.

## Gestión

```bash
npx skills list          # ver instalados
npx skills add <owner/repo@skill>
npx skills update -p     # actualizar todos
npx skills remove <name>
```
