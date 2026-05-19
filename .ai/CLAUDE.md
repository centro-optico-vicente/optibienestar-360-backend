# `.ai/` — Brief para asistencia IA (backend)

> Brief específico del repo `optisalud-plus-backend`. Para visión cross-stack y decisiones globales, ver el hub maestro en [`../../centro-optico-vicente/.ai/`](../../centro-optico-vicente/.ai/).

## Qué es este repo

Backend REST API del ecosistema OptiSalud Plus. Implementa lógica de negocio, persistencia, auth, jobs scheduleados, comunicaciones email para las fases del producto (FASE 1 bootstrap + FASE 5 afiliaciones/membresías).

## Stack confirmado

Ver `CLAUDE.md` root del repo. Resumen:
- Spring Boot 4 + Java 25 + PostgreSQL + Redis + R2
- JWT self-hosted
- Flyway + JPA validate
- RSQL para filtros, MapStruct para DTOs, Jetty embebido

## Estructura de paquetes (objetivo)

```
com.fenixcore.optisaludplus/
├── OptiSaludPlusApplication.java
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
7. **Idioma**: código en inglés. Mensajes UI en frontend (no acá). Ver [ADR 0009 cross-stack](../../centro-optico-vicente/.ai/decisions/0009-code-conventions.md).
8. **Endpoints**: `/v1/{group}/...` con `kebab-case`. JSON keys en `snake_case`. Errores RFC 7807.

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
