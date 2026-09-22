# `.ai/` — Brief para asistencia IA (backend)

> Brief específico del repo `optibienestar-360-backend`. Para visión cross-stack y decisiones globales, ver el hub maestro en [`../../centro-optico-vicente/.ai/`](../../centro-optico-vicente/.ai/).

## Qué es este repo

Backend REST API del ecosistema OptiBienestar 360. Implementa lógica de negocio, persistencia, auth, jobs scheduleados, comunicaciones email para las fases del producto (FASE 1 bootstrap + FASE 5 afiliaciones/membresías).

## Stack confirmado

Ver `CLAUDE.md` root del repo.

## Estructura de paquetes (objetivo)

```
com.fenixcore.optibienestar360/
├── OptiBienestar360Application.java
├── core/                 # Cross-cutting (config, exceptions, auditing)
├── security/             # Auth, JWT, filters
├── common/               # Servicios compartidos (storage, email)
└── modules/              # Módulos de negocio
    ├── users/
    ├── allies/
    ├── members/
    ├── memberships/
    ├── payments/
    ├── promoters/
    ├── validator/
    └── notifications/
```

Detalle en [`specs/01-package-structure.md`](specs/01-package-structure.md).

## Reglas de oro (locales)

1. **Cada migración Flyway sigue convención**: `V{N}__{snake_case_descripcion}.sql`. Una vez aplicada en prod, es inmutable. Para corregir, crear `V{N+1}__fix_X.sql`.
2. **JPA en `validate` mode SIEMPRE**. Nunca `update`/`create`/`create-drop`.
3. **Toda tabla extiende `BaseEntity`** (PK uuid, audit columns, is_active, status). Ver [ADR 0006 cross-stack](../../centro-optico-vicente/.ai/decisions/0006-table-conventions.md).
4. **Soft delete**: nunca `repository.delete()` en datos sensibles — usar `setIsActive(false)`.
5. **Datos médicos**: `MedicalRecord` con `@PreAuthorize` y audit log obligatorio en cada acceso.
6. **Pagos siempre manuales** (no integrar Stripe). Ver [ADR 0008 cross-stack](../../centro-optico-vicente/.ai/decisions/0008-manual-payments.md).
7. **Idioma**: **código en inglés** — identificadores (clases, métodos, variables, paquetes, constantes), **comentarios** (Javadoc, inline, headers SQL), logs y mensajes de excepción técnicos. Solo van en español los **datos visibles al usuario**: textos UI (en el frontend, no acá), plantillas email, y valores del dominio que terminan en la BD como datos (ej. `permission_domains.label = 'Afiliados'`, descripciones de roles). Detalle en [ADR 0009 local (espejo)](decisions/0009-code-conventions.md) y [ADR 0007 naming Java](decisions/0007-java-naming-conventions.md).
8. **Endpoints**: `/v1/{group}/...` con `kebab-case`. JSON keys en `snake_case`. Errores RFC 7807.
9. **Mensajes user-facing van vía MessageSource code, nunca string literal.** Excepciones de negocio extienden `LocalizedBusinessException` o reusan JDK/Spring exceptions con el code en `getMessage()`. Validación Jakarta Bean usa `@Pattern(message="{validation.x.y}")`. Texto en español o inglés vive únicamente en `messages_*.properties` / `ValidationMessages_*.properties` y plantillas email `<name>_<lang>.html`. Detalle, naming y patrones en [`specs/14-i18n.md`](specs/14-i18n.md).

## Convenciones de trabajo con IA

Reglas que aplican a cualquier sesión/agente IA en este repo (Claude Code, Cursor, Copilot, etc.):

