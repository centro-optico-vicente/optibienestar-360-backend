# Playbook — Nueva entidad backend

> **Reescrito 2026-10-10** contra el código real (ver [auditoría 2026-09-23](../notes/2026-09-23_audit.md), recomendación #4) — la versión anterior enseñaba PK `UUID` simple, `JpaRepository<E, UUID>` y `hasAnyRole(...)`, los tres obsoletos. Patrón de referencia real usado para este rewrite: `modules/payment/entity/PaymentCategory.java` + su repositorio, servicio, controller y DTO, más `core/entity/BaseEntity.java` y la migración `V119__payment_catalogs_permissions_and_audit.sql`.

> Paso a paso para crear una entidad de dominio completa: schema + entity + repo + service + DTO + mapper + controller + tests.

## Pre-requisitos

- [ ] Confirmar que la entidad pertenece al modelo definido en [hub `05-domain-model.md`](../../../centro-optico-vicente/.ai/specs/05-domain-model.md). Si no está, agregarlo al modelo primero.
- [ ] Identificar el módulo donde va (`modules/{nombre}`). Si es módulo nuevo, crear estructura `entity/repository/service/dto/`.
- [ ] Confirmar convenciones cross-stack en [ADR 0006 table conventions](../../../centro-optico-vicente/.ai/decisions/0006-table-conventions.md) — identificador dual `BIGINT` interno + `uuid` externo, **no** UUID como PK.

## Paso 1 — Migración Flyway

Ver [`new-migration.md`](new-migration.md).

```sql
-- V{N}__{module}_{table}.sql (patrón real: V115__payment_categories_and_methods.sql)
CREATE TABLE example_things
(
    example_things_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid               UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    name               VARCHAR(200) NOT NULL,
    code               VARCHAR(40)  NOT NULL UNIQUE,
    description        VARCHAR(255),

    -- audit + soft-delete (obligatorios por ADR 0006)
    is_active          BOOLEAN      NOT NULL DEFAULT TRUE,
    status             VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE', -- omitir esta columna si la entidad no necesita workflow de estados (ver BaseAuditEntity abajo)
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by         UUID,
    updated_by         UUID,

    CONSTRAINT ck_example_things_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_example_things_code ON example_things (code);

CREATE TRIGGER trg_example_things_updated_at
    BEFORE UPDATE ON example_things
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
```

**Nota sobre la PK:** el nombre de la columna PK es el nombre de la tabla completo, en plural (`example_things_id`, no `example_thing_id`) — así lo hacen las 175+ migraciones reales. El `uuid` es una columna aparte, nunca la PK.

## Paso 2 — Entity JPA

`modules/example/entity/ExampleThing.java` (patrón real: `PaymentCategory.java`):

```java
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "example_things")
@AttributeOverride(name = "id", column = @Column(name = "example_things_id", nullable = false, updatable = false))
public class ExampleThing extends BaseEntity {

    @Column(length = 40, unique = true, nullable = false)
    private String code;

    @Column(length = 200, nullable = false)
    private String name;

    @Column(length = 255)
    private String description;
}
```

`BaseEntity` (`core/entity/BaseEntity.java`) ya provee: `id` (`Long`, `@Id @GeneratedValue(IDENTITY)`), `uuid`, `active` (mapea a `is_active`), `status` (default `"ACTIVE"`), `createdAt`/`updatedAt`/`createdBy`/`updatedBy` vía Spring Data JPA Auditing, y auto-asigna el `uuid` en `@PrePersist` como fallback de JVM (en producción lo genera la BD). Si la tabla **no** necesita la columna `status` (es un catálogo sin workflow de estados, solo activo/inactivo), extender `BaseAuditEntity` en vez de `BaseEntity` — es idéntica salvo que no tiene el campo `status` (ver `core/audit/entity/EntityConfig.java` como ejemplo real que no lo necesita).

**Notas:**
- `FetchType.LAZY` default para relaciones (no especificar a menos que cambies a EAGER por buena razón).
- Para enums: `@Enumerated(EnumType.STRING)` siempre (nunca ORDINAL), o preferir `String` + `CHECK` en BD si el set de valores puede crecer sin deploy (patrón real: `PaymentCategory.direction`).

## Paso 3 — Repository

`modules/example/repository/ExampleThingRepository.java` (patrón real: `PaymentCategoryRepository.java`):

```java
@Transactional(readOnly = true)
public interface ExampleThingRepository extends JpaRepository<ExampleThing, Long>, JpaSpecificationExecutor<ExampleThing> {

    Optional<ExampleThing> findByUuid(UUID uuid);

    /** Natural-key lookup — para servicios que resuelven un code hardcodeado a la entidad. */
    Optional<ExampleThing> findByCode(String code);

    List<ExampleThing> findAllByActiveTrueOrderByName();
}
```

**Nunca** `JpaRepository<ExampleThing, UUID>` — el tipo de PK para JPA es siempre `Long`. El `uuid` es solo un campo más de la entidad, resuelto vía `findByUuid`.

## Paso 4 — DTOs

`modules/example/dto/`:

```java
public record ExampleThingDto(
    UUID uuid,
    String code,
    String name,
    String description,
    @Display(Display.Kind.BOOLEAN) boolean active
) {}

public record ExampleThingCreateRequest(
    @NotBlank @Size(max = 40) String code,
    @NotBlank @Size(max = 200) String name,
    @Size(max = 255) String description
) {}

public record ExampleThingUpdateRequest(
    @NotBlank @Size(max = 200) String name,
    @Size(max = 255) String description,
    Boolean active
) {}
```

**Convención `_Display` (hub ADR 0014, implementada en `core/display/`):** cualquier campo FK o valor presentacional que el frontend deba mostrar formateado/traducido se anota `@Display`. Dos formas (ver Javadoc de `core/display/Display.java` para el detalle completo, no copiar sin leerlo):
- **FK:** el campo es un `DisplayRef` (o lleva `@Display(fk = "...")`) — en el JSON de salida se aplana a `<nombre>_Uuid` + `<nombre>_Display`.
- **Escalar presentacional** (fecha, monto, enum, boolean): el campo se queda con su tipo real y se agrega `<nombre>_Display` al lado, resuelto por `Locale` en `DisplayFormatter`.

Las keys JSON van en **camelCase** (no snake_case) — ver [ADR 0019 del hub](https://github.com/fenix-core/centro-optico-vicente/blob/main/.ai/decisions/0019-api-json-casing-contract.md). No hay MapStruct en el código real revisado para este playbook — el mapeo a DTO se hace con un método estático simple en el service (ver Paso 6); usar MapStruct si el módulo ya lo tiene, pero no es obligatorio.

## Paso 5 — Service

`modules/example/service/ExampleThingService.java` (patrón real: `PaymentCategoryService.java`):

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExampleThingService {

    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of("code", "name");
    private static final String[] SEARCHABLE_FIELDS = {"code", "name"};
    private static final Map<String, SortFieldValidator.SortableField> SORTABLE_FIELDS =
            SortFieldValidator.sortableFieldsOf(ExampleThing.class, Map.of());

    private final ExampleThingRepository repository;
    private final DefaultSortResolver defaultSortResolver;

    public Page<ExampleThingDto> list(Pageable pageable, String filter, String q, boolean includeInactive) {
        Pageable defaultedPageable = defaultSortResolver.withDefaultSortIfUnsorted("example_thing", pageable);
        Pageable resolvedPageable = SortFieldValidator.resolve(defaultedPageable, SORTABLE_FIELDS, "example_thing");
        Specification<ExampleThing> spec = includeInactive
                ? (root, query, cb) -> cb.conjunction()
                : (root, query, cb) -> cb.equal(root.get("active"), Boolean.TRUE);
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS, "example_thing.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        return repository.findAll(spec, resolvedPageable).map(ExampleThingService::toDto);
    }

    /** Lightweight options para selects/dropdowns — ver `OptionsSupport`. */
    public List<OptionDto> listOptions(String q, int limit, List<UUID> currentValues) {
        Specification<ExampleThing> spec = ((Specification<ExampleThing>) (root, query, cb) -> cb.isTrue(root.get("active")))
                .and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        return OptionsSupport.build(repository, repository::findByUuid, spec, currentValues, limit,
                ExampleThing::getUuid, ExampleThing::getCode, ExampleThing::getName, ExampleThing::isActive);
    }

    public ExampleThingDto get(UUID uuid) {
        return toDto(find(uuid));
    }

    @Transactional
    @Auditable(entity = "example_thing", action = AuditAction.CREATE)
    public ExampleThingDto create(ExampleThingCreateRequest req) {
        ExampleThing e = new ExampleThing();
        e.setCode(req.code());
        e.setName(req.name());
        e.setDescription(req.description());
        return toDto(repository.save(e));
    }

    @Transactional
    @Auditable(entity = "example_thing", action = AuditAction.UPDATE, uuidArgIndex = 0)
    public ExampleThingDto update(UUID uuid, ExampleThingUpdateRequest req) {
        ExampleThing e = find(uuid);
        e.setName(req.name());
        e.setDescription(req.description());
        if (req.active() != null) e.setActive(req.active());
        return toDto(repository.save(e));
    }

    /** Soft-delete únicamente. Nunca `repository.delete()`. */
    @Transactional
    @Auditable(entity = "example_thing", action = AuditAction.DELETE, uuidArgIndex = 0)
    public void delete(UUID uuid) {
        ExampleThing e = find(uuid);
        e.setActive(false);
        repository.save(e);
    }

    private ExampleThing find(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("ExampleThing not found: " + uuid));
    }

    static ExampleThingDto toDto(ExampleThing e) {
        return new ExampleThingDto(e.getUuid(), e.getCode(), e.getName(), e.getDescription(), e.isActive());
    }
}
```

**`@Auditable`** (`core/audit/Auditable.java`) marca un método de servicio para que `DataChangeAuditAspect` lo intercepte. El `entity` **debe existir** como fila en `entity_config` (ver Paso 7) o el aspecto se salta la auditoría silenciosamente (fail-safe, no bloquea la operación de negocio). `uuidArgIndex` indica qué argumento del método es el UUID de la entidad modificada (`-1`, el default, para CREATE — el UUID se resuelve del DTO devuelto).

**Sort por defecto configurable:** `DefaultSortResolver` + `entity_config.default_sort` permiten que un admin configure el orden por defecto de un listado sin deploy (sin esto, el default hardcodeado es `createdAt DESC`). Registrar la entidad en `entity_config` (Paso 7) para habilitarlo — si no se registra, el listado simplemente usa el default hardcodeado, no es obligatorio para que la entidad funcione.

## Paso 6 — Controller

`modules/example/controller/AdminExampleThingController.java` (patrón real: `AdminPaymentCategoryController.java`):

```java
@RestController
@RequestMapping("/v1/admin/example-things")
@RequiredArgsConstructor
public class AdminExampleThingController {

    private static final String VIEW   = "hasAuthority('EXAMPLE_THING_VIEW_ALL')";
    private static final String CREATE = "hasAuthority('EXAMPLE_THING_CREATE')";
    private static final String UPDATE = "hasAuthority('EXAMPLE_THING_UPDATE')";
    private static final String DELETE = "hasAuthority('EXAMPLE_THING_DELETE')";

    private final ExampleThingService service;

    @GetMapping
    @PreAuthorize(VIEW)
    public ResponseEntity<AppliedSortPage<ExampleThingDto>> list(
            @PageableDefault(size = 50) Pageable pageable,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        Page<ExampleThingDto> page = service.list(pageable, filter, q, includeInactive);
        return ResponseEntity.ok(new AppliedSortPage<>(page, service.effectiveSort(pageable)));
    }

    @GetMapping("/options")
    @PreAuthorize(VIEW)
    public ResponseEntity<List<OptionDto>> options(
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "50") int limit,
            @RequestParam(required = false) List<UUID> currentValues) {
        return ResponseEntity.ok(service.listOptions(q, limit, currentValues));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize(VIEW)
    public ResponseEntity<ExampleThingDto> get(@PathVariable UUID uuid) {
        return ResponseEntity.ok(service.get(uuid));
    }

    @PostMapping
    @PreAuthorize(CREATE)
    public ResponseEntity<ExampleThingDto> create(@Valid @RequestBody ExampleThingCreateRequest req) {
        ExampleThingDto created = service.create(req);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{uuid}").buildAndExpand(created.uuid()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{uuid}")
    @PreAuthorize(UPDATE)
    public ResponseEntity<ExampleThingDto> update(@PathVariable UUID uuid, @Valid @RequestBody ExampleThingUpdateRequest req) {
        return ResponseEntity.ok(service.update(uuid, req));
    }

    @DeleteMapping("/{uuid}")
    @PreAuthorize(DELETE)
    public ResponseEntity<Void> delete(@PathVariable UUID uuid) {
        service.delete(uuid);
        return ResponseEntity.noContent().build();
    }
}
```

**Nunca** `@PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")` a nivel de clase — siempre `hasAuthority('PERMISO')` por endpoint. Los roles reales son `SYSTEM`, `ADMINISTRADOR`, `OPERADOR`, `OPERADOR_MEDICO`, `ALIADO`, `AFILIADO`, `PROMOTOR` (seed `V6__seed_roles.sql`) — pero el controller nunca referencia un rol directamente, solo permisos.

## Paso 7 — Permisos y `entity_config`

Patrón real completo: `V119__payment_catalogs_permissions_and_audit.sql`. Cada catálogo/entidad con pantalla admin propia recibe el **set estándar de 6 permisos**: `{ENTIDAD}_VIEW_ALL`, `_CREATE`, `_UPDATE`, `_DELETE`, `_RECORD_AUDIT_VIEW` (historial de cambios), `_REPORT_AUDIT_VIEW` (historial de reportes generados). Agregar `_REPORT_GENERATE` solo si la entidad tiene una feature real de generación de reportes (no por defecto).

```sql
-- V{N}__example_things_permissions.sql
INSERT INTO entity_config (entity_key, display_name, table_name) VALUES
    ('example_thing', 'Example Things', 'example_things')
ON CONFLICT (entity_key) DO NOTHING;

INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('EXAMPLE_THING_VIEW_ALL',          'CATALOGS', 'Ver el catálogo de example things'),
    ('EXAMPLE_THING_CREATE',            'CATALOGS', 'Crear un example thing'),
    ('EXAMPLE_THING_UPDATE',            'CATALOGS', 'Actualizar un example thing'),
    ('EXAMPLE_THING_DELETE',            'CATALOGS', 'Desactivar un example thing'),
    ('EXAMPLE_THING_RECORD_AUDIT_VIEW', 'CATALOGS', 'Ver el historial de cambios de un example thing'),
    ('EXAMPLE_THING_REPORT_AUDIT_VIEW', 'CATALOGS', 'Ver el historial de reportes generados de example things')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code;

-- ADMINISTRADOR recibe los permisos explícitamente. SYSTEM los recibe
-- automáticamente vía el trigger de V30 (trg_permissions_grant_system) — no
-- hace falta incluirlo acá, aunque hacerlo también es inofensivo (ON CONFLICT).
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ADMINISTRADOR'
  AND p.name IN ('EXAMPLE_THING_VIEW_ALL', 'EXAMPLE_THING_CREATE', 'EXAMPLE_THING_UPDATE',
                 'EXAMPLE_THING_DELETE', 'EXAMPLE_THING_RECORD_AUDIT_VIEW', 'EXAMPLE_THING_REPORT_AUDIT_VIEW')
ON CONFLICT (role_id, permission_id) DO NOTHING;
```

Usar un `domain_id`/`domain_code` de `permission_domains` existente si la entidad encaja en uno (ej. `CATALOGS`), o crear un dominio nuevo (`INSERT INTO permission_domains ...`) si es un área nueva — ver el ejemplo real `PAYMENT_CATALOG` en V119.

Ver [`new-role.md`](new-role.md) para el detalle completo de roles/permisos.

## Paso 8 — Tests

`src/test/java/.../modules/example/`:

### Repository test (con `@DataJpaTest`)

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ExampleThingRepositoryTest {
    @Autowired ExampleThingRepository repo;

    @Test
    void findByCode_returnsEntity() {
        ExampleThing e = new ExampleThing();
        e.setCode("FOO");
        e.setName("Foo");
        repo.save(e);
        assertThat(repo.findByCode("FOO")).isPresent();
    }
}
```

### Service test (mock repository)

```java
@ExtendWith(MockitoExtension.class)
class ExampleThingServiceTest {
    @Mock ExampleThingRepository repo;
    @InjectMocks ExampleThingService service;

    @Test
    void delete_softDeletesNotHardDeletes() {
        ExampleThing e = new ExampleThing();
        e.setActive(true);
        when(repo.findByUuid(any())).thenReturn(Optional.of(e));
        service.delete(UUID.randomUUID());
        assertThat(e.isActive()).isFalse();
        verify(repo, never()).delete(any());
    }
}
```

### Controller integration test (con `@SpringBootTest` + Testcontainers)

```java
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AdminExampleThingControllerIT {
    @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:15");

    @Test @WithMockUser(authorities = "EXAMPLE_THING_CREATE")
    void create_returns201() throws Exception {
        mvc.perform(post("/v1/admin/example-things")
                .contentType(APPLICATION_JSON)
                .content("""
                    {"code": "FOO", "name": "Foo", "description": null}
                """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.uuid").exists());
    }
}
```

## Paso 9 — Documentación

- [ ] Actualizar [`../context/current-state.md`](../context/current-state.md).
- [ ] Marcar tarea en [`../checklist.md`](../checklist.md) con fecha.
- [ ] Si introduce nuevo endpoint público, actualizar [hub `06-integration.md`](../../../centro-optico-vicente/.ai/specs/06-integration.md).
- [ ] Si introduce nuevo término del dominio, actualizar [hub `domain-glossary.md`](../../../centro-optico-vicente/.ai/context/domain-glossary.md).

## Checklist final

- [ ] Migración Flyway: PK `BIGINT GENERATED ALWAYS AS IDENTITY` nombrada `{tabla}_id` (plural) + columna `uuid` separada
- [ ] Entidad JPA extiende `BaseEntity` (o `BaseAuditEntity` si no necesita `status`) con `@AttributeOverride` sobre `id`
- [ ] Repository extiende `JpaRepository<E, Long>` + `findByUuid(UUID)`
- [ ] DTOs con `@Display` donde aplique (FK o campo presentacional)
- [ ] Service con `@Transactional` correcto, RSQL filter validation, `/options` support
- [ ] Controller con `@PreAuthorize(hasAuthority(...))` por endpoint — nunca `hasAnyRole` a nivel de clase
- [ ] `entity_config` + 6 permisos estándar (`VIEW_ALL/CREATE/UPDATE/DELETE/RECORD_AUDIT_VIEW/REPORT_AUDIT_VIEW`) registrados
- [ ] `@Auditable` en create/update/delete del service
- [ ] Tests unit + integration
- [ ] Sin `repository.delete()` (siempre soft delete)
- [ ] Naming inglés en código; JSON en camelCase
- [ ] Documentación actualizada
