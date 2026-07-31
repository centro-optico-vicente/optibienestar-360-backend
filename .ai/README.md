# `.ai/` — Contexto IA del backend

Esta carpeta contiene la documentación específica del repo `optibienestar-360-backend` para asistencia IA. Para visión cross-stack, ver el hub maestro en [`../../centro-optico-vicente/.ai/`](../../centro-optico-vicente/.ai/).

## Estructura

```
.ai/
├── CLAUDE.md              ← Brief de entrada (navegación e índice viven ahí)
├── README.md              ← Este archivo (solo estructura)
├── checklist.md           ← Checklist maestro backend
├── skills.md              ← Skills externos recomendados
├── scope-additions-v2.md  ← Alcance adicional v2
├── scope-additions-v3.md  ← Alcance adicional v3
│
├── context/
│   ├── MEMORY.md          ← Índice navegable
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
│   ├── 13-observability.md
│   ├── 14-i18n.md
│   └── 15-reporting-documents.md
│
├── decisions/              ← ADRs locales backend-only
│   └── 0001..N-*.md
│
├── checklists/              ← Checklists por vertical/fase
│   └── vertical-*.md, fase-*.md
│
├── playbooks/               ← Guías tareas recurrentes
│   ├── new-entity.md
│   ├── new-migration.md
│   ├── new-endpoint.md
│   ├── new-role.md
│   └── edit-role-permissions.md
│
└── tools/                   ← Scripts de soporte (ej. insert-totals.mjs)
```

Para navegación por tarea ("qué archivo necesito para X"), ver la tabla en [`CLAUDE.md`](CLAUDE.md) — es la única fuente de esa tabla, no se duplica aquí.

## Cómo contribuir

- Cambios pequeños: editar y commitear.
- Nueva spec: crear archivo + listarlo en `00-overview.md`.
- Nuevo playbook: crear archivo + agregarlo a tabla en `CLAUDE.md`.
- Nuevo ADR local: crear con siguiente número, citar si supersede uno anterior.
