# ADR 0012 — Convenciones HTTP para estado vacío (200 con representación vacía vs 404)

**Estado:** Aceptado
**Fecha:** 2026-07-05
**Decisores:** equipo OptiSalud Plus

## Contexto

Un reporte de producción disparó una discusión sobre cuándo un endpoint debe devolver `404` vs `200 + representación vacía`. El caso puntual fue `GET /v1/admin/members/{uuid}/medical-record`: el afiliado existe pero todavía no tiene un `medical_records` cargado. El endpoint respondía `404 medical_record.not_found`, y el frontend no podía distinguir de forma limpia esta situación de un `member.not_found` real (afiliado inexistente) sin parsear el texto localizado del `detail`.

La causa es que teníamos convenciones implícitas mezcladas:

- Endpoints de lista devolvían `200 + Page vacía` cuando no había resultados (bien).
- Endpoints de sub-recurso 1:1 devolvían `404` cuando el sub-recurso no estaba "cargado" (mal — semánticamente el recurso existe conceptualmente, solo está vacío).
- Endpoints de lookup por ID devolvían `404` cuando el ID no resolvía (bien).

El primer y el tercer caso son la convención REST correcta, pero el segundo obligaba al frontend a branchear sobre `HTTP status + parse localized detail text` para distinguir "vacío" de "error real". Ese branching es frágil (si cambia el texto de traducción se rompe) y hostil a la UX (una request que "es lo esperado en un flujo válido" no debería devolver un status code de la familia error).

## Decisión

Adoptamos la siguiente matriz para todos los endpoints REST del backend. Cada endpoint nuevo debe encajar exactamente en una fila y responder según la columna correspondiente.

### Matriz de convenciones

| Tipo de endpoint | Ejemplo del codebase | "Vacío" devuelve | "No encontrado por ID" devuelve |
|---|---|---|---|
| **Lista** — devuelve array o `Page<T>` | `GET /v1/admin/members`, `GET /v1/admin/plans`, `GET /v1/me/payments`, `GET /v1/me/referrals` | **200** + `[]` o `Page` vacía | N/A |
| **Sub-recurso 1:1 bajo un parent** — la última segmento no es un ID sino un nombre de sub-recurso | `GET /v1/admin/members/{uuid}/medical-record`, futuros `/preferences`, `/settings`, `/profile-picture` | **200** + DTO con flag `exists: false` y campos opcionales `null` | 404 del **parent** con el key correspondiente (ej. `member.not_found`) |
| **Lookup por ID directo** — el path termina con el identificador del recurso pedido | `GET /v1/admin/members/{uuid}`, `GET /v1/admin/plans/{uuid}`, `GET /v1/admin/promoters/{uuid}` | N/A (si el ID no matchea, es error real) | **404** + `ProblemDetail` con key i18n (ej. `plan.not_found`) |
| **Agregado / dashboard / computado** — devuelve una vista compuesta que "siempre existe" para el usuario | `GET /v1/promoter/dashboard`, futuro `/me/digital-card` | **200** + representación con campos vacíos donde no haya data | 404 solo si el parent (usuario/promotor) no existe — normalmente el auth ya filtra esto antes |

### Regla de bolsillo

> Si el frontend puede pintar una UI válida cuando "no hay data todavía", devolvé **200**. Reservá **404** para casos donde el request en sí es incorrecto (el ID no matchea nada, o el path es inválido).

### Cómo indicar "vacío" en un sub-recurso 1:1

Cuando devolvés `200 + representación vacía`, el DTO debe incluir un **flag booleano explícito** que le permita al frontend distinguir sin ambigüedad "populated vs empty":

```java
public record MedicalRecordDto(
    UUID uuid,                    // null cuando exists=false
    UUID personUuid,              // siempre poblado si conocemos el parent
    String bloodType,             // null cuando exists=false
    // ... resto de campos
    boolean active,               // true por default cuando exists=false
    Instant createdAt, updatedAt, // null cuando exists=false
    boolean exists                // false = empty state, true = populated
) {}
```

El nombre convencional del flag es `exists`. Alternativas nombradas como `populated`, `filled`, `hasData` son aceptables si el contexto del DTO lo hace más natural — mantener consistencia dentro del mismo módulo.

### Qué NO hacer

