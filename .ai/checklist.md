# Checklist operacional — OptiBienestar 360 Backend

> **Propósito:** dashboard de progreso global. Los checkboxes canónicos viven en los archivos de fase y vertical en [`checklists/`](checklists/).
>
> **Orden de ejecución y dependencias entre verticales:** sección [Dependencias entre verticales](#dependencias-entre-verticales) más abajo.
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

| Fase | Archivo | Tareas | Hechas | Pendientes | % avance | Estado |
|---|---|---|---|---|---|---|
| FASE 0 — Bootstrap infra | [fase-0-bootstrap.md](checklists/fase-0-bootstrap.md) | 5 | 5 | 0 | 100% | ✅ Completa |
| FASE 1 — Bootstrap Spring Boot | [fase-1-bootstrap-backend-spring-boot.md](checklists/fase-1-bootstrap-backend-spring-boot.md) | 37 | 37 | 0 | 100% | ✅ Completa |

---

## Fase 2 — Afiliaciones y Membresías (verticales)

> Fuente de verdad: archivos `vertical-N-*.md`. El archivo [`fase-2-afiliaciones-y-membresias.md`](checklists/fase-2-afiliaciones-y-membresias.md) tiene referencias desactualizadas — usar los verticales.

| # | Vertical | Archivo | Tareas | Hechas | Pendientes | % avance | Estado |
|---|---|---|---|---|---|---|---|
| 1 | Seguridad y Autenticación | [vertical-1](checklists/vertical-1-seguridad-y-autenticacion.md) | 44 | 39 | 5 | 89% | 🟡 hardening/tests |
| 2 | Catálogos | [vertical-2](checklists/vertical-2-catalogos.md) | 9 | 9 | 0 | 100% | ✅ Completa |
| 3 | Aliados | [vertical-3](checklists/vertical-3-aliados.md) | 21 | 14 | 7 | 67% | 🟡 |
| 4 | Afiliados y Familia | [vertical-4](checklists/vertical-4-afiliados-y-familia.md) | 25 | 11 | 14 | 44% | 🟠 |
| 5 | Planes y Membresías | [vertical-5](checklists/vertical-5-planes-y-membresias.md) | 32 | 17 | 15 | 53% | 🟡 |
| 6 | Pagos manuales | [vertical-6](checklists/vertical-6-pagos-manuales.md) | 10 | 10 | 0 | 100% | ✅ Completa |
| 7 | Validador | [vertical-7](checklists/vertical-7-validador.md) | 9 | 6 | 3 | 67% | 🟡 |
| 8 | Promotores, Comisiones y Referidos | [vertical-8](checklists/vertical-8-promotores-comisiones-referidos.md) | 57 | 11 | 46 | 19% | 🟠 |
| 9 | Notificaciones y Carnet digital | [vertical-9](checklists/vertical-9-notificaciones-y-carnet.md) | 10 | 1 | 9 | 10% | 🟠 |
| 10 | Optimización, Reportes y Hardening | [vertical-10](checklists/vertical-10-optimizacion-reportes-hardening.md) | 37 | 1 | 36 | 3% | 🔴 |
| **TOTAL Fase 2** | | | **254** | **119** | **135** | **47%** | 🟡 |

> **% avance** = round(100 × hechas / tareas), redondeado al entero más cercano, contando checkboxes `- [ ]` / `- [x]` en cada archivo vertical (el método de "Conteo rápido" abajo). Cada archivo repite su fila como tabla de totales al inicio. Bandas del estado: ✅ 100% · 🟡 50–99% · 🟠 10–49% · 🔴 <10%.
> Los conteos previos estaban desactualizados (marcaban 0 hechas en verticales ya construidos) y usaban una granularidad de tarea distinta a la de los archivos; esta tabla se recalculó desde los checkboxes reales.

### Cross-cutting y adicionales v2

> Fuera del total de Fase 2 (verticales 1–10) porque no son alcance original: el **11** es cross-cutting (i18n, paralelizable) y el **12** es un adicional v2 (ver [Adicionales v2](#adicionales-v2)).

| # | Vertical | Archivo | Tareas | Hechas | Pendientes | % avance | Estado |
|---|---|---|---|---|---|---|---|
| 11 | Internacionalización (i18n) — `es` + `en`, locale híbrido (JWT > header > es-VE) | [vertical-11](checklists/vertical-11-i18n.md) | 42 | 41 | 1 | 98% | 🟡 |
| 12 | **[v2]** Subsidios y Exoneraciones | [vertical-12](checklists/vertical-12-subsidios-y-exoneraciones.md) | 20 | 0 | 20 | 0% | 🔲 |

---

## Dependencias entre verticales

```
1 (Auth) ──────────────────────────────────────────► todos los demás
2 (Catálogos) ──► 3 (Aliados), 4 (Afiliados)
3 (Aliados) ────► 7 (Validador)
4 (Afiliados) ──► 5 (Membresías)
5 (Membresías) ─► 6 (Pagos), 7 (Validador)
6 (Pagos) ──────► 8 (Promotores)
1–8 ────────────► 9 (Notificaciones)
1–9 ────────────► 10 (Optimización / Hardening)
11 (i18n) ──────► cross-cutting, paralelizable con cualquiera; idealmente
                  arranca tras 1 (Auth) porque mete claim `locale` al JWT
                  y reescribe los mensajes del módulo auth.
```

Cada alcance entrega migraciones Flyway + entidades JPA + servicios + endpoints completos y testeables.

---

## Adicionales v2

> Adicionales surgidos post-arranque (PDF "Informe de Avances y Solicitud de Continuidad v1", mesas técnicas mayo–junio 2026). Mapa de trazabilidad: [`scope-additions-v2.md`](scope-additions-v2.md).
>
> **Convención:** los ítems v2 dentro de cada vertical están marcados con tag `[v2]` y agrupados en una sección "Adicionales v2" al final del archivo. Vertical-12 es íntegramente v2 (nuevo).
>
> Verticales afectados por v2 sin cambiar su numeración: 3, 4, 5, 8. Vertical nuevo: 12. Versionado escalable — futuras v3 usarán `[v3]` y `scope-additions-v3.md`.

---

## Notas operativas

- **Fuente de verdad de checkboxes:** archivos `checklists/vertical-N-*.md`
- **Al completar una tarea:** marcar `- [x]` en el vertical + actualizar fila de la tabla arriba
- **Al agregar tareas nuevas:** añadir en el archivo del vertical + sumar al conteo
- **Conteo rápido:** `grep -c "^\- \[x\]" .ai/checklists/vertical-N-*.md`
- **Sincronizar con hub:** actualizar [`context/current-state.md`](context/current-state.md) al cierre de cada sesión

---

## Referencias

- [`ROADMAP.md`](ROADMAP.md) — visión estratégica
- [`context/current-state.md`](context/current-state.md) — estado real del código hoy
- [Hub maestro checklist](../../centro-optico-vicente/.ai/checklist.md) — tag `[B]`
