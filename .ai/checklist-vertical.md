# Plan de ejecución por alcances verticales — Fase 2

> Cada alcance entrega migraciones Flyway + entidades JPA + servicios + endpoints completos y testeables.
>
> **Dashboard de progreso (conteos, %):** [`checklist.md`](checklist.md)
> **Fuente de verdad de checkboxes:** archivos `checklists/vertical-N-*.md`

## Dependencias

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

## Alcances

| # | Alcance | Archivo | Estado |
|---|---|---|---|
| 1 | Seguridad y Autenticación | [vertical-1-seguridad-y-autenticacion.md](checklists/vertical-1-seguridad-y-autenticacion.md) | 🟡 En curso (4/16) |
| 2 | Catálogos | [vertical-2-catalogos.md](checklists/vertical-2-catalogos.md) | 🔲 |
| 3 | Aliados | [vertical-3-aliados.md](checklists/vertical-3-aliados.md) | 🔲 |
| 4 | Afiliados y Familia | [vertical-4-afiliados-y-familia.md](checklists/vertical-4-afiliados-y-familia.md) | 🔲 |
| 5 | Planes y Membresías | [vertical-5-planes-y-membresias.md](checklists/vertical-5-planes-y-membresias.md) | 🔲 |
| 6 | Pagos manuales | [vertical-6-pagos-manuales.md](checklists/vertical-6-pagos-manuales.md) | 🔲 |
| 7 | Validador (CRÍTICO p95 < 200ms) | [vertical-7-validador.md](checklists/vertical-7-validador.md) | 🔲 |
| 8 | Promotores, Comisiones y Referidos | [vertical-8-promotores-comisiones-referidos.md](checklists/vertical-8-promotores-comisiones-referidos.md) | 🔲 |
| 9 | Notificaciones y Carnet digital | [vertical-9-notificaciones-y-carnet.md](checklists/vertical-9-notificaciones-y-carnet.md) | 🔲 |
| 10 | Optimización, Reportes y Hardening | [vertical-10-optimizacion-reportes-hardening.md](checklists/vertical-10-optimizacion-reportes-hardening.md) | 🔲 |
| 11 | Internacionalización (i18n) — `es` + `en`, locale híbrido (JWT > header > es-VE) | [vertical-11-i18n.md](checklists/vertical-11-i18n.md) | 🔲 |
