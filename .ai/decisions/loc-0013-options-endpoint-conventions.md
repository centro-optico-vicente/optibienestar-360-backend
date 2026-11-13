# ADR LOC-0013 (backend) — Convención `?q=` obligatorio + endpoint `/options` para selects

**Estado:** Aceptado
**Fecha:** 2026-08-05
**Relacionado con:** [ADR LOC-0006 — Convenciones de búsqueda en repositorios JPA](loc-0006-repository-search-conventions.md), [ADR LOC-0012 — Convenciones HTTP para estado vacío](loc-0012-empty-state-http-conventions.md)

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

Implementación vía el helper reusable `core/util/OptionsSupport.build(...)` — encapsula: buscar coincidencias respetando `q`/`active`/límite, y garantizar que los uuids de `currentValues` aparezcan siempre (resueltos con el `findByUuid` que cada repositorio ya expone por [ADR LOC-0006](loc-0006-repository-search-conventions.md), sin agregar un `findByUuidIn` nuevo).

### Regla 3 — `/options` no tiene permiso propio; reutiliza el del listado paginado hermano

> **Reemplazada por la enmienda 2026-11-13 (b)** — `/options` pide solo sesión válida. Se conserva el texto original como historia.

`/options` **no** introduce un permiso nuevo (ej. no existe `PLAN_OPTIONS_READ`). Se protege con el mismo `@PreAuthorize` que ya protege el `GET` de listado paginado de su recurso padre (`PLAN_VIEW_ALL`, `MEMBER_VIEW_ALL`, `USER_VIEW_ALL`, `ROLE_VIEW`, `CATALOG_VIEW_ALL`, etc.). Esto es consistente con la filosofía ya congelada en `V34__catalog_view_permission.sql`: los permisos de lectura se agrupan por **dominio de recurso**, no se fragmentan por tipo de endpoint (paginado vs. options vs. detalle). Fragmentar aquí no ganaría ningún control real — `/options` expone *menos* datos que el listado completo (solo `uuid`/`code`/`label`/`active`), nunca más — y obligaría a duplicar asignaciones de rol en las 14 entidades. Si en el futuro aparece un caso donde el listado completo expone un dato sensible que `/options` deba ocultar de cierto rol (o viceversa), ahí sí se justificaría separar el permiso — hoy no existe ese caso de uso.

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
| `ally-services` (cola `pending`) | ✅ (cerrado por este ADR) | — (cola de revisión, no es tabla principal) |
| `products` | ✅ | ✅ (label `<code> — <name>` si tiene código) |
| `product-categories` | ✅ | ✅ (código técnico → label solo nombre) |
| `campaigns`, `allies`, `corporate-contracts`, `company-bank-accounts`, `scheduled-jobs`, `subsidies`, `bonus-rules`, `commission-tiers`, `collection-commission-tiers`, `hierarchy-override-tiers`, `competitive-commission-rules` | ✅ | ✅ (enmienda 2026-11-13 b) |
| `me/allies` (aliados propios del usuario) | N/A (sin paginar) | ✅ filtro en memoria, mismo criterio insensible a tildes/`ñ` |

`service-categories`, `corporate-contracts`, `promoters`: ver Regla 4 (enmienda) — se agregan aunque el frontend aún no los use.

## Enmienda 2026-11-13 — alcance (tablas principales) y label

### Regla 4 — Toda tabla **principal** con listado tiene `/options`, aunque hoy no haya select

La Regla 2 aplica a las **tablas principales** (entidades con identidad propia: `products`, `plans`, `allies`, `campaigns`, `promoters`, `payment-methods`…). **No** aplica a tablas de asociación/detalle (pivots, líneas hijas, historiales: `ally-products` de un aliado, `plan-terms`, `member-beneficiaries`…), que se consultan siempre a través de su padre.

