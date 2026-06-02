# Playbook — Nuevo endpoint REST

> Para crear un endpoint nuevo siguiendo todas las convenciones del backend.

## Pre-requisitos

- [ ] Confirmar a qué grupo pertenece (`/v1/public/*`, `/v1/admin/*`, `/v1/me/*`, etc.)
- [ ] Confirmar el rol/permiso requerido
- [ ] Confirmar la entidad/dominio (si no existe, ver [`new-entity.md`](new-entity.md) primero)

## Paso 1 — Definir contract

Antes de codear:
- URL: `/v1/{group}/{resource}/{id?}/{sub-resource?}`
- Método: GET / POST / PUT / PATCH / DELETE
- Request body shape (DTO)
- Response body shape (DTO)
- Status codes esperados (200/201/204/400/401/403/404/...)
- Si requiere paginación, sort, filter (RSQL)

Documentar en [hub `06-integration.md`](../../../centro-optico-vicente/.ai/specs/06-integration.md) si es endpoint público o de uso cross-stack importante.

## Paso 2 — DTOs

Si no existen ya en el módulo, crearlos en `dto/`:

```java
// Request
public record FooCreateDTO(
    @NotBlank @Size(max = 100) String name,
    @Email String email
) {}

// Response
public record FooDetailDTO(
    UUID id,
    String name,
    String email,
    String status,
    Instant createdAt
) {}
```

Validación con Jakarta Bean Validation. Validators custom en `common/validator/`.

## Paso 3 — Service method

```java
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class FooService {

    private final FooRepository repository;
    private final FooMapper mapper;

    @Transactional
    public FooDetailDTO doSomething(UUID id, FooActionDTO dto) {
        Foo entity = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Foo", id));

        // Aplicar regla de negocio
        if (entity.getStatus().equals("BLOCKED")) {
            throw new BusinessRuleException("Cannot act on blocked Foo");
        }

        entity.setSomething(dto.something());
        return mapper.toDetail(entity);
    }
}
```

## Paso 4 — Controller method

```java
@RestController
@RequestMapping("/v1/admin/foos")
@RequiredArgsConstructor
@Tag(name = "Foos")
public class FooController {

    private final FooService service;

    @PostMapping("/{id}/action")
    @PreAuthorize("hasAuthority('FOO_ACTION')")
    @Operation(
        summary = "Perform action on Foo",
        description = "Detailed description of what this endpoint does."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Action performed successfully"),
        @ApiResponse(responseCode = "400", description = "Validation failed"),
        @ApiResponse(responseCode = "401", description = "Not authenticated"),
        @ApiResponse(responseCode = "403", description = "Not authorized"),
        @ApiResponse(responseCode = "404", description = "Foo not found"),
        @ApiResponse(responseCode = "422", description = "Business rule violation (e.g., Foo blocked)")
    })
    public FooDetailDTO doAction(
        @Parameter(description = "Foo ID") @PathVariable UUID id,
        @Valid @RequestBody FooActionDTO dto
    ) {
        return service.doSomething(id, dto);
    }
}
```

## Paso 5 — Endpoint listado con paginación + RSQL + búsqueda libre

> **Regla obligatoria:** todo endpoint de listado debe paginar (`Page<DTO>`). Ver convención completa de params (`page`/`size`/`sort`/`filter`/`q`, `size=-1` o `unpaged=true` para todo, defaults por tipo de recurso) en [`../specs/06-rest-api.md` → Paginación](../specs/06-rest-api.md).

```java
@GetMapping
@PreAuthorize("hasAnyAuthority('FOO_VIEW_ALL')")
@Operation(summary = "List foos with optional RSQL filter + free-text search")
public ResponseEntity<Page<FooListItemDTO>> findAll(
    @Parameter(hidden = true)
    @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
    @Parameter(description = "RSQL filter expression") @RequestParam(required = false) String filter,
    @Parameter(description = "Free-text search across name/code/description") @RequestParam(required = false) String q
) {
    if (pageable.isPaged() && pageable.getPageSize() > 200) {
        throw new IllegalArgumentException("page.size.exceeded");
    }
    return ResponseEntity.ok(service.list(pageable, filter, q));
}
```

**Whitelist de campos filterable** (en service):
```java
private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of(
    "name", "status", "createdAt"
);
// Validar antes de aplicar RSQL — patrón en UserService.validateFilterFields()
```

## Paso 6 — Endpoint upload (multipart)

```java
@PostMapping(value = "/{id}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
@PreAuthorize("hasAuthority('FOO_UPLOAD')")
@Operation(summary = "Upload file for Foo")
public FooDetailDTO upload(
    @PathVariable UUID id,
    @RequestParam("file") @ValidatedFile(maxSizeMB = 5, allowedMimes = {"image/jpeg", "image/png", "application/pdf"}) MultipartFile file
) {
    return service.uploadFile(id, file);
}
```

