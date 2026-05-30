# 03 — Convenciones JPA + Flyway

> Implementa [ADR 0005 cross-stack](../../../centro-optico-vicente/.ai/decisions/0005-flyway-jpa.md).

## Configuración base

```properties
# application.properties (base)
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
spring.flyway.baseline-on-migrate=true
spring.flyway.validate-on-migrate=true

spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.properties.hibernate.jdbc.batch_size=20
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true
```

## Workflow del dev

1. Pensar el cambio de schema
2. Crear migration: `V{N}__{descripcion}.sql` (ver [`../playbooks/new-migration.md`](../playbooks/new-migration.md))
3. Crear/modificar entidad JPA correspondiente (ver [`../playbooks/new-entity.md`](../playbooks/new-entity.md))
4. Arrancar app: Flyway aplica → Hibernate valida
5. Si todo ok, commit ambos archivos juntos

## Reglas JPA

### Entidad

```java
@Entity
@Table(name = "members")
@Getter @Setter @NoArgsConstructor
public class Member extends BaseEntity {

    @Id
    @Column(name = "member_id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "document_number", nullable = false, unique = true, length = 20)
    private String documentNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_id", referencedColumnName = "city_id")
    private City city;

    // beneficiarios — unidirectional desde Beneficiary
    // no agregar @OneToMany acá si no es necesario
}
```

### Reglas

- **Toda entidad extiende `BaseEntity`** (audit + is_active + status)
- **PK**: nombre específico (`memberId`, no `id`); UUID; `updatable = false`
- **FetchType.LAZY** por default; cambiar a EAGER sólo con buena justificación
- **Bidireccional** sólo si justifica (default unidireccional)
- **Enums:** `@Enumerated(EnumType.STRING)` siempre (nunca ORDINAL)
- **Decimals:** `BigDecimal` con `@Column(precision = 12, scale = 2)`
- **Fechas:** `Instant` (UTC) o `LocalDate` según corresponda
- **Strings:** preferir `VARCHAR(N)` en la migración (mapea a `String` por default, sin anotaciones extra). **Evitar `CHAR(N)`** salvo razón clara — `String` por default lo valida como `VARCHAR` y rompe el arranque (ver sección "Hibernate validate" abajo).
- **Tipos PostgreSQL no estándar** (`CITEXT`, `INET`, `TEXT`, `CHAR(N)`, `JSONB`, etc.): mapear con `@Column(columnDefinition = "…")` en la entity para que el `validate` matchee lo que reporta el driver.

### Performance: evitar N+1

```java
// MAL:
List<Member> members = repository.findAll();
members.forEach(m -> m.getCity().getName());  // N+1

// BIEN:
@EntityGraph(attributePaths = {"city"})
List<Member> findAllWithCity();

// O JOIN FETCH:
@Query("SELECT m FROM Member m JOIN FETCH m.city WHERE m.isActive = true")
List<Member> findAllActiveWithCity();
```

### Repository

```java
public interface MemberRepository
        extends JpaRepository<Member, UUID>,
                JpaSpecificationExecutor<Member> {  // para RSQL

    Optional<Member> findByDocumentNumberAndIsActiveTrue(String documentNumber);

    @EntityGraph(attributePaths = {"city", "beneficiaries"})
    Optional<Member> findWithDetailsById(UUID id);
}
```

## Reglas Flyway

### Naming

- `V{N}__{snake_case}.sql` (doble underscore)
- N monotónico creciente; saltos permitidos pero no recomendados
- Descripción breve sin spaces

### Inmutabilidad

- Una migración aplicada en prod es INMUTABLE
- Para corregir: crear `V{N+1}__fix.sql`
- Modificar archivo aplicado → checksum mismatch → app no arranca

### Transaccionalidad

- Flyway envuelve cada migración en transacción por default
- Excepciones: `CREATE INDEX CONCURRENTLY` requiere fuera de transacción → usar archivos separados o `R__` repeatable

### Repeatable (raras)

`R__{descripcion}.sql` — re-aplica si checksum cambia. Útil para vistas que evolucionan.

## Hibernate validate

Al arrancar:
- Hibernate compara cada entidad con su tabla
- Si falta una columna, tipo no coincide, etc. → falla al arranque
- Mensajes típicos:
  - `missing column [X] in table [Y]`
  - `wrong column type encountered in column [iso_code] in table [countries]; found [bpchar (Types#CHAR)], but expecting [varchar(2) (Types#VARCHAR)]` ← CHAR vs VARCHAR

Si esto pasa:
1. Confirmar que la migración correspondiente existe y se aplicó
2. Confirmar que el tipo Java mapea al tipo SQL correctamente
3. Posibles mismatches: `String` ↔ `VARCHAR/TEXT/CITEXT/CHAR`, `Instant` ↔ `TIMESTAMP WITH TIME ZONE`, `BigDecimal` ↔ `NUMERIC(p,s)`

### Mapeo de tipos PostgreSQL no-default — `columnDefinition`

`String` mapea por default a `VARCHAR`. Para cualquier otro tipo de texto, **declarar `columnDefinition`** en la entity (es el patrón establecido en este proyecto):

```java
// CITEXT (case-insensitive, ej. users.email)
@Column(unique = true, columnDefinition = "citext")
private String email;

// INET (IPv4/IPv6, ej. user_sessions_log.ip_address)
@Column(name = "ip_address", nullable = false, columnDefinition = "inet")
private String ipAddress;

// TEXT (sin límite de longitud, ej. contact_messages.message)
@Column(nullable = false, columnDefinition = "TEXT")
private String message;

// CHAR(N) — desaconsejado en este proyecto (Postgres lo guarda como bpchar
// con padding de espacios; ver regla "Preferir VARCHAR(N)" arriba). Sólo si
// hay una razón clara (p. ej. interop con un sistema externo que exige CHAR):
@Column(unique = true, nullable = false, columnDefinition = "char(2)")
private String someFixedCode;
```

> **Preferir `VARCHAR(N)` en nuevas migraciones** y omitir `columnDefinition` en la entity. Usar `CHAR(N)` / `CITEXT` / `INET` sólo cuando aporte algo concreto (longitud fija real, case-insensitive nativo, semántica de red), y entonces alinear la entity con `columnDefinition`.

## Tests

### `@DataJpaTest` (rápido, sólo capa de persistencia)

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class MemberRepositoryTest {
    @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:15");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", pg::getJdbcUrl);
        r.add("spring.datasource.username", pg::getUsername);
        r.add("spring.datasource.password", pg::getPassword);
    }

    @Autowired MemberRepository repo;

    @Test
    void findByDocumentNumberAndIsActiveTrue_excludesInactive() {
        // ...
    }
}
```

### `@SpringBootTest` (full app, integración)

```java
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MemberControllerIT { /* ... */ }
```

## Performance

### HikariCP tuning

```properties
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=30000
spring.datasource.hikari.idle-timeout=600000
spring.datasource.hikari.max-lifetime=1800000
```

### Batch inserts

```properties
spring.jpa.properties.hibernate.jdbc.batch_size=20
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true
```

## Referencias

- [ADR 0005 Flyway + JPA validate](../../../centro-optico-vicente/.ai/decisions/0005-flyway-jpa.md)
- [02-database.md](02-database.md)
- [`../playbooks/new-migration.md`](../playbooks/new-migration.md)
- [`../playbooks/new-entity.md`](../playbooks/new-entity.md)
