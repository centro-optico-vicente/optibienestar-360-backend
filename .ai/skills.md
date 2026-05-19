# Skills externos recomendados (backend)

Skills para instalar en este repo vía `npx skills add` (o equivalente).

## Stack core

| Skill | Cuándo invocar |
|---|---|
| `spring-boot-best-practices` | Refactor backend, config, beans, profiles, actuator |
| `postgresql-expert` | Diseñar schema, índices, EXPLAIN, queries complejas |
| `redis-expert` | Cache strategies, TTL, eviction, blacklist tokens |

## Seguridad

| Skill | Cuándo invocar |
|---|---|
| `owasp-security` | Audit Spring Security, JWT, password storage, CORS |
| `api-security-best-practices` | Rate limiting, validación entrada |

## API design

| Skill | Cuándo invocar |
|---|---|
| `rest-api-design` | Diseño endpoints, status codes, paginación, RSQL |

## Comandos

```bash
# Instalar (interactivo, seleccionar agente claude-code)
npx skills add

# Listar instalados
npx skills list

# Actualizar todos
npx skills update -p

# Quitar
npx skills remove <name>
```

## Reglas

- Skills viven en `.agents/skills/{name}/` con symlinks en `.claude/skills/{name}` (gestionado automáticamente por `npx skills`).
- No editar archivos dentro de `.agents/skills/` directamente.
- Lockfile en `skills-lock.json` al root del repo.
- Si un skill conflictúa con un ADR local, prevalece el ADR.

## Orden sugerido

**Bloqueantes:**
1. `postgresql-expert`
2. `spring-boot-best-practices`

**Después:**
3. `owasp-security`
4. `redis-expert`
5. `rest-api-design`
6. `api-security-best-practices`
