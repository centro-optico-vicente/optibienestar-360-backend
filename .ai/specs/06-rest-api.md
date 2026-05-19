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

```java
@GetMapping
public Page<MemberListItemDTO> findAll(
    @RequestParam(required = false) String filter,
    @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
) {
    if (pageable.getPageSize() > 100) {
        throw new BusinessRuleException("Page size cannot exceed 100");
    }
    Specification<Member> spec = RSQLJPASupport.toSpecification(filter);
    return service.findAll(spec, pageable);
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
                .title("OptiSalud Plus API")
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
