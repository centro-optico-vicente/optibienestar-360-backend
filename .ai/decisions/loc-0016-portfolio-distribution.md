# ADR LOC-0016 (backend) — Distribución de cartera y filtro "Sin asesor"

**Estado:** Aceptado
**Fecha:** 2026-11-14
**Decisores:** equipo OptiBienestar 360
**Relacionado:** [ADR LOC-0013 `/options`](loc-0013-options-endpoint-conventions.md) · [hub ADR 0014 `_Display`](https://github.com/centro-optico-vicente/centro-optico-vicente/blob/main/.ai/decisions/0014-display-value-convention.md) · [hub ADR 0017 comisiones](https://github.com/centro-optico-vicente/centro-optico-vicente/tree/main/.ai/decisions/) · frontend PR [#364](https://github.com/centro-optico-vicente/optibienestar-360-frontend/pull/364)

## Contexto

- El afiliado que entra **sin código de referido** queda con `members.promoter_id = NULL`: `PromoterResolver.resolveForEnrollment` no estampa INSTITUCION (el comentario viejo de `MembersService.buildMember` decía lo contrario y se corrigió). El asesor de sistema INSTITUCION (`promoters.is_system = true`) solo aparece al **atribuir** un pago/comisión (`PromoterResolver.resolveForAttribution`) o cuando `portfolio-to-supervisor` no encuentra supervisor activo.
- No había forma de filtrar ni mover en bloque a esos afiliados, ni de repartir la cartera de un asesor dado de baja entre varios, ni de rotar parte de la cartera de un asesor sobrecargado. Aliados solo se reasignaban de a uno y las solicitudes de afiliación no se podían reasignar.

## Decisión

### Definición de "sin asesor"

**Sin asesor** = `promoter_id IS NULL` **o** el asesor es el de sistema (`is_system = true`). Ambos casos se tratan igual para el dinero y para "el primero que cobra", así que se agrupan en un solo filtro. La misma definición aplica a afiliados (`members.promoter_id`), aliados (`allies.captured_by_promoter_id`) y solicitudes de afiliación (`affiliation_requests.promoter_id`).

### Endpoints

| Método y ruta | Permiso | Qué hace |
|---|---|---|
| `GET /v1/admin/members?unassigned=true` | `MEMBER_VIEW_ALL` | Filtro adicional, combinable con los demás. |
| `GET /v1/admin/members/advisor/candidates` | `MEMBER_ASSIGN_PROMOTER` | `Page<MemberCandidateDto>` paginada (`page`, `size`, `sort`). Params: `source=UNASSIGNED\|ADVISOR` (obligatorio), `sourcePromoterUuid` (obligatorio con `ADVISOR`; acepta asesores **inactivos** y el de sistema), `q` (nombre / cédula), `planUuid` (plan de la membresía activa), `status` (estado **del afiliado**: ACTIVE, SUSPENDED, INACTIVE…). Solo afiliados activos (`is_active = true`, igual que la cartera del asesor). Orden: `displayName` (por defecto, asc) o `createdAt` (también `document` y `promoter_Display`), siempre desempatado por `uuid`. |
| `POST /v1/admin/members/advisor/distribute/preview` | `MEMBER_ASSIGN_PROMOTER` | Mismo cálculo que `distribute`, **sin escribir**. |
| `POST /v1/admin/members/advisor/distribute` | `MEMBER_ASSIGN_PROMOTER` | Reparte la selección, todo o nada en una transacción. |
| `GET /v1/admin/advisors/options?includeInactive=true` | sesión | Nuevo `includeInactive` (default `false`) junto a `includeSystem`, para elegir un asesor dado de baja como origen. |
| `GET /v1/admin/allies?unassigned=true` y `GET /v1/admin/ally-requests?unassigned=true` | los de cada listado | Filtro "sin asesor". |
| `POST /v1/admin/allies/advisor/bulk-assign` | `ALLY_ASSIGN_PROMOTER` | `{ allyUuids (1..500), promoterUuid }` → `{ total, skipped }`. Todo o nada; cada cambio pasa por `AlliesService.assignAdvisor` (queda auditado igual que el individual). |
| `GET /v1/admin/affiliation-requests?unassigned=true` | `AFFILIATION_REQUEST_VIEW_ALL` | Filtro "sin asesor". |
| `PUT /v1/admin/affiliation-requests/{uuid}/advisor` | `AFFILIATION_REQUEST_ASSIGN_PROMOTER` (V250) | `{ promoterUuid, reason? }` → detalle de la solicitud. Solo DRAFT o PENDING (422 `affiliation_request.advisor.invalid_state`). El `referral_code` sigue al asesor, porque la aprobación enrola al afiliado por ese código; un borrador asistido pasa al portal del nuevo asesor. Se registra en el log (UPDATED, campo `advisor`, motivo como comentario). |

**Cuerpo de `distribute` / `preview`:**

```json
{ "memberUuids": ["…"],                       // o bien
  "filter": { "source": "UNASSIGNED|ADVISOR", "sourcePromoterUuid": "…", "q": "…", "planUuid": "…", "status": "…" },
  "excludeMemberUuids": ["…"],                // solo tiene sentido con filter ("todos los N resultados" menos estos)
  "targets": [{ "promoterUuid": "…", "count": 5 }],
  "mode": "EQUAL" | "COUNT",
  "reason": "…" }                             // obligatorio, ≤ 500
```

**Respuesta:** `{ total, skipped, targets: [{ promoterUuid, promoterDisplay, count }] }` con `total = skipped + Σ targets.count`: `total` = afiliados que cubrió el reparto (toda la selección en EQUAL, la suma de cantidades en COUNT), `skipped` = los que ya estaban en el asesor que les tocó, `count` = los que efectivamente se mueven a cada destino.

### Reglas

- Exactamente uno de `memberUuids` / `filter` (422 `member.distribution.selection_invalid`); selección vacía → 422 `member.distribution.empty`; más de **2000** afiliados → 422 `member.distribution.too_many`.
- Destinos: 1..20 (`member.distribution.targets_invalid`), distintos (`…targets_duplicate`), existentes (404), activos (`promoter.inactive`) y no de sistema (`promoter.system_not_assignable`).
- **EQUAL**: round-robin sobre los destinos en un orden estable (nombre sin distinguir mayúsculas, luego `uuid`); 10 entre 3 → 4/3/3.
- **COUNT**: cada destino necesita `count ≥ 1` (`member.distribution.count_required`); la suma no puede superar la selección (`…count_exceeds`); los afiliados más allá de la suma quedan intactos (en el mismo orden estable, los primeros `count₁` van al primer destino, los siguientes `count₂` al segundo…).
- Los que ya están en el destino que les tocó se omiten (`skipped`), igual que `bulk-assign`.
- La escritura reutiliza `MemberPromoterService` (`reassignAll`): una fila `member_promoter_assignments` por afiliado movido (desde → hacia, actor, motivo) y la notificación al afiliado (salvo destino de sistema, que aquí no puede darse). No recalcula comisiones ya generadas (PDF 2.a).
- Afiliado inactivo en `memberUuids` → 422 `member.distribution.member_inactive`; desconocido → 404 y no se escribe nada.

## Pregunta abierta — "asesor de piso/oficina detrás de INSTITUCION"

Hoy las comisiones atribuidas a INSTITUCION se **generan y se marcan PAID sin pago saliente** (`CommissionPayoutService`): son utilidad de la administración, no se le paga a nadie. Si el personal administrativo (asesor de piso/oficina) debe cobrar comisión por los afiliados que atiende, la vía compatible con el hub ADR 0017 es **darlo de alta como asesor** (tipo `EMPLEADO`) y asignarle cartera con esta misma pantalla, en vez de pagar "a INSTITUCION". **Sin decidir:** si es una sola persona o varias, y desde cuándo. No hay código para esto; queda pendiente de decisión de negocio.

## Consecuencias

- La cartera sin asesor y la de asesores dados de baja se mueve en bloque con auditoría completa.
- El frontend deja de cargar la cartera completa sin paginar para reasignar: usa `candidates` paginado.
- Nuevo permiso `AFFILIATION_REQUEST_ASSIGN_PROMOTER` (V250), otorgado a todo rol que ya tenía `MEMBER_ASSIGN_PROMOTER`.
