# ADR LOC-0015 (backend) — Contrato OpenAPI publicado por release

**Estado:** Propuesto
**Fecha:** 2026-10-25
**Decisores:** equipo OptiBienestar 360 (pendiente de aceptación del dueño)
**Origen:** [ADR LOC-0011 del frontend](https://github.com/centro-optico-vicente/optibienestar-360-frontend/blob/docs/adr-openapi-generated-types/.ai/decisions/loc-0011-openapi-generated-types.md) (Fase 1, lado backend; aceptado por el dueño el 2026-10-25, condicionado a la Fase 0)
**Relacionado:** [hub ADR 0014 `_Display`](https://github.com/centro-optico-vicente/centro-optico-vicente/blob/main/.ai/decisions/0014-display-value-convention.md) · [hub ADR 0019 camelCase](https://github.com/centro-optico-vicente/centro-optico-vicente/blob/main/.ai/decisions/0019-api-json-casing-contract.md) · [hub ADR 0025 prefijo `loc-`](https://github.com/centro-optico-vicente/centro-optico-vicente/blob/main/.ai/decisions/0025-local-adr-naming-convention.md)

## Contexto

El frontend quiere generar sus tipos TypeScript desde el OpenAPI del backend (ADR LOC-0011 del frontend). Decisiones ya tomadas por el dueño: (1) `/v3/api-docs` y Swagger UI se restringen en producción; (2) el CI del backend publica un `openapi.json` versionado como asset de cada release; (3) el trabajo de `_Display` y `required` vive en este repo, con ADR y PRs propios; (4) el frontend no commitea nada generado.

Este ADR fija cómo se genera el contrato, cómo se modelan `_Display` y `required`, el interruptor por variable de entorno y la convención del asset. Todo lo que aparece en la tabla de hallazgos es **verificado** contra un `openapi.json` real generado por CI (Fase 0), no supuesto.

## Hallazgos de la Fase 0 (verificados)

Fuente: run de CI [40262115722](https://github.com/centro-optico-vicente/optibienestar-360-backend/actions/runs/40262115722) de la rama `feature/openapi-contract-ci-artifact` (artifact `openapi-contract`: `openapi.json` + `wire-samples.json`, este último serializado por el mapper real de las respuestas).

| Pregunta | Resultado verificado |
|---|---|
| Versión OpenAPI emitida | **3.1.0** (default de springdoc 3.0.3). `info.version` = `1.0.0` manual (`OpenApiConfig`), `servers` = `http://localhost` genérico. |
| Tamaño | 901 228 bytes con pretty-print (~880 KiB); 413 paths, 381 schemas (184 `*Dto`, 150 `*Request`). |
| `AllyListItemDto` — `_Display` | **No fiel.** Spec: 14 propiedades, `city` como `$ref DisplayRef {uuid, code, name}`. Cable: 21 claves; `city` no existe, salen `city_Uuid`, `city_Display` y `city_Code`, más `published_Display`, `publishedAt_Display`, `active_Display`, `status_Display`, `createdAt_Display`. Faltan 8 claves en el spec y sobra `city`. |
| `MemberDetailDto` | Fiel en nombres: 43 propiedades en spec = 43 claves en cable (el record no usa `@Display`; anida `CityDto`, `GenderDto`…). |
| `PublicAllyDetailDto` | Fiel en nombres: 16 = 16 (sin `@Display`). |
| Alcance del problema `_Display` | 603 usos de `@Display` en 104 archivos; 96 propiedades del spec referencian `DisplayRef`; solo 4 schemas tienen propiedades `_Display` (los DTOs de auditoría que las declaran a mano). |
| `required` | Solo 114 de 381 schemas lo traen, casi todos `*Request` (Bean Validation: 112 de 150). Entre las respuestas solo `ContactRequestDto` e `Item`. Ninguno de los 3 DTOs tiene `required`, aunque el cable emite **todas** las claves (con `null` explícito) en los DTO sin `@JsonInclude`; hay 23 usos de `@JsonInclude(NON_NULL)` (21 a nivel de clase) y esos DTOs omiten las claves nulas. |
| Nulabilidad | El spec no expresa ninguna (0 `nullable` / 0 `type` en arreglo) aunque el cable emite `null` explícito. |
| `Page<X>` | Springdoc lo aplana por instanciación: 26 schemas `PageX` y 37 `AppliedSortPageX` (`content`, `pageable`, `totalElements`, `sort: SortObject`, …; `appliedSort` en el segundo). Usable por el generador; `Pageable`/`PageableObject`/`SortObject` salen como schemas auxiliares. |
| Enums | 188 propiedades con `enum`, solo de enums Java reales (p. ej. `SystemConfigDto.auditCreateMode`, `TermsVersionDto.termType` = `AFILIADO\|ASESOR\|ALIADO`). Los `status` tipados `String` con `@Display(ENUM)` salen como `string` sin unión, como se esperaba. |
| ¿Springdoc lee el mismo `ObjectMapper` que sirve las respuestas? | **No.** `ObjectMapperProvider` de springdoc 3.0.3 se construye sobre `Json31.mapper()` de swagger-core (Jackson 2, `com.fasterxml`) y no recibe ningún bean de Spring. Las respuestas HTTP las sirve el `JsonMapper` de Jackson 3 (`tools.jackson`) con `DisplayValueSerializerModifier`; el `ObjectMapper` Jackson 2 `@Primary` de `JacksonConfig` (con `DisplayBeanSerializerModifier`) no sirve respuestas. Confirmado empíricamente: el spec trae `city: DisplayRef` mientras el cable trae `city_Uuid`. |

Dos correcciones a lo supuesto en el ADR del frontend: el emisor real del cable es `DisplayValueSerializerModifier` (Jackson 3), no `DisplayBeanSerializerModifier`; y el cable emite una tercera clave `<rel>_Code` cuando la referencia trae código.

Comprobación adicional (artifact del run [40263022866](https://github.com/centro-optico-vicente/optibienestar-360-backend/actions/runs/40263022866)): `AppliedSortPage<AllyListItemDto>` serializado por el mapper real tiene las mismas 12 claves que el schema `AppliedSortPageAllyListItemDto` del spec.

**Veredicto Fase 0:** el spec generado es utilizable (3.1, tamaño razonable, `Page` y enums resueltos) pero **no es fiel al cable** en `_Display`, `required` ni nulabilidad. Hace falta el trabajo de la sección siguiente antes de que el frontend dependa de él.

## Decisión

### 1. Cómo se genera (sin base de datos, sin humano)

Una prueba de contexto Spring, `contract/OpenApiContractIT` (perfil `test`, H2, Flyway apagado): hace `GET /v3/api-docs` vía MockMvc y escribe `build/openapi/openapi.json` (y `wire-samples.json` para comparar con el cable). Corre dentro de `./gradlew build`, así que el CI existente ya la ejecuta y `ci.yaml` sube el resultado como artifact `openapi-contract`.

Por qué esta ruta y no el plugin Gradle de springdoc (`springdoc-openapi-gradle-plugin`): el plugin arranca la aplicación real (`bootRun`) y necesita base de datos, Redis y secretos válidos; la prueba reutiliza el contexto de test que ya funciona sin infraestructura y no agrega ningún plugin ni dependencia. Costo aceptado: el contrato sale del contexto de test, no del perfil `prod`; las rutas expuestas son las mismas (los controladores no dependen del perfil).

### 2. Interruptor de exposición (PR #349)

`SPRINGDOC_ENABLED` activa `/v3/api-docs` y Swagger UI: `true` por defecto en dev/test, `false` por defecto en `application-prod.properties`. El perfil `prod` también lo usa staging (el compose del hub mapea `staging` → `BACKEND_PROFILE=prod`), por eso staging fija `SPRINGDOC_ENABLED=true`. Desactivado: springdoc no registra endpoints y `SecurityConfig` deja de permitir las rutas (401 anónimo). Como el contrato viaja como asset del release, producción no necesita exponerlo.

### 3. `_Display` en el spec (`core/openapi/WireContractModelConverter`)

Un `ModelConverter` (bean, lo toma springdoc) post-procesa cada schema de record del paquete del proyecto y replica la regla del modificador de serialización (hub ADR 0014):

- Propiedad FK (`DisplayRef` o `@Display(fk=…)`): se reemplaza `<rel>` por `<rel>_Uuid` (string/uuid), `<rel>_Display` (string) y `<rel>_Code` (string, opcional: solo se emite si hay código).
- Escalar con `@Display`: se conserva `<campo>` y se agrega `<campo>_Display` (string) a continuación.
- Las claves `_Display` son de solo salida: no se agregan a schemas `*Request`/`*Command`.

La prueba de contrato compara, para los 3 DTOs de referencia, las claves del cable (serializadas con el mapper real) contra las propiedades del spec: si alguien cambia el modificador sin tocar el conversor, la prueba falla.

### 4. Regla de `required` y nulabilidad (mismo conversor)

Aplica globalmente a schemas de respuesta (todo schema de record cuyo nombre no termina en `Request` ni `Command`); no se anota con `@Schema` DTO por DTO:

1. Tipo primitivo o `@NotNull`/`@NotBlank`/`@NotEmpty` → `required`.
2. DTO con `@JsonInclude(NON_NULL | NON_EMPTY | NON_ABSENT | NON_DEFAULT)` (a nivel de clase o de componente): un componente de referencia sin `@NotNull` es opcional (el cable omite la clave), sin `nullable`.
3. DTO sin `@JsonInclude` (inclusión por defecto): el cable emite todas las claves, por tanto todo componente es `required`, y los de referencia sin `@NotNull` se marcan nulables: `type: ["string", "null"]`, o `anyOf: [{$ref}, {type: "null"}]` para objetos anidados (OpenAPI 3.1; en 3.0 se usa `nullable: true`).
4. Los schemas `*Request` conservan lo que ya aporta Bean Validation.

### 5. Convención del asset del release (PR #352)

`publish.yaml` (se dispara con `release: published`) adjunta el archivo al release con el nombre exacto **`openapi.json`** (`gh release upload --clobber`). URL: `https://github.com/centro-optico-vicente/optibienestar-360-backend/releases/download/<tag>/openapi.json`. El repo es público (sin token). La versión del contrato es el tag del release; `info.version` (`API_CONTRACT_VERSION`, manual) sigue siendo independiente.

## Resultado tras el conversor (verificado)

Fuente: run de CI [40263040348](https://github.com/centro-optico-vicente/optibienestar-360-backend/actions/runs/40263040348) (artifact `openapi-contract`, 1 010 064 bytes, 252 schemas con `required`, `openapi: 3.1.0`). `contract/OpenApiWireFidelityIT` compara el spec con lo que serializa el mapper real y pasa para los 3 DTOs de referencia y para un DTO `NON_NULL` (`CommissionDto`):

| DTO | Antes | Después |
|---|---|---|
| `AllyListItemDto` | 14 propiedades, `city: $ref DisplayRef`, sin `required` | 21 propiedades = 21 claves del cable (`city_Uuid`, `city_Display`, `city_Code` opcional, `*_Display`); 20 `required` (todas salvo `city_Code`); referencias nulables, primitivos y sus `_Display` no nulables |
| `MemberDetailDto` | 43 = 43, sin `required` | 43 = 43, los 43 `required`; objetos anidados como `anyOf [$ref, null]`; `int`/`boolean` no nulables |
| `PublicAllyDetailDto` | 16 = 16, sin `required` | 16 = 16, los 16 `required` y nulables |
| `CommissionDto` (`NON_NULL`) | sin `required`, FK anidados | FK aplanados; valores base no nulables; `required` solo lo que el servidor escribe siempre (primitivos y sus `_Display`). Los `_Uuid`/`_Display` siguen nulables a propósito (el formateador puede escribir una etiqueta `null` para un valor presente) |

Límites conocidos (no cubiertos por la regla): solo se procesan records (los pocos DTO que son clases quedan como antes); un `@JsonInclude` en una propiedad `Optional`/colección con semántica propia no se modela más allá de NON_*; `List<DisplayRef>` con `@Display` se describe como arreglo de `DisplayRef` más `<campo>_Display`, igual que el cable.

## Alternativas

| Opción | Veredicto |
|---|---|
| Plugin Gradle de springdoc | Descartada: levanta la app completa (BD/Redis/secretos); más lento y frágil en CI. |
| `@Schema` manual en ~287 records | Descartada: ruido y deriva; la regla global es verificable con una prueba. |
| Cambiar el cable para que coincida con el spec (objeto `city`) | Descartada: rompe el contrato `_Display` vigente (hub ADR 0014) y a los consumidores. |
| Servir el spec desde TEST y dejarlo público | Descartada: expone toda la superficie `/v1/admin/**` (ver sección 2). |

## Consecuencias

- Positivas: el contrato queda atado a un release; sin infraestructura extra; el spec se puede validar en cada PR (artifact + prueba).
- Negativas: el conversor duplica la regla del modificador `_Display` (mitigado por la prueba de contrato); el spec sigue conteniendo schemas auxiliares inertes (`DisplayRef`).
- Pendiente fuera de este repo: el compose/`env_template.env` del hub debe pasar `SPRINGDOC_ENABLED` (staging `true`); el frontend fija la versión en `api/contract-version`.