Validador custom `@ValidatedFile`:
- Verifica MIME real (no extensión)
- Verifica tamaño
- En `StorageService.upload`: sanitizar nombre, calcular hash, subir a R2

## Paso 7 — Endpoint con presigned URL (para download)

```java
@GetMapping("/{id}/file-url")
@PreAuthorize("@fooSecurity.canRead(#id, authentication)")
public Map<String, String> getFileUrl(@PathVariable UUID id) {
    String key = service.getFileKey(id);
    URL url = storageService.generatePresignedUrl(key, Duration.ofMinutes(5));
    return Map.of("url", url.toString(), "expires_in", "300");
}
```

## Paso 8 — Endpoint con cache Redis

```java
@GetMapping("/{id}")
@Cacheable(value = "foos", key = "#id", unless = "#result == null")
public FooDetailDTO findById(@PathVariable UUID id) {
    return service.findById(id);
}

@PutMapping("/{id}")
@CacheEvict(value = "foos", key = "#id")
public FooDetailDTO update(@PathVariable UUID id, @Valid @RequestBody FooUpdateDTO dto) {
    return service.update(id, dto);
}
```

## Paso 9 — Endpoint con idempotency

Para POST que puede reintentarse (pagos, etc.):

```java
@PostMapping
public PaymentDTO create(
    @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
    @Valid @RequestBody PaymentCreateDTO dto
) {
    if (idempotencyKey != null) {
        return idempotencyService.executeIfNew(idempotencyKey, () -> service.create(dto));
    }
    return service.create(dto);
}
```

## Paso 10 — Tests

```java
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class FooControllerIT {

    @Container
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:15");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", pg::getJdbcUrl);
        r.add("spring.datasource.username", pg::getUsername);
        r.add("spring.datasource.password", pg::getPassword);
    }

    @Autowired MockMvc mvc;

    @Test
    @WithMockUser(authorities = "FOO_ACTION")
    void doAction_returns200() throws Exception {
        mvc.perform(post("/v1/admin/foos/{id}/action", existingId)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"something": "value"}
                """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(existingId.toString()));
    }

    @Test
    @WithMockUser  // sin authority FOO_ACTION
    void doAction_returns403WhenNoPermission() throws Exception {
        mvc.perform(post("/v1/admin/foos/{id}/action", existingId)
                .contentType(APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden());
    }
}
```

## Paso 11 — Documentación

- [ ] Swagger genera doc automáticamente desde `@Operation`/`@ApiResponses`
- [ ] Actualizar [`../checklist.md`](../checklist.md): marcar `[x]` con fecha
- [ ] Si es endpoint público importante, agregar a [hub `06-integration.md`](../../../centro-optico-vicente/.ai/specs/06-integration.md)
- [ ] Si introduce permiso nuevo, agregar a [`../specs/05-roles-permissions.md`](../specs/05-roles-permissions.md)

## Paso 12 — Commit

```bash
git add .
git commit -m "feat(foos): add POST /v1/admin/foos/{id}/action endpoint

- Service method with business rule validation
- Controller with @PreAuthorize FOO_ACTION
- DTOs FooActionDTO + FooDetailDTO
- Integration test + permission test

Refs: .ai/checklist.md tarea X.Y"
```

## Checklist final

- [ ] URL en `kebab-case` plural
- [ ] JSON keys en `snake_case`
- [ ] DTOs con validación Bean Validation
- [ ] Service con `@Transactional` correcto
- [ ] Controller con `@PreAuthorize` específico (no solo `hasRole`)
- [ ] Swagger annotations: `@Operation`, `@ApiResponses`
- [ ] Paginación si es listado (default 20, max 100)
- [ ] RSQL si es listado con filtros
- [ ] Cache Redis si es lectura frecuente
- [ ] Idempotency si es POST reintentable
- [ ] Validación MIME + tamaño si es upload
- [ ] Tests integration: happy path + 401 + 403 + 404 + 400/422 según aplique
- [ ] Sin exponer entidades JPA directo (siempre DTOs)
- [ ] Errores estilo RFC 7807 (`GlobalExceptionHandler` se encarga si lanzás excepciones del proyecto)

## Anti-patterns a evitar

❌ Devolver entidades JPA directo: `@GetMapping public Foo find(...)` → siempre DTO
❌ `@PreAuthorize("isAuthenticated()")` cuando hace falta más granular
❌ Sin paginación en listados (puede traer 100K filas y romper memoria)
❌ Filtros RSQL sin whitelist (expone columnas sensibles)
❌ Logear PII (cédulas, emails) en logs estándar
❌ Mezclar responsabilidades: controller con lógica de negocio (debe ir en service)
❌ Olvidar tests de permisos (testear 401 y 403)
