# Skills instalados (backend)

Gestionados con `npx skills`. Viven en `.agents/skills/{name}/`.

## Instalados y activos

| Skill | Slash command | Cuándo invocar |
|---|---|---|
| `spring-boot-engineer` | `/spring-boot-engineer` | Config, beans, profiles, actuator, Spring Security, WebFlux |
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
- `spring-boot-best-practices` no existe como skill publicado — se usa `spring-boot-engineer` (equivalente, 5.9K installs).

## Gestión

```bash
npx skills list          # ver instalados
npx skills add <owner/repo@skill>
npx skills update -p     # actualizar todos
npx skills remove <name>
```
