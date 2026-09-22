# ADR 0014 — Refactor de servicios grandes: bajo demanda, no proactivo

**Estado:** Aceptado
**Fecha:** 2026-09-22
**Decisores:** equipo OptiBienestar 360

## Contexto

Una auditoría de `:compileTestJava` fallando repetidamente (cada vez que se agrega una dependencia a un servicio o un campo a un record, los tests que construyen esas clases a mano dejan de compilar — casos reales: `PaymentsService` con `MemberRepository` faltante, `CommissionTiersService`/`CommissionTierDto`/`CommissionTierCreateRequest`/`CommissionTierUpdateRequest` con `CurrencyRepository` y campos de currency faltantes) llevó a revisar si la causa raíz era solo la construcción manual de tests o si había servicios sobrecargados (God Services) detrás.

Conteo de dependencias (`private final` en constructor) sobre `src/main/java/**/*Service.java`:

| Servicio | Deps | Líneas |
|---|---|---|
| `PaymentsService` | 22 (20 en campo, 2 legacy-only) | ~1000 |
| `MembersService` | 20 | — |
| `PromotersService` | 13 | — |
| `AuthService` | 12 | — |
| `CommissionPayoutService` | 11 | — |
| `CampaignService` | 11 | — |
| `UserService` | 10 | — |
| `AlliesService` | 10 | — |
| `CommissionTiersService` | 6 | ~240 |

`PaymentsService` se auditó en detalle: 5 clusters de responsabilidad casi sin solapamiento (registro IN, registro/gestión OUT, review workflow + efectos de comisión, soporte de archivo/presigned URL, notificaciones), un compatibility constructor legacy mantenido a mano, y lógica de validación de mandatory-fields duplicada entre `applyOutFields` y `registerInternal` — señales concretas de que el tamaño ya duele, no solo "se ve grande". En contraste, `CommissionTiersService` (6 deps, responsabilidad única CRUD + invariante pct-XOR-flat) audita limpio — no todo servicio con varias dependencias es un God Service; el criterio no es un número aislado sino cohesión + duplicación + evidencia de dolor real.

## Decisión

**No se ejecuta un refactor proactivo de los servicios grandes existentes.** `PaymentsService`, `MembersService` y el resto de la tabla quedan como están hasta que alguien los toque por una razón de negocio (nuevo endpoint, bugfix, nueva dependencia).

**Regla aplicada al tocar un servicio** (edit o creación), como parte del PR que ya lo está tocando:

1. Si el servicio (o el que se está por crear) queda en **más de ~12 dependencias de constructor** O **más de ~600 líneas**, evaluar extracción de responsabilidades antes de mergear ese PR — no como PR separado planificado a futuro.
2. La evaluación es "¿este cambio agrega una responsabilidad nueva o solo extiende una existente?". Agregar una dependencia a una responsabilidad ya presente no dispara split. Agregar una responsabilidad nueva (ej. otro tipo de notificación, otro flujo de review) es la señal fuerte para extraer.
3. Buscar duplicación entre métodos del mismo servicio (mismo patrón de validación repetido) — es la señal más barata de detectar y la más indicativa de que hay 2+ responsabilidades mezcladas.
4. Si el split no es seguro dentro del alcance del PR actual (alto riesgo, flujo de producción sensible como pagos), documentarlo como deuda conocida en `context/current-state.md` en vez de forzarlo — no dejarlo solo en la cabeza de quien lo tocó.

**Por qué bajo demanda y no proactivo:**
- El costo de spliterar servicios sanos en producción (riesgo de regresión) es alto contra un beneficio que solo se cobra la próxima vez que se edite ese archivo.
- Evita que el refactor se convierta en trabajo de alcance no pedido durante una sesión que solo buscaba arreglar CI.
- El umbral explícito (12 deps / 600 líneas) evita que el criterio quede implícito y que servicios como `MembersService` o `PromotersService` sigan creciendo sin que nadie lo note hasta que duela tanto como `PaymentsService`.

**Relación con la causa raíz de CI:** el fallo de `:compileTestJava` se resuelve con **test factories/Object Mother estáticas por clase** (arreglás un archivo cuando cambia el constructor, no N tests dispersos) — independiente de esta regla de refactor. Ver discusión en sesión 2026-09-22 sobre `@InjectMocks` vs. factories y builders vs. `@With` en records de request/DTO; no re-litigar sin un ADR nuevo que supere esto.

## Alternativas consideradas

### Opción A: Refactor proactivo de todos los servicios >10 deps ahora
- **Pro:** resuelve la deuda de una vez, contexto de la auditoría fresco.
- **Contra:** alto riesgo sobre flujos de producción (pagos, membresías) sin que haya una necesidad de negocio empujando el cambio; expande el alcance de la tarea original (arreglar CI) a un refactor mayor no pedido.

### Opción B (elegida): Bajo demanda con umbral explícito
- **Pro:** el costo del refactor se paga cuando ya hay contexto de negocio para tocar ese servicio; el umbral evita que quede en criterio implícito.
- **Contra:** los servicios ya grandes pueden tardar en refactorizarse si nadie los toca — aceptable porque "no tocado" también significa "no está generando dolor activo".

## Consecuencias

### Positivas
- CI se destraba sin bloquear en un refactor mayor.
- El próximo PR que toque `PaymentsService`/`MembersService` tiene un criterio objetivo para decidir si split o no, no una discusión ad-hoc.
- `CommissionTiersService` y servicios similares (pocas deps, responsabilidad única) quedan explícitamente fuera de esta preocupación.

### Negativas / a mitigar
- Requiere que quien toque un servicio grande efectivamente aplique el checklist (paso 1-4) — no hay enforcement automático (considerar a futuro un check de CI que cuente `private final` por archivo y falle sobre el umbral solo en archivos modificados en el diff).

## Cómo aplicar

- Al editar o crear un servicio: contar dependencias de constructor. Si supera ~12 o el archivo supera ~600 líneas, aplicar el checklist de la sección "Decisión" antes de mergear.
- Al auditar duplicación: buscar bloques de validación/lógica repetidos entre métodos del mismo servicio — extracción de responsabilidad, no solo extracción de método privado.
- Servicios candidatos ya identificados (para cuando se toquen): `PaymentsService` (5 clusters: registro IN, registro/gestión OUT, review workflow, soporte de archivo, notificaciones), `MembersService` (pendiente de auditar en detalle cuando se toque).
