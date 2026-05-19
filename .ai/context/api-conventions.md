# Convenciones de API REST (backend)

> Resumen aplicado al backend. Detalles cross-stack en [hub `06-integration.md`](../../../centro-optico-vicente/.ai/specs/06-integration.md).

## URL structure

- Base: `/v1/`
- Grupos: `/v1/public/*`, `/v1/auth/*`, `/v1/me/*`, `/v1/admin/*`, `/v1/ally/*`, `/v1/promoter/*`
- URLs en `kebab-case` plural: `/v1/admin/allies/{id}/agreements`
- Path params son IDs (UUIDs): `/{member_id}`, `/{ally_id}`

## JSON

- Keys en `snake_case`: `member_id`, `next_due_date`, `is_active`
- Fechas ISO 8601: `2026-05-18T14:30:00Z`
- Decimal sin notación científica: `5.00` (no `5E0`)
- Booleans literal: `true` / `false`
- UUIDs: `"550e8400-e29b-41d4-a716-446655440000"`

Configuración en `core.config.JacksonConfig`:
```java
PropertyNamingStrategies.SNAKE_CASE
SerializationFeature.WRITE_DATES_AS_TIMESTAMPS = false
JsonInclude.Include.NON_NULL
```

## Headers

- `Authorization: Bearer <jwt>` para requests autenticadas
- `Content-Type: application/json` (o `multipart/form-data` para uploads)
- `Accept-Language: es-VE` (i18n, opcional)
- `X-Idempotency-Key: <uuid>` (opcional, para POST que pueden reintentarse)

## Status codes

| Código | Cuándo |
|---|---|
| 200 | GET, PUT, PATCH, DELETE exitoso |
| 201 | POST exitoso con recurso creado (incluir `Location` header) |
| 204 | Éxito sin body (ej. logout) |
| 400 | Validación falla |
| 401 | Sin auth o JWT inválido |
| 403 | Auth ok pero sin permiso |
| 404 | Recurso no existe |
| 409 | Conflicto (ej. duplicado, estado inválido) |
| 422 | Regla negocio violada |
| 429 | Rate limit |
| 500 | Bug (NUNCA exponer stack) |

## Error body (RFC 7807)

```json
{
  "type": "https://api.dominio.com/errors/validation",
  "title": "Validation failed",
  "status": 400,
  "detail": "Document number has invalid format",
  "instance": "/v1/admin/members",
  "errors": [
    { "field": "document_number", "message": "Must match V-{8 digits} or E-{8 digits}" }
  ],
  "trace_id": "abc123"
}
```

Implementado en `core.exception.GlobalExceptionHandler`.

## Paginación

Listados:
```
GET /v1/admin/members?page=0&size=20&sort=createdAt,desc
```

Response:
```json
{
  "content": [...],
  "page": 0,
  "size": 20,
  "total_elements": 1234,
  "total_pages": 62,
  "has_next": true,
  "has_previous": false
}
```

- `page`: 0-indexed
- `size`: default 20, max 100 (validar en controller)
- `sort`: campo,direccion. Default sort por endpoint.

## Filtros (RSQL)

```
GET /v1/admin/members?filter=status==ACTIVE;city.name==Maturín;age=ge=18
```

Operadores: `==`, `!=`, `=in=`, `=out=`, `=ge=`, `=gt=`, `=le=`, `=lt=`, `=like=`. AND `;`, OR `,`.

Implementación: usar `@RsqlSpecification` o `RSQLJPASupport` del starter `rsql-jpa-spring-boot-starter`.

Whitelist obligatoria de campos filterable por endpoint (no exponer todo).

## Validación de input

DTOs anotadas con Jakarta Bean Validation:

```java
public record MemberCreateDTO(
    @NotBlank @Pattern(regexp = "^[VE]-\\d{8}$") String documentNumber,
    @NotBlank @Size(max = 200) String fullName,
    @Email String email,
    @Past LocalDate birthDate,
    @NotNull UUID planId
) {}
```

Validador custom para cédula venezolana: `@VenezuelanID`.

## Idempotency

Para POST que pueden reintentarse (pagos, etc.):

```java
@PostMapping("/payments")
public PaymentDTO create(
    @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
    @RequestBody PaymentCreateDTO dto
) {
    if (idempotencyKey != null && idempotencyService.exists(idempotencyKey)) {
        return idempotencyService.getResult(idempotencyKey);
    }
    // crear + guardar resultado vinculado al key
}
```

Storage: Redis con TTL 24h.

## Auth

Ver [`../specs/04-security.md`](../specs/04-security.md).

## CORS

Configurado en `core.config.CorsConfig`:
- `https://centro-optico-vicente.com`, `https://app.dominio.com`
- `http://localhost:3000`, `http://localhost:3001` (dev)
- Métodos: GET, POST, PUT, PATCH, DELETE, OPTIONS
- Headers permitidos: Authorization, Content-Type, X-Requested-With, X-Idempotency-Key
- `allowCredentials = true`

## Swagger

`/swagger-ui/index.html` accesible en dev.
En prod: protegido con basic auth + IP allowlist (config Traefik).

Anotaciones en controllers:
```java
@Operation(summary = "Lista afiliados con filtros RSQL", description = "...")
@ApiResponses({
    @ApiResponse(responseCode = "200", description = "OK"),
    @ApiResponse(responseCode = "401", description = "No autenticado"),
    @ApiResponse(responseCode = "403", description = "Sin permiso")
})
```

## Rate limiting

| Endpoint | Límite |
|---|---|
| `/v1/auth/*` pre-auth | 10/min por IP (Traefik) |
| `/v1/public/*` | 100/min por IP |
| `/v1/me/*` | 1000/hour por user |
| `/v1/admin/*` | 5000/hour por user |
| `/v1/ally/validate/*` | configurable por aliado (default 1000/día) |

Implementación: Redis counters con TTL.
