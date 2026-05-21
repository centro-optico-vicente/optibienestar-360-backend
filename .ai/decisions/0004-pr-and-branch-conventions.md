# ADR 0004 (local backend) — Convenciones de Pull Request y ramas (Gitflow)

**Estado:** Aceptado  
**Fecha:** 2026-05-21

## Contexto

Se necesita una convención uniforme para nombrar ramas y redactar Pull Requests que sea legible en el historial de GitHub y reproducible por IA en cada sesión.

## Decisión

### Nomenclatura de ramas (Gitflow simplificado)

```
<tipo>/<descripción-corta-en-kebab-case>
```

| Tipo | Cuándo usarlo |
|---|---|
| `feature/` | Nueva funcionalidad (endpoint, módulo, flujo) |
| `fix/` | Corrección de bug en desarrollo |
| `hotfix/` | Corrección urgente sobre producción (`main`) |
| `chore/` | CI/CD, Dockerfiles, dependencias, configuración, docs |
| `refactor/` | Refactor sin cambio de comportamiento observable |
| `release/` | Preparación de versión (bump, changelog) |

Ejemplos:
```
feature/post-contact-endpoint
feature/auth-jwt-login
fix/placeholder-resolution-test-context
chore/gh-actions-cache-optimization
hotfix/cors-missing-header
```

### Formato del Pull Request

**Título** — Conventional Commits:
```
<tipo>: <descripción imperativa en minúsculas, máx 70 chars>
```

Ejemplos:
```
feat: Add POST /v1/public/contact endpoint
fix: Resolve PlaceholderResolutionException in test context
chore: Optimize Docker build cache with prebuilt JAR
```

**Contenido** (copy-paste directo en GitHub):

```markdown
feat: Add POST /v1/public/contact endpoint

## Summary

- <bullet qué se hace y por qué — una línea>
- <bullet>
- <bullet>

## Changes

- `ruta/Archivo.java` — descripción
- `db/migration/VN__*.sql` — descripción

## Test plan

- [ ] Caso feliz: request válido → comportamiento esperado
- [ ] Validación: campo requerido faltante → 400 problem+json
- [ ] `./gradlew build` pasa (CI verde)

```

> **Regla clave:** la primera línea del contenido repite el título exacto del PR. Esto hace que el merge commit en `main` sea legible sin abrir el PR.

## Por qué

- **Conventional Commits** permite generar CHANGELOG automático y determinar semver de forma mecánica.
- **Gitflow simplificado** (sin `develop`) se adapta al equipo pequeño y al ciclo de releases con GitHub Releases.
- **Título repetido** en el cuerpo garantiza que el squash-merge message incluye el contexto completo.

## Alternativas

- **Trunk-based sin gitflow:** más simple pero pierde trazabilidad por tipo de cambio.
- **Gitflow completo con `develop`:** innecesariamente complejo para un equipo de 1-3 personas.

## Consecuencias

- Toda rama se crea con prefijo de tipo antes de trabajar.
- Todo PR generado por IA sigue esta plantilla (título en primera línea del cuerpo).
- El título del PR es también el mensaje del squash-merge commit.
