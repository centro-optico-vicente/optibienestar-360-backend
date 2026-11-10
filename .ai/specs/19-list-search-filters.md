# 19 — Búsqueda `q`, filtros maestros y rangos de fecha en listados

> **Norma general (hub):** [`08-debounce-search-inputs.md`](https://github.com/centro-optico-vicente/centro-optico-vicente/blob/main/.ai/specs/08-debounce-search-inputs.md). Este spec es su parte de backend.
> **Relacionadas:** [`06-rest-api.md`](06-rest-api.md) (contrato de paginación, RSQL y `/options`), [`02-database.md`](02-database.md) (índices trigram).
> **Frontend:** [`14-list-search-debounce.md`](https://github.com/centro-optico-vicente/optibienestar-360-frontend/blob/main/.ai/specs/14-list-search-debounce.md).

## Regla

Todo endpoint que liste registros —tabla paginada, sub-lista de una pantalla de detalle o `/options` para una lista desplegable— acepta `?q=` de texto libre, **insensible a mayúsculas y a tildes** (`merida` encuentra `Mérida`). Además:

- `q` cubre también los **nombres de las entidades relacionadas** que la pantalla muestra (afiliado, asesor, banco, referencia de una línea de pago…), no solo los campos propios.
- Si la entidad tiene tablas maestras asociadas, el listado se puede **filtrar por ellas** (`memberUuid`, `bankUuid`, `categoryUuid`…).
- Si muestra **fechas operacionales o transaccionales**, acepta un **rango** `dateFrom` / `dateTo`. Las bitácoras (sesiones, ejecuciones de jobs, cambios de datos, reportes) quedan fuera.

## Herramientas

| Necesidad | Usar |
|---|---|
| `q` sobre campos propios o de asociaciones **no nulas** | `SearchSpecifications.acrossFields(q, "name", "person.fullName", …)` |
| `q` sobre una asociación **nula** o dentro de un `EXISTS` | `SearchSpecifications.anyLike(cb, path, q, campos…)` sobre un `LEFT JOIN` explícito o un subquery |
| Rango sobre un `Instant` / `LocalDate` | `DateRangeSpecifications.instantBetween` / `dateBetween`; solapamiento de vigencias: `overlaps` / `overlapsOffset` |
| Lista pequeña ya cargada en Java | `TextSearch` (quita tildes con `Normalizer`) y `DateRangeSpecifications.instantIn` / `dateIn` (desde el PR #409) |
| Consulta `@Query` (HQL/JPQL) | `cast(function('immutable_unaccent', lower(campo)) as string) LIKE cast(function('immutable_unaccent', lower(concat('%', :q, '%'))) as string)` |

`acrossFields` usa `immutable_unaccent(lower(campo))`, la misma expresión de los índices trigram (`persons.full_name` V15, `allies.name` V11, `products.name` V197), de modo que Postgres puede usarlos. Cubrir más campos con índice es una decisión de indexado aparte: sin índice en todas las ramas del `OR`, cae a Seq Scan.

## Reglas de implementación

1. **Asociaciones nulas con `LEFT JOIN` explícito.** Navegar con `root.get("promoter").get("displayName")` crea un `INNER JOIN` implícito y descarta en silencio los registros sin asesor o sin beneficiario.
2. **Colecciones hijas con `EXISTS`** (p. ej. método o banco en `payment_lines`) y **many-to-many con `IN (subquery)`** (tipos y profesiones de un aliado), para no duplicar filas en la página. No se expresan bien por RSQL, cuya lista blanca solo admite escalares.
3. **Filtros maestros como parámetros de primera clase**, agrupados en un `record` cuando son varios (`PaymentListFilter`, `MemberListFilter`, `AllyListFilter`, `AffiliationRequestListFilter`), con un `none()` para los llamadores que no filtran.
4. **Fechas:** `yyyy-MM-dd`, ambos extremos inclusivos, interpretados como **día de `America/Caracas`** (ADR 0010). En `Instant`, `>= dateFrom 00:00` y `< dateTo + 1 día 00:00`.
5. **HQL: siempre `cast(… as string)`** alrededor de `function('immutable_unaccent', …)`. Hibernate tipa `function()` como `Object` y rechaza un `Object` como operando de `LIKE`; la validación de la consulta falla al **arrancar la aplicación** y tumba todos los tests de contexto.
6. **Compatibilidad:** al agregar parámetros a un método de servicio, conservar la firma anterior como *overload* que delega con `null`.
7. **Tests de controlador:** los `*ControllerIT` simulan el servicio con Mockito y `any()` **por argumento**. Si el controlador pasa a llamar el overload nuevo, hay que actualizar el stub a la nueva aridad; con la antigua el mock devuelve `null` y la respuesta sale vacía (`json can not be null or empty`). Revisar `src/test` al cambiar una firma.
8. **`/options`:** `q` insensible a tildes, `limit` y `currentValues` (ver [LOC-0013](../decisions/loc-0013-options-endpoint-conventions.md)). Cada endpoint nuevo lleva su `*OptionsIT`.

## Checklist para un listado nuevo

- [ ] `?q=` con los campos propios y los de las entidades relacionadas que se muestran
- [ ] Filtros por cada tabla maestra relevante
- [ ] `dateFrom` / `dateTo` si hay fechas operacionales
- [ ] Asociaciones nulas con `LEFT JOIN`; colecciones con `EXISTS` / `IN`
- [ ] Firma anterior conservada y stubs de los ITs actualizados
- [ ] Campos nuevos buscados evaluados para índice trigram si la tabla es grande