Ya **no** se espera a que el frontend "necesite" el select para agregarlo: cualquier tabla principal que liste en `GET /v1/admin/{recurso}` expone `GET /v1/admin/{recurso}/options` (con `?q=`, `limit`, `currentValues`, Regla 3). Esto reemplaza la excepción de arriba ("quedan fuera — no tienen ningún `<select>` hoy"): `service-categories`, `corporate-contracts`, `promoters` y, sobre todo, **`products`** (producto/servicio) se agregan reutilizando `OptionsSupport`. `products` y `product-categories` ya lo tienen.

### Regla 5 — Label del `/options`: `<code> — <name>` solo con código legible

El `label` es lo que el usuario ve en el select; el frontend no lo recompone (spec frontend `07-forms.md`, "Código en selects").

- Código **legible/identificador de negocio** (`V`, `0156`, `J`, SKU de producto, código de plan/rango): `label = code + " — " + name` (`DocumentTypeService`, `BankService`, `PlansService`…). **Producto/servicio incluido**: el código ayuda a distinguir ítems de nombre parecido.
- Código **identificador técnico** en `SNAKE_CASE` (`BANK_TRANSFER`, `PAGO_MOVIL`, `MEMBERSHIP_FEE`, `MPPS`): `label = name` solamente — el código ensucia el select sin aportar. Se sigue devolviendo en el campo `code` del `OptionDto`. Hoy: `payment-methods`, `payment-categories`, `licensing-bodies`.
- Sin código (`User`, `Member`, `Role`, `Campaign`, `Ally`, `City`, `Occupation`): `label = name`, `code = null`.

**Cumplimiento actual de la Regla 5 (2026-11-13):**

| Servicio (`labelOf`) | Código | Hoy | Debe ser |
|---|---|---|---|
| `document-types`, `banks`, `genders`, `states`, `plans`, `promoter-ranks` | legible | `código — nombre` | ✅ |
| `payment-methods`, `payment-categories`, `licensing-bodies` | técnico | nombre | ✅ |
| `marital-statuses` (`SOLTERO`), `professions` (`INGENIERIA_CIVIL`), `ally-types` (`CLINICA`) | técnico | `código — nombre` | ❌ pendiente: solo nombre |
| `promoter-types` | por verificar | `código — nombre` | por verificar |
| `products` | legible (SKU, opcional) | `código — nombre` (solo nombre si no tiene código) | ✅ |
| `product-categories` | técnico (`SERVICIOS_FUNERARIOS`) | nombre | ✅ |

## Enmienda 2026-11-13 (b) — `/options` con sesión válida, desacoplado de la pantalla

### Regla 3 (reemplazo) — `/options` pide solo `isAuthenticated()`

`<X>_VIEW_ALL` hace dos cosas: abre la pantalla en el menú del frontend (`nav.ts` → `requires`) y autoriza el listado admin. Si además autoriza `/options`, no hay forma de dejar que alguien **liste en un select sin ver la pantalla**: el afiliado que registra un pago necesita bancos y monedas, el encargado de aliados necesita productos, el asesor que registra un afiliado en una campaña necesita campañas — y ninguno debe ver esas pantallas de gestión.

Por eso todo `/options` admin usa `@PreAuthorize("isAuthenticated()")` (constante `LOOKUP` en cada controller). `<X>_VIEW_ALL` sigue gobernando solo la pantalla, el listado paginado y el detalle. Un endpoint que se llame `.../options` pero **no** sea un lookup de entidad (p. ej. `/memberships/{uuid}/promotion/options`, promociones elegibles para una acción) conserva su permiso de acción.

**Riesgo aceptado (MVP):** `/options` expone solo `uuid`, `code`, `label` y `active`, nunca el DTO; pero en usuarios, miembros, asesores y subsidios el `label` es el nombre de una persona.

**Fase 2 (pendiente):** para entidades con datos sensibles (pagos, asesores, usuarios, aliados, afiliados, consumos) se agrega un permiso `<X>_LOOKUP` por entidad — `hasAnyAuthority('<X>_VIEW_ALL', '<X>_LOOKUP')` —, otorgable por rol desde el editor de roles y fuera de cualquier `requires` del menú.

