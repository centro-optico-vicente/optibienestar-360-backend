# ADR 0003 (local backend) — Audit columns obligatorias + Spring Data JPA Auditing

**Estado:** Aceptado
**Fecha:** 2026-05-18

## Contexto

Datos médicos y financieros requieren trazabilidad completa: quién creó/modificó cada registro y cuándo. Implementarlo manualmente en cada service es propenso a olvidos.

## Decisión

**Toda entidad extiende `BaseEntity` que provee columnas de auditoría auto-populadas por Spring Data JPA Auditing.**

### Columnas obligatorias

| Columna | Tipo | Auto-populated |
|---|---|---|
| `created_at` | `TIMESTAMP WITH TIME ZONE` | `@CreatedDate` Spring |
| `updated_at` | `TIMESTAMP WITH TIME ZONE` | `@LastModifiedDate` Spring + trigger SQL backup |
| `created_by` | `UUID NULL` | `@CreatedBy` Spring (via `AuditorAware`) |
| `updated_by` | `UUID NULL` | `@LastModifiedBy` Spring |

### Implementación

```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private UUID updatedBy;
    // getters/setters
}
```

`@EnableJpaAuditing` en config + `AuditorAware<UUID>`:

```java
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class JpaAuditingConfig {
    @Bean
    AuditorAware<UUID> auditorProvider() {
        return () -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() instanceof String) {
                return Optional.empty();  // sistema (jobs scheduleados, seed inicial)
            }
            // Asumimos que el principal es nuestro UserDetails custom con userId
            CustomUserDetails details = (CustomUserDetails) auth.getPrincipal();
            return Optional.of(details.getUserId());
        };
    }
}
```

### Backup SQL trigger

En cada migración: trigger `set_updated_at()` (creado en V2). Backup en caso de que Hibernate falle por algún motivo:

```sql
CREATE TRIGGER trg_{table}_before_update_set_updated_at
    BEFORE UPDATE ON {table}
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
```

## Eventos críticos en `audit_log`

Adicional al audit en cada tabla, eventos críticos se registran en tabla específica:

- Login/logout
- Cambio de password
- Cambio de rol
- Aprobación/rechazo de pago
- Acceso a `MedicalRecord`
- Cambio status membresía
- Soft delete

Implementación: AOP aspect `@AuditAction` + service que inserta en `audit_log` table.

## Consecuencias

### Positivas
- Auditoría sin código boilerplate en cada service.
- `created_by`/`updated_by` siempre poblados si hay usuario en SecurityContext.
- Trigger SQL como red de seguridad para `updated_at`.

### Negativas / a mitigar
- Jobs scheduleados (`@Scheduled`) corren sin SecurityContext → `created_by`/`updated_by` son null. **Mitigar**: setear `SYSTEM_USER` constante en estos casos o crear usuario "system" en DB.
- Tests con `@DataJpaTest` no tienen SecurityContext → similar al anterior.

## Referencias

- [ADR 0006 cross-stack Table conventions](../../../centro-optico-vicente/.ai/decisions/0006-table-conventions.md)
- Spring Data JPA Auditing: https://docs.spring.io/spring-data/jpa/reference/auditing.html
