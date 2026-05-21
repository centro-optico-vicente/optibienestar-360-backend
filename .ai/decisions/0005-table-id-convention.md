# ADR 0005 (local backend) — Convención de identificadores de tabla (BIGINT + UUID)

**Estado:** Aceptado  
**Fecha:** 2026-05-21  
**Referencia cross-stack:** ADR 0006 de `exchange-rates-api` (patrón origen)

## Contexto

Todas las tablas necesitan dos identificadores con roles distintos:

- **ID interno** (`BIGINT`): clave primaria para JOINs y foreign keys dentro de la base de datos. Entero secuencial — compacto, óptimo en índices B-tree.
- **ID externo** (`UUID`): identificador público expuesto en URLs y APIs REST. Opaco, no predecible, sin fuga de cardinalidad.

## Decisión

Toda tabla nueva debe seguir este patrón de columnas:

```sql
{table_name}_id  BIGINT  GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
uuid             UUID    NOT NULL UNIQUE DEFAULT gen_random_uuid(),
```

### Reglas derivadas

| Regla | Detalle |
|---|---|
| Nombre de PK | `{table_name}_id` (no genérico `id`) |
| Tipo de PK | `BIGINT GENERATED ALWAYS AS IDENTITY` |
| UUID | `NOT NULL UNIQUE DEFAULT gen_random_uuid()` — generado por BD |
| FKs entre tablas | Usan el `BIGINT` (`{table_name}_id`) |
| API REST | Siempre expone el `uuid`, nunca el `BIGINT` |
| JPA `@Id` | `Long id` con `@GeneratedValue(strategy = IDENTITY)` |
| `@AttributeOverride` | `@AttributeOverride(name = "id", column = @Column(name = "{table_name}_id"))` en cada entidad |
| `JpaRepository<E, Long>` | El tipo genérico del repo es siempre `Long` |

### BaseEntity

```java
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
@Column(nullable = false, updatable = false)
private Long id;

@Column(nullable = false, updatable = false, unique = true)
private UUID uuid;

@PrePersist
private void assignUuid() {
    if (uuid == null) uuid = UUID.randomUUID();
}
```

> El `uuid` en `BaseEntity` es fallback de JVM; en producción la BD lo genera vía `DEFAULT gen_random_uuid()`.

## Por qué

- Enteros secuenciales son más eficientes para índices y FKs en PostgreSQL.
- UUIDs en URLs evitan enumeración de recursos y simplifican la migración de datos entre entornos.
- El patrón es coherente con el resto del ecosistema (referencia cruzada al ADR 0006 de exchange-rates-api).

## Alternativas

- **Solo UUID como PK:** menos eficiente en índices, fragmentación de páginas.
- **Solo BIGINT expuesto en API:** predecible, enumerable — riesgo de seguridad.

## Consecuencias

- Toda entidad JPA que extienda `BaseEntity` debe incluir `@AttributeOverride` para renombrar la columna PK.
- Los repositorios siempre declaran `JpaRepository<E, Long>`.
- Los DTOs de respuesta siempre incluyen `uuid` y omiten `id`.
