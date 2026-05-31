# Playbook — Nueva migración Flyway

> Decisión implementada: [ADR 0005 cross-stack](../../../centro-optico-vicente/.ai/decisions/0005-flyway-jpa.md).

## Cuándo crear una migración

- Crear/modificar/borrar **schema** (tablas, columnas, índices, vistas, funciones, triggers, extensiones)
- Insertar/modificar **seeds** (datos iniciales de catálogos)
- Crear nuevos **enums** declarados (ENUM type)

NO crear migración para:
- Datos transaccionales (eso es vía endpoints)
- Cambios "experimentales" en dev (usar branch + DB local fresca)

## Procedimiento

### 1. Decidir el número

Verificar la última migración existente:

```bash
ls -1 src/main/resources/db/migration/ | sort -V | tail
# Ej: V14__allies.sql

# Próximo: V15
```

### 2. Crear el archivo

Naming: `V{N}__{snake_case_descripcion_breve}.sql`

```bash
touch src/main/resources/db/migration/V15__ally_users.sql
```

Reglas:
- Doble underscore entre número y descripción (`V15__`, no `V15_`)
- Descripción breve y clara (no spaces, sólo underscores)
- Una migración = un cambio cohesivo (no mezclar 5 cambios no relacionados)

### 3. Escribir el SQL

#### Template para nueva tabla

```sql
-- ====================================================
-- V15__ally_users.sql
-- Tabla de usuarios operadores de aliados (recepcionistas, etc.)
-- ====================================================

CREATE TABLE ally_users (
    ally_user_id    UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    ally_id         UUID            NOT NULL REFERENCES allies(ally_id),
    user_id         UUID            NOT NULL REFERENCES users(user_id),
    -- specific fields...
    role_within_ally VARCHAR(50)    NOT NULL DEFAULT 'OPERATOR',
    -- audit + status (obligatorios por ADR 0006)
    is_active        BOOLEAN        NOT NULL DEFAULT TRUE,
    status           VARCHAR(50)    NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by       UUID           NULL REFERENCES users(user_id),
    updated_by       UUID           NULL REFERENCES users(user_id),
    -- constraints
    CONSTRAINT uq_ally_users_ally_user UNIQUE (ally_id, user_id),
    CONSTRAINT ck_ally_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

-- índices
CREATE INDEX idx_ally_users_ally ON ally_users(ally_id) WHERE is_active = TRUE;
CREATE INDEX idx_ally_users_user ON ally_users(user_id) WHERE is_active = TRUE;

-- trigger updated_at
CREATE TRIGGER trg_ally_users_before_update_set_updated_at
    BEFORE UPDATE ON ally_users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- comentarios opcionales para documentación interna
COMMENT ON TABLE ally_users IS 'Usuarios operadores que actúan en nombre de un aliado';
COMMENT ON COLUMN ally_users.role_within_ally IS 'Rol del usuario dentro del aliado (no rol del sistema)';
```

#### Template para agregar columna

```sql
-- V20__add_phone_to_members.sql
ALTER TABLE members ADD COLUMN phone_secondary VARCHAR(20) NULL;
CREATE INDEX idx_members_phone_secondary ON members(phone_secondary) WHERE phone_secondary IS NOT NULL;
COMMENT ON COLUMN members.phone_secondary IS 'Teléfono alternativo opcional';
```

#### Template para seeds

```sql
-- V{N}__seed_example.sql  (formato genérico de un seed)
-- Para el seed real de roles ver V6__seed_roles.sql.
INSERT INTO example_things (name, description)
VALUES
    ('FOO', 'Descripción de foo'),
    ('BAR', 'Descripción de bar');
```

#### Template para nuevo índice

```sql
-- V25__add_index_payments_membership_status.sql
CREATE INDEX CONCURRENTLY idx_payments_membership_status
    ON payments(membership_id, status)
    WHERE is_active = TRUE;
```

> ⚠️ `CONCURRENTLY` evita locks pero NO se puede usar dentro de transacción Flyway por defecto. Configurar `spring.flyway.outOfOrder=false` y opcionalmente usar repeatable migrations (`R__`) o ejecutar manualmente fuera de Flyway en mantenimiento.

### 4. Crear/modificar entidad JPA correspondiente

Ver [`new-entity.md`](new-entity.md).

JPA debe coincidir exactamente con el schema (Hibernate validate fallaría al arrancar si no).

### 5. Probar localmente

```bash
# Opción A: dejar que la app aplique al arrancar
./gradlew bootRun --args='--spring.profiles.active=dev'

# Opción B: forzar Flyway sin arrancar app
./gradlew flywayMigrate -i

# Verificar
psql -d optisalud -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"

# Validar JPA mappea bien
./gradlew test --tests "OptiSaludPlusApplicationTests"
```

