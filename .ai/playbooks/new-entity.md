# Playbook — Nueva entidad backend

> Paso a paso para crear una entidad de dominio completa: schema + entity + repo + service + DTO + mapper + controller + tests.

## Pre-requisitos

- [ ] Confirmar que la entidad pertenece al modelo definido en [hub `05-domain-model.md`](../../../centro-optico-vicente/.ai/specs/05-domain-model.md). Si no está, agregarlo al modelo primero.
- [ ] Identificar el módulo donde va (`modules/{nombre}`). Si es módulo nuevo, crear estructura `entity/repository/service/controller/dto/mapper/`.
- [ ] Confirmar convenciones cross-stack en [ADR 0006 table conventions](../../../centro-optico-vicente/.ai/decisions/0006-table-conventions.md).

## Paso 1 — Migración Flyway

Ver [`new-migration.md`](new-migration.md).

```sql
-- V{N}__{module}_{table}.sql
CREATE TABLE example_things (
    example_thing_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- specific columns
    name VARCHAR(200) NOT NULL,
    code CITEXT NOT NULL UNIQUE,
    description TEXT,
    -- audit + status (obligatorios por ADR 0006)
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by UUID NULL REFERENCES users(user_id),
    updated_by UUID NULL REFERENCES users(user_id),
    -- constraints
    CONSTRAINT ck_example_things_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_example_things_code ON example_things(code);
CREATE INDEX idx_example_things_status ON example_things(status) WHERE is_active = TRUE;

-- Trigger para updated_at (función set_updated_at() ya existe desde V2)
CREATE TRIGGER trg_example_things_before_update_set_updated_at
    BEFORE UPDATE ON example_things
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
```

## Paso 2 — Entity JPA

`modules/example/entity/ExampleThing.java`:

```java
@Entity
@Table(name = "example_things")
@Getter
@Setter
@NoArgsConstructor
public class ExampleThing extends BaseEntity {

    @Id
    @Column(name = "example_thing_id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "code", nullable = false, columnDefinition = "citext")
    private String code;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    // BaseEntity provee: isActive, status, createdAt, updatedAt, createdBy, updatedBy
}
```

**Notas:**
- Usar Lombok (`@Getter`, `@Setter`, etc.) o records si aplica
- `FetchType.LAZY` default para relaciones (no especificar a menos que cambies a EAGER por buena razón)
- Para enums: `@Enumerated(EnumType.STRING)` siempre (nunca ORDINAL)

## Paso 3 — Repository

`modules/example/repository/ExampleThingRepository.java`:

```java
public interface ExampleThingRepository
        extends JpaRepository<ExampleThing, UUID>,
                JpaSpecificationExecutor<ExampleThing> {

    Optional<ExampleThing> findByCodeAndIsActiveTrue(String code);

    boolean existsByCode(String code);

    // Métodos custom con @Query si hace falta:
    // @Query("SELECT e FROM ExampleThing e WHERE ...")
}
```

## Paso 4 — DTOs

`modules/example/dto/`:

```java
// Para crear
public record ExampleThingCreateDTO(
    @NotBlank @Size(max = 200) String name,
    @NotBlank @Pattern(regexp = "^[A-Z0-9_]{3,50}$") String code,
    @Size(max = 2000) String description
) {}

// Para actualizar
public record ExampleThingUpdateDTO(
    @Size(max = 200) String name,
    @Size(max = 2000) String description,
    String status
) {}

// Para listar
public record ExampleThingListItemDTO(
    UUID id,
    String name,
    String code,
    String status,
    Instant createdAt
) {}

// Para detalle
public record ExampleThingDetailDTO(
    UUID id,
    String name,
    String code,
    String description,
    String status,
    Boolean isActive,
    Instant createdAt,
    Instant updatedAt,
    UUID createdBy,
    UUID updatedBy
) {}
```

## Paso 5 — Mapper (MapStruct)