### Regla 4 (refuerzo) — toda entidad maestra con listado tiene `/options` **y** `GET /{uuid}`

Toda lista desplegable del frontend llama al `/options` de su entidad, nunca al listado completo. Quedan fuera: transacciones/documentos (pagos, comisiones, premios, reclamos, tasas), auditorías, historiales `/v1/me/*` (salvo los que alimentan un select, como `/v1/me/allies/options`) y `/v1/public/*`.

Todo `/options` acepta `q` de búsqueda libre sobre los `SEARCHABLE_FIELDS` de su listado (vía `SearchSpecifications.acrossFields`): insensible a mayúsculas y a tildes con `immutable_unaccent(lower(...))` en ambos lados, así `pena` encuentra `Peña` y `merida` encuentra `Mérida`.

### Filtros del servidor en `/options` y búsqueda siempre en el servidor

Toda búsqueda de un select se hace en el servidor con `q` (el frontend nunca filtra localmente: una lista desplegable trae a lo sumo 50 filas y filtrar esas ocultaría el resto). Cuando el formulario necesita acotar por un hecho de la entidad, el `/options` lo recibe como parámetro en lugar de traer todo y filtrar: `ally-types/options?segment=B2B|B2P` (un aliado no mezcla tipos de ambos segmentos) y `payment-categories/options?direction=IN|OUT`. `limit` por defecto 50, tope 200.

`GET /v1/me/payment-methods` (sesión válida, DTO completo con las banderas `mandatory*`) acepta `q` y `limit` y busca en el servidor con el mismo motor del listado admin; sin ninguno devuelve la lista completa en caché.

### Pendiente — atributos extra del método de pago

El formulario de pago usa las banderas `mandatory*` del método para mostrar/ocultar campos; `OptionDto` no las trae. Hasta decidir entre (a) lista con atributos (más pesada, UI fluida) y (b) `/options` + detalle del seleccionado (liviana, una consulta extra), el select de método de pago usa `GET /v1/me/payment-methods?q=` (búsqueda en el servidor, DTO completo). Bancos y monedas no tienen ese problema: sus formularios solo usan `code` y el label, que `/options` ya trae.

Alcance del pendiente: afecta a **los dos** endpoints de métodos de pago, no solo al de sesión.

- `GET /v1/me/payment-methods` (sesión): ya acepta `q`/`limit`; lo usan los formularios de pago del portal y las pantallas de cuentas bancarias.
- `GET /v1/admin/payment-methods` (listado completo): lo consumen `PaymentFormModal`, `PaymentMasterFilters`, `PromoterPayoutAccountsTab`, `CommissionPayoutBySelectionModal` y `affiliation-requests`. Los consumidores que solo filtran (no leen `mandatory*`) pueden migrar ya a `/v1/admin/payment-methods/options`; los formularios dependen de la decisión (a)/(b).
- `/v1/public/payment-methods` queda fuera (regla 5: `/v1/public/*` no se toca).

## Por qué

- Consistencia: cualquier entidad nueva sabe qué exponer sin negociarlo caso por caso.
- Performance: un select no necesita el DTO completo ni metadata de paginación — menos payload, menos serialización, menos joins innecesarios si el DTO completo trae relaciones anidadas.
- UX: `currentValues` + `active` evita que un valor ya asignado (aunque hoy esté inactivo) desaparezca silenciosamente de un formulario de edición.

## Consecuencias

- Al crear una entidad nueva con listado admin (ver `playbooks/new-entity.md` / `playbooks/new-endpoint.md`), agregar `?q=` y `/options` es **obligatorio antes del primer PR**, salvo justificación explícita (entidad sin campos de texto).
- El consumo desde el frontend (reemplazar los `list(...)` completos actuales por `/options` en los selects ya identificados) es un trabajo aparte, no cubierto por este ADR.
