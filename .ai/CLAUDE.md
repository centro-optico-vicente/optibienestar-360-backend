# `.ai/` del backend — mapa de la carpeta

> Se carga solo cuando la IA abre algo dentro de `.ai/`; por eso es corto ([ADR 0040 del hub](https://github.com/centro-optico-vicente/centro-optico-vicente/blob/main/.ai/decisions/0040-ai-context-token-budget.md)). Reglas y ruteo por tarea: [`CLAUDE.md` raíz](../CLAUDE.md).

| Carpeta / archivo | Contiene | Cuándo abrirlo |
|---|---|---|
| `decisions/README.md` | Índice de ADR locales (`LOC-`) | Antes de proponer un diseño |
| `specs/NN-*.md` | Convenciones por área (índice en `specs/00-overview.md`) | Solo la del área que se toca |
| `playbooks/` | Pasos para entidad, migración, endpoint, rol, build local | Vía las skills `new-*` |
| `context/current-state.md` | Lo que existe HOY | Antes de asumir que algo existe |
| `checklist.md` → `checklists/vertical-*.md` | Tabla resumen → tareas por vertical | Para elegir o marcar trabajo |
| `notes/`, `scope-additions-*.md`, `tools/` | Auditorías y alcances históricos | Solo si la tarea los nombra |
| `skills.md` | Catálogo de skills externos | Al instalar skills |

La BD actual **no** está aquí: `scripts/db/table.sh` sobre `src/main/resources/db/snapshot/`. Al marcar tareas, recalcular totales con `../centro-optico-vicente/scripts/ai/ai_audit.sh checklists .ai`.