`modules/example/mapper/ExampleThingMapper.java`:

```java
@Mapper(componentModel = "spring")
public interface ExampleThingMapper {

    ExampleThing toEntity(ExampleThingCreateDTO dto);

    ExampleThingDetailDTO toDetail(ExampleThing entity);

    ExampleThingListItemDTO toListItem(ExampleThing entity);

    void updateEntity(ExampleThingUpdateDTO dto, @MappingTarget ExampleThing entity);
}
```

## Paso 6 — Service

`modules/example/service/ExampleThingService.java`:

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExampleThingService {

    private final ExampleThingRepository repository;
    private final ExampleThingMapper mapper;

    public Page<ExampleThingListItemDTO> findAll(Specification<ExampleThing> spec, Pageable pageable) {
        return repository.findAll(spec, pageable).map(mapper::toListItem);
    }

    public ExampleThingDetailDTO findById(UUID id) {
        ExampleThing entity = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ExampleThing", id));
        return mapper.toDetail(entity);
    }

    @Transactional
    public ExampleThingDetailDTO create(ExampleThingCreateDTO dto) {
        if (repository.existsByCode(dto.code())) {
            throw new BusinessRuleException("Code already exists: " + dto.code());
        }
        ExampleThing entity = mapper.toEntity(dto);
        entity.setStatus("ACTIVE");
        ExampleThing saved = repository.save(entity);
        return mapper.toDetail(saved);
    }

    @Transactional
    public ExampleThingDetailDTO update(UUID id, ExampleThingUpdateDTO dto) {
        ExampleThing entity = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ExampleThing", id));
        mapper.updateEntity(dto, entity);
        return mapper.toDetail(entity);
    }

    @Transactional
    public void softDelete(UUID id) {
        ExampleThing entity = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ExampleThing", id));
        entity.setIsActive(false);
        // Nunca repository.delete()
    }
}
```

## Paso 7 — Controller

`modules/example/controller/ExampleThingController.java`:

```java
@RestController
@RequestMapping("/v1/admin/example-things")
@RequiredArgsConstructor
@Tag(name = "ExampleThings")
@PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
public class ExampleThingController {

    private final ExampleThingService service;

    @GetMapping
    @Operation(summary = "List example things with RSQL filters")
    public Page<ExampleThingListItemDTO> findAll(
        @RequestParam(required = false) String filter,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        Specification<ExampleThing> spec = RSQLJPASupport.toSpecification(filter);
        return service.findAll(spec, pageable);
    }

