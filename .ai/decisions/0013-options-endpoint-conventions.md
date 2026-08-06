# ADR 0013 (local backend) — Convención `?q=` obligatorio + endpoint `/options` para selects

**Estado:** Aceptado
**Fecha:** 2026-08-05
**Relacionado con:** [ADR 0006 — Convenciones de búsqueda en repositorios JPA](0006-repository-search-conventions.md), [ADR 0012 — Convenciones HTTP para estado vacío](0012-empty-state-http-conventions.md)

## Contexto

Una auditoría (2026-08-05) confirmó que **23 de 24 endpoints admin de listado paginado ya tenían `?q=`** (búsqueda libre vía `SearchSpecifications.acrossFields`) — solo faltaba la cola de revisión de servicios de aliados. La convención existía de facto, replicada endpoint por endpoint, pero nunca se congeló como regla — cada entidad nueva corría el riesgo de "olvidarse" el `?q=` sin que ningún checklist lo marcara como obligatorio.

Por separado, se identificó un patrón de desperdicio: cuando el frontend necesita poblar un `<select>`/dropdown (ej. elegir un `Plan` al inscribir una membresía, un `Member`/`User` en un typeahead), reutiliza el endpoint de listado admin completo — que devuelve el DTO entero (fees, timestamps, roles anidados, datos médicos, etc.) envuelto en `Page` (con `totalElements`, `totalPages`…). Es tráfico y cómputo desperdiciado para un caso de uso que solo necesita `{uuid, código, etiqueta}`. No existía ningún endpoint "options" en el proyecto — greenfield, sin convención previa que romper.

## Decisión

### Regla 1 — Todo endpoint de listado debe exponer `?q=`

Todo endpoint `GET` que devuelve `Page<DTO>` (admin o público) **debe** exponer `?q=` con `SearchSpecifications.acrossFields` sobre sus campos de texto visibles. No es opcional "si aplica" — es obligatorio salvo que la entidad no tenga ningún campo de texto buscable (ej. tablas pivot puras sin `name`/`code`/`description`).

### Regla 2 — Todo endpoint de listado debe tener un endpoint hermano `/options`

`GET /v1/{grupo}/{recurso}/options` devuelve `List<OptionDto>` **sin paginar** (sin `Page`, sin `totalElements`/`totalPages`), pensado exclusivamente para poblar selects/dropdowns/typeaheads.

```java
public record OptionDto(UUID uuid, String code, String label, boolean active) {}
```

`code` es `null` cuando la entidad no tiene un código propio (ej. `User`, `Member` — ahí el "código" natural sería el documento de identidad de la persona, no un campo propio de la entidad). `active` refleja el estado real del registro — permite al frontend renderizar atenuado/"(inactivo)" un valor ya asignado que ya no está activo, sin ocultarlo ni dejarlo en blanco.

Query params estándar:

| Param | Default | Semántica |
|---|---|---|
| `q` | — | Igual que en el listado paginado — mismo `SEARCHABLE_FIELDS`. |
| `limit` | `50` | Tope duro `200` — si el cliente pide más, se recorta. |
| `currentValues` | — | Lista de uuids (`?currentValues=<uuid1>,<uuid2>`) que **siempre** aparecen en la respuesta, con su `active` real, sin importar `q`/`limit`/estado. |

`active=true` es un filtro implícito salvo que el uuid venga en `currentValues` — así un valor ya asignado a un registro (ej. un `Plan` desactivado que sigue vigente en una membresía existente) no desaparece silenciosamente del select al editar.

Implementación vía el helper reusable `core/util/OptionsSupport.build(...)` — encapsula: buscar coincidencias respetando `q`/`active`/límite, y garantizar que los uuids de `currentValues` aparezcan siempre (resueltos con el `findByUuid` que cada repositorio ya expone por [ADR 0006](0006-repository-search-conventions.md), sin agregar un `findByUuidIn` nuevo).

## Tabla resumen — estado tras esta implementación

| Entidad | `?q=` | `/options` |
|---|---|---|
| `plans` | ✅ | ✅ |
| `members` | ✅ | ✅ |
| `users` | ✅ | ✅ |
| `states` | ✅ | ✅ (+ `countryUuid`) |
| `cities` | ✅ | ✅ (+ `stateUuid`) |
| `genders`, `marital-statuses`, `occupations`, `medical-specialties`, `ally-types`, `document-types` | ✅ | ✅ |
| `roles` | N/A (sin paginar) | ✅ |
| `countries` | ✅ | ✅ |
| `ally-services` (cola `pending`) | ✅ (cerrado por este ADR) | — (no tiene select en frontend hoy) |

`service-categories`, `corporate-contracts`, `promoters` como select quedan fuera — no tienen ningún `<select>` en el frontend hoy; se agregan después reutilizando `OptionsSupport` si el frontend los necesita.

## Por qué

- Consistencia: cualquier entidad nueva sabe qué exponer sin negociarlo caso por caso.
- Performance: un select no necesita el DTO completo ni metadata de paginación — menos payload, menos serialización, menos joins innecesarios si el DTO completo trae relaciones anidadas.
- UX: `currentValues` + `active` evita que un valor ya asignado (aunque hoy esté inactivo) desaparezca silenciosamente de un formulario de edición.

## Consecuencias

- Al crear una entidad nueva con listado admin (ver `playbooks/new-entity.md` / `playbooks/new-endpoint.md`), agregar `?q=` y `/options` es **obligatorio antes del primer PR**, salvo justificación explícita (entidad sin campos de texto).
- El consumo desde el frontend (reemplazar los `list(...)` completos actuales por `/options` en los selects ya identificados) es un trabajo aparte, no cubierto por este ADR.