- **Commits sin trailer `Co-Authored-By`**. El autor humano firma el commit; no agregar `Co-Authored-By: Claude <…>` ni similares.
- **Invocar skills proactivamente** al iniciar trabajo relevante, no después. Catálogo en [`skills.md`](skills.md). Ejemplos: `spring-boot-engineer` para nuevos endpoints, `postgresql-table-design` para nuevas tablas, `owasp-security` antes de tocar auth.
- **Verificar `current-state.md` antes de asumir** qué existe — los checklists describen lo planeado, no lo construido.
- **No re-discutir decisiones** ya congeladas en `decisions/` o en el hub salvo que se escriba un ADR nuevo que las supersede.
- **Nunca commits directos a `main`** — todo cambio entra vía PR desde una rama `<tipo>/<descripción>` (`feature/`, `fix/`, `hotfix/`, `chore/`, `refactor/`). Sin excepciones, ni siquiera para hotfixes o cambios "obvios". Ver [ADR 0004](decisions/0004-pr-and-branch-conventions.md) (regla cero).
- **Crear ramas con `--no-track`** desde `origin/main`: `git switch -c <tipo>/<descripcion> --no-track origin/main`. **Nunca** `git checkout origin/main -b <name>` porque configura automáticamente el upstream de la rama nueva a `origin/main` y dispara pushes accidentales a `main` (el bug que provocó la creación de la regla cero).
- **Hook pre-push activado** — el repo trae [`.githooks/pre-push`](../.githooks/pre-push) que bloquea cualquier `git push` hacia `refs/heads/main`. Activar una vez por clone con `git config core.hooksPath .githooks` (también está en el [README root](../README.md#local-clone-setup-one-time)). Es la red de seguridad porque Branch Protection de GitHub es paid-only en repos privados.
- **PR/branch siguen Conventional Commits** + Gitflow simplificado. La primera línea del cuerpo repite el título.

## Orden de lectura recomendado para una IA

1. Este `CLAUDE.md`
2. [`README.md`](README.md) — estructura local detallada
3. [`context/current-state.md`](context/current-state.md) — qué existe HOY
4. [`checklist.md`](checklist.md) — qué hacer
5. Hub: [`../../centro-optico-vicente/.ai/context/business-rules.md`](../../centro-optico-vicente/.ai/context/business-rules.md)
6. Hub: [`../../centro-optico-vicente/.ai/specs/05-domain-model.md`](../../centro-optico-vicente/.ai/specs/05-domain-model.md)
7. [`specs/02-database.md`](specs/02-database.md) — schema completo
8. [`specs/04-security.md`](specs/04-security.md) — Spring Security config

## Para tareas comunes

| Tarea | Playbook |
|---|---|
| Crear nueva entidad + CRUD completo | [`playbooks/new-entity.md`](playbooks/new-entity.md) |
| Crear nueva migración Flyway | [`playbooks/new-migration.md`](playbooks/new-migration.md) |
| Crear nuevo endpoint REST | [`playbooks/new-endpoint.md`](playbooks/new-endpoint.md) |
| Agregar nuevo rol/permiso | [`playbooks/new-role.md`](playbooks/new-role.md) |

## Decisiones congeladas relevantes

**Cross-stack (en el hub):**
- [ADR 0001 Stack](../../centro-optico-vicente/.ai/decisions/0001-stack.md)
- [ADR 0004 Only free tools](../../centro-optico-vicente/.ai/decisions/0004-only-free-tools.md)
- [ADR 0005 Flyway + JPA validate](../../centro-optico-vicente/.ai/decisions/0005-flyway-jpa.md)
- [ADR 0006 Table conventions](../../centro-optico-vicente/.ai/decisions/0006-table-conventions.md)
- [ADR 0007 R2 as S3](../../centro-optico-vicente/.ai/decisions/0007-r2-as-s3.md)
- [ADR 0008 Manual payments](../../centro-optico-vicente/.ai/decisions/0008-manual-payments.md)
- [ADR 0009 Code conventions](../../centro-optico-vicente/.ai/decisions/0009-code-conventions.md)

**Locales (en este repo):**
- [ADR 0001 Jetty over Tomcat](decisions/0001-jetty-over-tomcat.md)
- [ADR 0002 Soft delete pattern](decisions/0002-soft-delete.md)
- [ADR 0003 Audit columns](decisions/0003-audit-columns.md)
- [ADR 0004 PR & branch conventions](decisions/0004-pr-and-branch-conventions.md)
- [ADR 0005 Table ID convention](decisions/0005-table-id-convention.md)
- [ADR 0006 Repository search conventions](decisions/0006-repository-search-conventions.md)
- [ADR 0007 Java naming conventions](decisions/0007-java-naming-conventions.md)
- [ADR 0009 Code conventions (espejo del hub)](decisions/0009-code-conventions.md)
- [ADR 0014 Service complexity refactor bajo demanda](decisions/0014-service-complexity-refactor-on-demand.md)
