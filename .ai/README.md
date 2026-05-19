# `.ai/` — Contexto IA del backend

Esta carpeta contiene la documentación específica del repo `optisalud-plus-backend` para asistencia IA. Para visión cross-stack, ver el hub maestro en [`../../centro-optico-vicente/.ai/`](../../centro-optico-vicente/.ai/).

## Estructura

```
.ai/
├── CLAUDE.md          ← Brief de entrada
├── README.md          ← Este archivo
├── checklist.md       ← Subset backend del checklist maestro
├── skills.md          ← Skills externos recomendados
│
├── context/
│   ├── MEMORY.md      ← Índice navegable
│   ├── current-state.md
│   └── api-conventions.md
│
├── specs/
│   ├── 00-overview.md
│   ├── 01-package-structure.md
│   ├── 02-database.md
│   ├── 03-jpa-flyway.md
│   ├── 04-security.md
│   ├── 05-roles-permissions.md
│   ├── 06-rest-api.md
│   ├── 07-cache.md
│   ├── 08-storage-r2.md
│   ├── 09-smtp.md
│   ├── 10-validators.md
│   ├── 11-billing-manual.md
│   ├── 12-commissions.md
│   └── 13-observability.md
│
├── decisions/         ← ADRs locales backend-only
│   └── 0001..N-*.md
│
└── playbooks/         ← Checklists tareas recurrentes
    ├── new-entity.md
    ├── new-migration.md
    ├── new-endpoint.md
    └── new-role.md
```

## Cómo navegarla

| Cuando necesites… | Ve a |
|---|---|
| Onboarding rápido | [`CLAUDE.md`](CLAUDE.md) |
| Qué hacer hoy | [`checklist.md`](checklist.md) |
| Cómo está estructurado el código | [`specs/01-package-structure.md`](specs/01-package-structure.md) |
| Schema DB completo | [`specs/02-database.md`](specs/02-database.md) |
| Convenciones REST | [`specs/06-rest-api.md`](specs/06-rest-api.md) |
| Crear entidad/migración/endpoint | [`playbooks/`](playbooks/) |
| ADRs locales backend | [`decisions/`](decisions/) |
| ADRs cross-stack | [`../../centro-optico-vicente/.ai/decisions/`](../../centro-optico-vicente/.ai/decisions/) |
| Reglas de negocio | [`../../centro-optico-vicente/.ai/context/business-rules.md`](../../centro-optico-vicente/.ai/context/business-rules.md) |

## Cómo contribuir

- Cambios pequeños: editar y commitear.
- Nueva spec: crear archivo + listarlo en `00-overview.md`.
- Nuevo playbook: crear archivo + agregarlo a tabla en `CLAUDE.md`.
- Nuevo ADR local: crear con siguiente número, citar si supersede uno anterior.
