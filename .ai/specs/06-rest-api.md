# 06 — Convenciones REST API (detalle backend)

> Resumen aplicado en backend. Convenciones cross-stack en [hub `06-integration.md`](../../../centro-optico-vicente/.ai/specs/06-integration.md). Detalle base en [`../context/api-conventions.md`](../context/api-conventions.md).

## Estructura de controllers

Cada módulo de negocio tiene 1-N controllers en `modules/{nombre}/controller/`:

```
modules/members/controller/
├── MemberController.java          # /v1/admin/members/*
├── MyMemberController.java        # /v1/me/member
└── PublicMemberController.java    # (si aplica) /v1/public/members
```

## Anotaciones obligatorias

```java
@RestController
@RequestMapping("/v1/admin/members")
@RequiredArgsConstructor
@Tag(name = "Members", description = "Gestión de afiliados")
public class MemberController {

    private final MemberService service;

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MEMBER_VIEW_ALL')")
    @Operation(summary = "Get member by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK"),
        @ApiResponse(responseCode = "404", description = "Member not found"),
        @ApiResponse(responseCode = "401", description = "Not authenticated"),
        @ApiResponse(responseCode = "403", description = "Not authorized")
    })
    public MemberDetailDTO findById(@PathVariable UUID id) {
        return service.findById(id);
    }
}
```

## Paginación

> **Regla obligatoria.** Todo endpoint de listado (`GET /<recurso>`) **debe** paginar por default y devolver `Page<DTO>`. No se aceptan endpoints que devuelvan `List<DTO>` para colecciones sin acotar — un dataset chico hoy se vuelve grande mañana y romper el contrato es caro. Se aplica a TODO nuevo service/controller que se agregue al API.

### Convención de query params

| Param | Significado | Default |
|---|---|---|
| `page` | Número de página (0-indexed) | `0` |
| `size` | Tamaño de página | `50` para catálogos, `20` para el resto |
| `sort` | Campo + dirección (`name,asc`) | varía por recurso (ver `@PageableDefault`) |
| `size=-1` **o** `unpaged=true` | Devuelve TODOS los resultados sin paginar | — |
| `filter` | Expresión RSQL contra whitelist de campos (ver sección RSQL) | — |
| `q` | Búsqueda libre case-insensitive + `unaccent` en `name` / `code` / `description` (los que existan en la entidad). **Obligatorio salvo que la entidad no tenga ningún campo de texto buscable** (ver [ADR 0013](../decisions/0013-options-endpoint-conventions.md)) | — |

`size=-1` y `unpaged=true` son equivalentes y los maneja un `PageableHandlerMethodArgumentResolver` global (en `WebConfig`) que los mapea a `Pageable.unpaged()`. Útil para selects/dropdowns. Nota: si está habilitado el `@Cacheable`, solo se cachea el path **unpaged + sin filter + sin q** (el resto del espacio de combinaciones tiene cardinalidad muy alta).

### Ejemplo de controller

```java
@GetMapping("/members")
@PreAuthorize("hasAuthority('MEMBER_VIEW_ALL')")
public ResponseEntity<Page<MemberDto>> list(
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
        @RequestParam(required = false) String filter,
        @RequestParam(required = false) String q) {
    if (pageable.isPaged() && pageable.getPageSize() > 200) {
        throw new IllegalArgumentException("page.size.exceeded");
    }
    return ResponseEntity.ok(memberService.list(pageable, filter, q));
}
```

### Response shape

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

(Spring Data devuelve `Page` con propiedades en camelCase por default; Jackson convierte a snake_case por config).

## Endpoint `/options` para selects

> **Regla obligatoria** (ver [ADR 0013](../decisions/0013-options-endpoint-conventions.md)). Todo endpoint de listado paginado tiene un hermano `GET /<recurso>/options` que devuelve `List<OptionDto>` **sin paginar** — sin `Page`, sin `total_elements`/`total_pages` — pensado para poblar selects/dropdowns/typeaheads sin pagar el costo del DTO completo.

```java
public record OptionDto(UUID uuid, String code, String label, boolean active) {}
```

`code` es `null` cuando la entidad no tiene un código propio (ej. `User`, `Member`). `active` refleja el estado real del registro — permite al frontend renderizar atenuado/"(inactivo)" un valor ya asignado que ya no está activo, sin ocultarlo.

### Query params

| Param | Default | Semántica |
|---|---|---|
| `q` | — | Mismo `SEARCHABLE_FIELDS` que el listado paginado. |
| `limit` | `50` | Tope duro `200`. |
| `currentValues` | — | Lista de uuids (`?currentValues=<uuid1>,<uuid2>`) que **siempre** aparecen en la respuesta, con su `active` real, sin importar `q`/`limit`/estado. |

`active=true` es implícito salvo que el uuid venga en `currentValues`.

### Helper reusable — `core/util/OptionsSupport`

```java
public final class OptionsSupport {
    public static <T, ID> List<OptionDto> build(
            JpaSpecificationExecutor<T> specExecutor,
            Function<UUID, Optional<T>> findByUuid,   // reusa el findByUuid ya existente en cada repo
            Specification<T> baseSpec,                 // filtros propios de la entidad (activeOnly + q + parentFilter)
            List<UUID> currentValues,
            int limit,
            Function<T, UUID> uuidOf,
            Function<T, String> codeOf,
            Function<T, String> labelOf,
            Function<T, Boolean> activeOf) { ... }
}
```