    @GetMapping("/{id}")
    public ExampleThingDetailDTO findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    // Obligatorio (ADR 0013) — ver spec 06-rest-api.md § "Endpoint /options para selects"
    @GetMapping("/options")
    public List<OptionDto> options(
        @RequestParam(required = false) String q,
        @RequestParam(required = false, defaultValue = "50") int limit,
        @RequestParam(required = false) List<UUID> currentValues
    ) {
        return service.listOptions(q, limit, currentValues);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('EXAMPLE_THING_CREATE')")
    public ExampleThingDetailDTO create(@Valid @RequestBody ExampleThingCreateDTO dto) {
        return service.create(dto);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('EXAMPLE_THING_UPDATE')")
    public ExampleThingDetailDTO update(
        @PathVariable UUID id,
        @Valid @RequestBody ExampleThingUpdateDTO dto
    ) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('EXAMPLE_THING_DELETE')")
    public void delete(@PathVariable UUID id) {
        service.softDelete(id);
    }
}
```

## Paso 8 — Tests

`src/test/java/.../modules/example/`:

### Repository test (con `@DataJpaTest`)

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ExampleThingRepositoryTest {
    @Autowired ExampleThingRepository repo;

    @Test
    void findByCodeAndIsActiveTrue_excludesInactive() {
        ExampleThing inactive = ...; // setIsActive(false)
        repo.save(inactive);
        assertThat(repo.findByCodeAndIsActiveTrue("CODE")).isEmpty();
    }
}
```

### Service test (mock repository)

```java
@ExtendWith(MockitoExtension.class)
class ExampleThingServiceTest {
    @Mock ExampleThingRepository repo;
    @Mock ExampleThingMapper mapper;
    @InjectMocks ExampleThingService service;

    @Test
    void create_throwsIfCodeExists() {
        when(repo.existsByCode("CODE")).thenReturn(true);
        var dto = new ExampleThingCreateDTO("Name", "CODE", null);
        assertThatThrownBy(() -> service.create(dto))
            .isInstanceOf(BusinessRuleException.class);
    }
}
```

### Test de `/options` con `currentValues` inactivo

```java
@Test @WithMockUser(authorities = "EXAMPLE_THING_VIEW_ALL")
void options_includesInactiveCurrentValue() throws Exception {
    ExampleThing inactive = ...; // setActive(false), guardado fuera del filtro/límite por defecto
    mvc.perform(get("/v1/admin/example-things/options")
            .param("currentValues", inactive.getUuid().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.uuid == '" + inactive.getUuid() + "')].active").value(false));
}
```

### Controller integration test (con `@SpringBootTest` + Testcontainers)

```java
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ExampleThingControllerIT {
    @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:15");

    @Test @WithMockUser(authorities = "EXAMPLE_THING_CREATE")
    void create_returns201() throws Exception {
        mvc.perform(post("/v1/admin/example-things")
                .contentType(APPLICATION_JSON)
                .content("""
                    {"name": "Test", "code": "TEST", "description": null}
                """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists());
    }
}
```

## Paso 9 — Seguridad

Si la entidad requiere permisos nuevos:
1. Agregar permisos al seed (`V6__seed_roles.sql` solo si V6 aún no se aplicó en ningún entorno; si ya está aplicada, crear una migration nueva `V{N}__add_permission_EXAMPLE.sql`).
2. Asignar a roles relevantes.
3. Documentar en [`../specs/05-roles-permissions.md`](../specs/05-roles-permissions.md).

## Paso 10 — Documentación

- [ ] Actualizar [`../context/current-state.md`](../context/current-state.md): "Entidad ExampleThing creada con CRUD completo".
- [ ] Marcar tarea en [`../checklist.md`](../checklist.md) con fecha.
- [ ] Si introduce nuevo endpoint público, actualizar [hub `06-integration.md`](../../../centro-optico-vicente/.ai/specs/06-integration.md).
- [ ] Si introduce nuevo término del dominio, actualizar [hub `domain-glossary.md`](../../../centro-optico-vicente/.ai/context/domain-glossary.md).

## Paso 11 — Commit

```bash
git add .
git commit -m "feat(example): add ExampleThing entity with CRUD endpoints

- Migration V{N}__example_things.sql
- Entity + Repository + Service + DTOs + Mapper
- Controller /v1/admin/example-things with RSQL filters
- Unit tests + integration test

Refs: .ai/checklist.md tarea X.Y"
```

## Checklist final

- [ ] Migración Flyway aplicada y validada
- [ ] Entidad JPA con `@MappedSuperclass BaseEntity`
- [ ] Repository extiende `JpaRepository + JpaSpecificationExecutor`
- [ ] DTOs con validación
- [ ] Mapper MapStruct
- [ ] Service con `@Transactional` correcto (`readOnly = true` default)
- [ ] Controller con `@PreAuthorize` + Swagger annotations
- [ ] Endpoint `/options` implementado (ADR 0013)
- [ ] Búsqueda libre `?q=` implementada (ADR 0013)
- [ ] Tests unit + integration
- [ ] Sin `repository.delete()` (siempre soft delete)
- [ ] Naming inglés en código
- [ ] Permisos agregados al sistema si necesario
- [ ] Documentación actualizada