- **NO** codificar "vacío" solo por "todos los campos `null` en el DTO" sin flag booleano — obliga al frontend a inspeccionar múltiples campos y siempre falta un edge case.
- **NO** devolver `204 No Content` para el sub-recurso vacío — el frontend perdería el `personUuid` snapshot y no podría correlacionar con otras superficies.
- **NO** re-purposear el status `404` para "vacío" solo porque "es tradicional REST" — la tradición REST también dice que 404 es para "identifier does not match a resource", que no es lo mismo que "resource exists conceptually but has no populated data".
- **NO** devolver 200 + array vacío para un lookup por ID — si pediste `/plans/{uuid}` y el UUID no matchea, es error, no lista.

## Consecuencias

**Positivas**:

- Frontends dejan de parsear texto localizado del `ProblemDetail` para distinguir empty state de errors reales. Ganancia inmediata en robustez ante cambios de traducción.
- El contrato de cada endpoint queda derivable del path sin leer el service — "termina con `{uuid}`? es lookup, 404 si no matchea. Termina con nombre de sub-recurso? es sub-recurso, 200 con flag `exists`".
- Endpoints simétricos: `PUT` upsert + `GET` empty-state → ambos son idempotentes y siempre 2xx en happy path.
- Los monitores externos que peguen a endpoints de lectura no van a false-alarm por 404 en flujos normales (ej. dashboard admin haciendo un GET pre-emptivo).

**Negativas** (aceptadas):

- Los DTOs de sub-recursos crecen 1 campo (`exists`). Trivial en tamaño; el schema OpenAPI lo documenta.
- Contract change para endpoints ya en producción cuando adoptamos la convención — se aceptó porque los consumidores (frontend admin) todavía no habían shipeado handling de esos 404 (los estaban recibiendo como bugs).

## Precedentes en el codebase que ya siguen la convención

- **Listas → 200 vacío**: `PaymentsService.listForUser` (retorna `Page.empty()` cuando no hay pagos), `ReferralsService.listForUser` (`GET /v1/me/referrals`), `MembersService`, `PlansService`, `AlliesService`, `PromotersService`, `CommissionsService` — todos usan `Page<T>` y jamás lanzan cuando la página está vacía.
- **Lookup por ID → 404**: `PromotersService.get(uuid)`, `CommissionsService.get(uuid)`, `PlansService.get(uuid)`, `MembersService.get(uuid)` — todos usan `orElseThrow(new NoSuchElementException("X.not_found"))` que `GlobalExceptionHandler` mapea a 404.

## Aplicación de la convención (2026-07-05)

Primer endpoint refactorizado bajo esta ADR: `GET /v1/admin/members/{uuid}/medical-record` (PR #122). Ver `MedicalRecordService.getForMember` para la implementación referencia (retorno vía `orElseGet(() -> emptyDto(member))` + factory `emptyDto` que construye el DTO con `exists=false`).

Endpoints anticipados que deben adoptar el mismo patrón cuando landien:

- `GET /v1/me/digital-card` (vertical-9) — sub-recurso.
- `GET /v1/promoter/dashboard` (vertical-8) — agregado.
- Cualquier futuro `GET /v1/admin/members/{uuid}/preferences` / `/settings` / etc. — sub-recurso 1:1.

## Alternativas consideradas

1. **Mantener 404 para sub-recursos vacíos, arreglar solo el frontend**. Rechazado: (a) traslada complejidad de la API al cliente, (b) obliga a distinguir sub-tipos de 404 por texto del `detail`, (c) rompe con la convención estándar REST para listas donde ya devolvemos 200 vacío.

2. **Devolver 204 No Content para sub-recursos vacíos**. Rechazado: pierde información útil (`personUuid` en el caso del medical record), impide que el frontend correlacione con otras superficies, y semanticamente 204 es para "operación exitosa sin body" (ej. `DELETE`), no para "recurso existe conceptualmente pero está vacío".

3. **Introducir un status HTTP custom / semántico como 200 con header `X-Empty-State: true`**. Rechazado: los headers custom son invisibles para muchas herramientas de introspección de API (OpenAPI viewers, curl casual). Un campo del body es explícito y auto-documentable.

4. **Return `Optional<T>` en el JSON (`{"present": false}` vs `{"present": true, "value": {...}}`)**. Rechazado: sobrecomplica el shape del DTO, obliga a un wrapper por cada endpoint que use la convención, y frontends terminarían escribiendo el mismo boilerplate igual. El campo `exists` inline es más simple.