### 6. Verificar antes de commit

- [ ] La migración corre sin errores en DB limpia
- [ ] La migración corre sin errores en DB existente (con datos previos)
- [ ] Hibernate `validate` no falla al arrancar
- [ ] Si agrega tabla: tiene PK uuid, audit columns, is_active, status, trigger updated_at
- [ ] Si agrega FK: tiene índice asociado
- [ ] Naming sigue convención (`V{N}__snake_case.sql`)
- [ ] Comentarios opcionales en columnas/tablas no triviales

### 7. Reglas críticas

#### Inmutabilidad

Una migración aplicada a producción es **INMUTABLE**. Si necesitás corregir:

❌ NO modificar `V15__ally_users.sql` después de mergeado
✅ Crear `V16__fix_ally_users_X.sql` con la corrección

Razón: Flyway compara checksums. Si modificás un archivo ya aplicado, falla al arranque.

#### Backward compatibility

Si la migración cambia algo en uso por código existente:
- **Aditiva** (add column nullable, add table) → safe
- **Destructiva** (drop column, rename, change type) → 2-fase:
  1. Versión N: agregar nuevo, mantener viejo
  2. Deploy app que escribe en ambos, lee del nuevo
  3. Versión N+1: drop viejo

#### Datos críticos

Si la migración transforma datos:
- **Backup ANTES** (incluso si ya hay backup diario, hacer uno extra antes)
- Probar en DB de staging primero
- Considerar ejecutar en mantenimiento si tabla > 100K filas

## Casos especiales

### Crear ENUM type

```sql
-- V22__create_payment_method_enum.sql
CREATE TYPE payment_method AS ENUM ('ZELLE', 'TRANSFERENCIA', 'EFECTIVO');

ALTER TABLE payments ADD COLUMN method payment_method NOT NULL DEFAULT 'ZELLE';
```

> En JPA: `@Enumerated(EnumType.STRING)` + `@JdbcType(PostgreSQLEnumJdbcType.class)` o usar `VARCHAR + CHECK constraint` (más flexible para agregar valores sin migración).

### Crear extensión

```sql
-- V1__initial_extensions.sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;     -- gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS unaccent;     -- full-text search en español
CREATE EXTENSION IF NOT EXISTS citext;       -- emails case-insensitive
```

### Crear función + trigger genérico

```sql
-- V2__base_audit_function.sql
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
```

Usar en cada tabla nueva:

```sql
CREATE TRIGGER trg_{table}_before_update_set_updated_at
    BEFORE UPDATE ON {table}
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
```

### Crear vista (cuando es estable)

```sql
-- V28__digital_cards_view.sql
CREATE OR REPLACE VIEW digital_cards_v AS
SELECT
    m.member_id,
    m.full_name,
    m.document_number,
    ms.membership_id,
    ms.status AS membership_status,
    p.name AS plan_name,
    ms.next_due_date
FROM members m
JOIN memberships ms ON m.member_id = ms.member_id
JOIN plans p ON ms.plan_id = p.plan_id
WHERE m.is_active = TRUE AND ms.is_active = TRUE;
```

### Migración repeatable (raras)

Para vistas/funciones que se actualizan frecuentemente sin perder tracking:

```
R__digital_cards_view.sql
```

Flyway re-aplica si el checksum cambia. Útil para vistas evolutivas.

## Validación post-aplicación

```bash
# 1. Ver historial Flyway
psql -d optisalud -c "SELECT * FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"

# 2. Verificar schema
psql -d optisalud -c "\d+ {tabla_nueva}"

# 3. Verificar índices
psql -d optisalud -c "\di {tabla_nueva}*"

# 4. Verificar JPA arranca
./gradlew test --tests "OptiSaludPlusApplicationTests"
```

## Cuando algo sale mal

### Migración falla en producción
1. **NO modificar el archivo** ya commiteado.
2. Si es fixable con SQL adicional: crear `V{N+1}__rollback_X.sql`.
3. Si rompe completamente: restore desde backup + análisis post-mortem.

### Hibernate validate falla al arrancar
- Mensaje típico: "missing column X in table Y" o "column type mismatch"
- Significa: schema y entidad no coinciden
- Verificar diferencias y corregir entidad (NO modificar migración)

### Checksum mismatch
- Significa: alguien modificó un archivo ya aplicado
- Recuperar versión original del git o ejecutar `flyway repair`

## Referencias

- [ADR 0005 Flyway + JPA validate](../../../centro-optico-vicente/.ai/decisions/0005-flyway-jpa.md)
- [ADR 0006 Table conventions](../../../centro-optico-vicente/.ai/decisions/0006-table-conventions.md)
- [`../specs/02-database.md`](../specs/02-database.md) — schema completo
- [Flyway docs](https://flywaydb.org/documentation/)
