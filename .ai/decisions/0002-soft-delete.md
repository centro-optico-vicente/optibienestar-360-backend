# ADR 0002 (local backend) — Soft delete pattern obligatorio

**Estado:** Aceptado
**Fecha:** 2026-05-18

## Contexto

En un sistema con datos médicos sensibles y obligaciones legales de auditoría (afiliados, pagos, antecedentes), perder información por error es inaceptable. Adicionalmente, queries de reportes históricos requieren acceso a registros "desactivados".

## Decisión

**TODA entidad de negocio implementa soft delete via `is_active` BOOLEAN.**

- Nunca llamar `repository.delete(entity)` ni `DELETE FROM ...` en código de aplicación
- Para "borrar": `entity.setIsActive(false)` (audit columns `updated_at`/`updated_by` se actualizan automáticamente)
- Listings por default filtran `WHERE is_active = TRUE`
- Endpoints admin específicos pueden listar inactivos con flag explícito (`?include_inactive=true`)

## Excepciones

Datos NO sensibles donde aplicar hard delete es seguro:
- Tokens expirados en Redis (TTL automático)
- Logs antiguos cuando se cumple retención
- Audit log NUNCA se borra (retención 7+ años)
- Datos transitorios (idempotency keys con TTL)

## Implementación

`BaseEntity` (abstract) provee la columna:

```java
@MappedSuperclass
public abstract class BaseEntity {
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    // ...
}
```

Repository custom para hard delete (solo casos justificados):

```java
// Sólo para casos específicos NO en datos de negocio
@Modifying
@Query("DELETE FROM TempToken t WHERE t.expiresAt < :now")
int hardDeleteExpiredTokens(Instant now);
```

## Consecuencias

### Positivas
- Recuperación de datos borrados por error es trivial (`UPDATE ... SET is_active = TRUE`)
- Cumple obligaciones de retención legal de datos médicos
- Permite reportes históricos completos

### Negativas / a mitigar
- Tablas crecen siempre (no se reducen) — mitigar con índices parciales `WHERE is_active = TRUE` para queries activas
- Cuidar en queries: olvidar el filtro `is_active = TRUE` puede incluir basura
- Mitigar con: Spring Data JPA Specifications base que incluye filtro por default

## Referencias

- [ADR 0006 cross-stack Table conventions](../../../centro-optico-vicente/.ai/decisions/0006-table-conventions.md)
