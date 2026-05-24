# Checklist operacional — OptiSalud Plus Backend

> **Propósito:** dashboard de progreso global. Los checkboxes canónicos viven en los archivos de fase y vertical en [`checklists/`](checklists/).
>
> **Orden de ejecución por dominio:** [`checklist-vertical.md`](checklist-vertical.md).
>
> **Mantener sincronizado:** al completar una tarea, marcar `- [x]` en el archivo correspondiente y actualizar la tabla de resumen aquí.

---

## Cómo leer las etiquetas `[P/C]`

### Prioridad (P)

| Tag | Significado | Usar cuando… |
|---|---|---|
| **P0** | Blocker / crítico | El resto del vertical depende de esto |
| **P1** | Alta | Desbloquea múltiples tareas o resuelve riesgo importante |
| **P2** | Estándar | Trabajo normal del roadmap |
| **P3** | Nice-to-have | Mejora pero no urgente |

### Complejidad (C)

| Tag | Esfuerzo aprox. | Ejemplo |
|---|---|---|
| **C1** | < 30 min | Config, línea única, agregar dependencia |
| **C2** | 30 min – 2 h | Un módulo, patrón conocido, sin research |
| **C3** | 2 h – 1 día | Multi-archivo, algo de investigación |
| **C4** | 1–3 días | Cross-cutting, varios subsistemas |
| **C5** | 3+ días | Arquitectural, decisiones importantes |

### Criterios para elegir tarea

- **Poco tiempo (< 2h):** filtra por `C1` o `C2`
- **Sesión larga (4+ h):** ataca `C3`/`C4` — si es `C5`, usar plan mode primero
- **Sin prioridad externa:** top-down por orden del vertical (sigue las dependencias)

---

## Fases de bootstrap

| Fase | Archivo | Tareas | Hechas | Pendientes | Estado |
|---|---|---|---|---|---|
| FASE 0 — Bootstrap infra | [fase-0-bootstrap.md](checklists/fase-0-bootstrap.md) | 5 | 5 | 0 | ✅ Completa |
| FASE 1 — Bootstrap Spring Boot | [fase-1-bootstrap-backend-spring-boot.md](checklists/fase-1-bootstrap-backend-spring-boot.md) | 34 | 34 | 0 | ✅ Completa |

---

## Fase 2 — Afiliaciones y Membresías (verticales)

> Fuente de verdad: archivos `vertical-N-*.md`. El archivo [`fase-2-afiliaciones-y-membresias.md`](checklists/fase-2-afiliaciones-y-membresias.md) tiene referencias desactualizadas — usar los verticales.

| # | Vertical | Archivo | Tareas | Hechas | Pendientes | Estado |
|---|---|---|---|---|---|---|
| 1 | Seguridad y Autenticación | [vertical-1](checklists/vertical-1-seguridad-y-autenticacion.md) | 16 | 14 | 2 | ✅ 2 hardening pendientes |
| 2 | Catálogos | [vertical-2](checklists/vertical-2-catalogos.md) | 6 | 0 | 6 | 🔲 |
| 3 | Aliados | [vertical-3](checklists/vertical-3-aliados.md) | 10 | 0 | 10 | 🔲 |
| 4 | Afiliados y Familia | [vertical-4](checklists/vertical-4-afiliados-y-familia.md) | 12 | 0 | 12 | 🔲 |
| 5 | Planes y Membresías | [vertical-5](checklists/vertical-5-planes-y-membresias.md) | 11 | 0 | 11 | 🔲 |
| 6 | Pagos manuales | [vertical-6](checklists/vertical-6-pagos-manuales.md) | 10 | 0 | 10 | 🔲 |
| 7 | Validador | [vertical-7](checklists/vertical-7-validador.md) | 9 | 0 | 9 | 🔲 |
| 8 | Promotores, Comisiones y Referidos | [vertical-8](checklists/vertical-8-promotores-comisiones-referidos.md) | 12 | 0 | 12 | 🔲 |
| 9 | Notificaciones y Carnet digital | [vertical-9](checklists/vertical-9-notificaciones-y-carnet.md) | 10 | 0 | 10 | 🔲 |
| 10 | Optimización, Reportes y Hardening | [vertical-10](checklists/vertical-10-optimizacion-reportes-hardening.md) | 15 | 0 | 15 | 🔲 |
| **TOTAL Fase 2** | | | **111** | **12** | **99** | 🟡 11% |

---

## Notas operativas

- **Fuente de verdad de checkboxes:** archivos `checklists/vertical-N-*.md`
- **Al completar una tarea:** marcar `- [x]` en el vertical + actualizar fila de la tabla arriba
- **Al agregar tareas nuevas:** añadir en el archivo del vertical + sumar al conteo
- **Conteo rápido:** `grep -c "^\- \[x\]" .ai/checklists/vertical-N-*.md`
- **Sincronizar con hub:** actualizar [`context/current-state.md`](context/current-state.md) al cierre de cada sesión

---

## Referencias

- [`checklist-vertical.md`](checklist-vertical.md) — dependencias entre verticales
- [`ROADMAP.md`](ROADMAP.md) — visión estratégica
- [`context/current-state.md`](context/current-state.md) — estado real del código hoy
- [Hub maestro checklist](../../centro-optico-vicente/.ai/checklist.md) — tag `[B]`