### Ejemplo de controller

```java
@GetMapping("/options")
@PreAuthorize("hasAuthority('PLAN_VIEW_ALL')")
public ResponseEntity<List<OptionDto>> options(
        @RequestParam(required = false) String q,
        @RequestParam(required = false, defaultValue = "50") int limit,
        @RequestParam(required = false) List<UUID> currentValues) {
    return ResponseEntity.ok(plansService.listOptions(q, limit, currentValues));
}
```

## RSQL

Operadores soportados: `==`, `!=`, `=in=`, `=out=`, `=ge=`, `=gt=`, `=le=`, `=lt=`, `=like=`. AND `;`, OR `,`.

**Whitelist obligatoria por endpoint:**

```java
@Service
public class MemberService {
    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of(
        "documentNumber", "fullName", "status", "city.name", "createdAt"
    );

    public Page<MemberListItemDTO> findAll(Specification<Member> spec, Pageable pageable) {
        // Validar campos usados antes de aplicar (lo hace el starter RSQL si se configura)
        // O usar un visitor custom
        return repository.findAll(spec, pageable).map(mapper::toListItem);
    }
}
```

## Idempotency

POST que pueden reintentarse:

```java
@PostMapping
public PaymentDTO create(
    @RequestHeader(value = "X-Idempotency-Key", required = false) String key,
    @Valid @RequestBody PaymentCreateDTO dto
) {
    return idempotencyService.executeIfNew(key, () -> service.create(dto));
}
```

`IdempotencyService` usa Redis con TTL 24h.

## Upload multipart

```java
@PostMapping(value = "/{id}/upload-document", consumes = MULTIPART_FORM_DATA_VALUE)
@PreAuthorize("hasAuthority('MEMBER_UPLOAD_DOCUMENT')")
public DocumentDTO upload(
    @PathVariable UUID id,
    @RequestParam("file") @ValidatedFile(maxSizeMB = 5, allowedMimes = {"image/jpeg", "image/png", "application/pdf"}) MultipartFile file,
    @RequestParam("document_type") String documentType
) {
    return service.uploadDocument(id, file, documentType);
}
```

## Download via presigned URL

```java
@GetMapping("/{id}/document-url")
public Map<String, Object> getDocumentUrl(@PathVariable UUID id) {
    String key = service.getDocumentKey(id);
    URL url = storageService.generatePresignedUrl(key, Duration.ofMinutes(5));
    return Map.of(
        "url", url.toString(),
        "expires_in", 300
    );
}
```

## Errores RFC 7807

`GlobalExceptionHandler` maneja excepciones del proyecto y devuelve `application/problem+json`:

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ProblemDetail handleNotFound(ResourceNotFoundException e) {
        return ProblemDetail.builder()
            .type(URI.create("https://api.dominio.com/errors/not-found"))
            .title("Resource not found")
            .status(404)
            .detail(e.getMessage())
            .build();
    }

    @ExceptionHandler(BusinessRuleException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ProblemDetail handleBusinessRule(BusinessRuleException e) { /* ... */ }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail handleValidation(MethodArgumentNotValidException e) {
        // construir lista de errores por campo
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ProblemDetail handleAccessDenied(AccessDeniedException e) { /* ... */ }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ProblemDetail handleUnknown(Exception e) {
        log.error("Unexpected error", e);  // log full
        return ProblemDetail.builder()  // NO exponer stack al cliente
            .type(URI.create("https://api.dominio.com/errors/internal"))
            .title("Internal server error")
            .status(500)
            .detail("Unexpected error occurred")
            .build();
    }
}
```

## Swagger / OpenAPI

`core/config/OpenApiConfig.java`:

```java
@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("OptiBienestar 360 API")
                .version("1.0.0")
                .contact(new Contact().email("dev@solopsoftware.com"))
            )
            .components(new Components()
                .addSecuritySchemes("bearerAuth", new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                )
            )
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
```

## Validación de input

Bean Validation en DTOs:

```java
public record MemberCreateDTO(
    @NotBlank @VenezuelanID String documentNumber,
    @NotBlank @Size(max = 200) String fullName,
    @Email @NotBlank String email,
    @Past LocalDate birthDate,
    @NotNull UUID planId,
    @Size(max = 3) List<@Valid BeneficiaryCreateDTO> beneficiaries
) {}
```

Validador custom `@VenezuelanID`:

```java
@Target({FIELD, PARAMETER})
@Retention(RUNTIME)
@Constraint(validatedBy = VenezuelanIDValidator.class)
public @interface VenezuelanID {
    String message() default "Invalid Venezuelan ID format (must be V-12345678 or E-12345678)";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

public class VenezuelanIDValidator implements ConstraintValidator<VenezuelanID, String> {
    private static final Pattern P = Pattern.compile("^[VE]-\\d{6,9}$");
    public boolean isValid(String value, ConstraintValidatorContext ctx) {
        if (value == null) return true;  // @NotBlank lo verifica
        return P.matcher(value).matches();
    }
}
```

## CORS

Configurado en [04-security.md](04-security.md). El controller no necesita anotaciones adicionales.

## Referencias

- [hub `06-integration.md`](../../../centro-optico-vicente/.ai/specs/06-integration.md)
- [`../context/api-conventions.md`](../context/api-conventions.md)
- [04-security.md](04-security.md)
- [`../playbooks/new-endpoint.md`](../playbooks/new-endpoint.md)
